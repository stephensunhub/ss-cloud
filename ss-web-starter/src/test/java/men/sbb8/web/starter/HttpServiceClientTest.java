package men.sbb8.web.starter;

import men.sbb8.web.starter.dto.UserInfo;
import men.sbb8.web.starter.dto.resp.Resp;
import men.sbb8.web.starter.error.BusinessException;
import men.sbb8.web.starter.error.RemoteCallException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = {
        "ss.web.client.propagate-user-headers=demo"
})
class HttpServiceClientTest {

    /** 客户端 base-url 在容器启动时就要解析，所以提前取一个空闲端口，而不是用 RANDOM_PORT */
    private static final int PORT = freePort();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("server.port", () -> PORT);
        registry.add("management.server.port", () -> PORT);
        registry.add("spring.http.serviceclient.demo.base-url", () -> "http://localhost:" + PORT);
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Autowired
    DemoClient client;

    int port = PORT;

    @Test
    void deserializesRespWithGenerics() {
        var resp = client.obj();
        assertTrue(resp.isSuccess());
        assertEquals(1, resp.unwrap().get("n"));
        assertEquals("2026-01-02T03:04:05", resp.unwrap().get("time"));
    }

    @Test
    void businessErrorOnHttp200IsReturnedAsFailResp() {
        var resp = client.biz();
        assertTrue(resp.isFail());
        assertEquals("PARAMS_ERROR", resp.getCode());
        BusinessException e = assertThrows(BusinessException.class, resp::unwrap);
        assertEquals("PARAMS_ERROR", e.getCode());
    }

    @Test
    void http4xxWithRespBecomesRemoteCallException() {
        RemoteCallException e = assertThrows(RemoteCallException.class, () -> client.valid(Map.of("name", "")));
        assertEquals(400, e.getStatus().value());
        assertEquals("PARAMS_ERROR", e.getCode());
        assertEquals("name 不能为空", e.getRemoteMsg());
    }

    @Test
    void http5xxBecomesRemoteCallException() {
        RemoteCallException e = assertThrows(RemoteCallException.class, () -> client.boom());
        assertEquals(500, e.getStatus().value());
        assertEquals("SYSTEM_ERROR", e.getCode());
    }

    @Test
    void downstream5xxIsExposedAs502WithoutDetails() {
        RestClient plain = RestClient.builder().baseUrl("http://localhost:" + port)
                .defaultStatusHandler(HttpStatusCode::isError, (req, res) -> { }).build();
        var response = plain.get().uri("/relay-boom").retrieve().toEntity(new ParameterizedTypeReference<Resp<Void>>() {});
        assertEquals(502, response.getStatusCode().value());
        assertEquals("BAD_GATEWAY", response.getBody().getCode());
        assertEquals("下游服务异常", response.getBody().getMsg());
    }

    @Test
    void downstreamFrameworkErrorsAreNotPassedThrough() {
        RemoteCallException e = assertThrows(RemoteCallException.class, () -> client.missing());
        assertEquals(404, e.getStatus().value());
        assertEquals("NOT_FOUND", e.getCode());

        RestClient plain = RestClient.builder().baseUrl("http://localhost:" + port)
                .defaultStatusHandler(HttpStatusCode::isError, (req, res) -> { }).build();
        var response = plain.get().uri("/relay-missing").retrieve().toEntity(new ParameterizedTypeReference<Resp<Void>>() {});
        assertEquals(502, response.getStatusCode().value());
        assertEquals("BAD_GATEWAY", response.getBody().getCode());
    }

    @Autowired
    RestClient.Builder restClientBuilder;

    @Test
    void applicationOwnRestClientKeepsSpringErrorBehaviour() {
        RestClient own = restClientBuilder.baseUrl("http://localhost:" + port).build();
        assertThrows(org.springframework.web.client.HttpServerErrorException.class,
                () -> own.get().uri("/boom").retrieve().body(String.class));
    }

    @Test
    void traceContinuesAcrossHttpServiceClientCalls() {
        Map<String, String> ids = RestClient.create("http://localhost:" + port).get().uri("/relay-trace")
                .retrieve().body(new ParameterizedTypeReference<Resp<Map<String, String>>>() {}).unwrap();
        assertTrue(ids.get("caller").matches("[0-9a-f]{32}"), ids.toString());
        assertEquals(ids.get("caller"), ids.get("callee"));
    }

    @Test
    void metricsHaveApplicationTagAndHistograms() throws Exception {
        client.obj();
        String body = java.net.http.HttpClient.newHttpClient().send(
                java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:" + port + "/actuator/prometheus")).build(),
                java.net.http.HttpResponse.BodyHandlers.ofString()).body();
        assertTrue(body.contains("application=\"application\""), "指标应带 application 标签");
        assertTrue(body.contains("http_server_requests_seconds_bucket"), "HTTP 服务端耗时应有直方图桶");
        assertTrue(body.contains("http_client_requests_seconds_bucket"), "HTTP 客户端耗时应有直方图桶");
    }

    @Test
    void userHeadersArePropagatedOnlyInsideARequest() {
        UserInfo outside = client.me().unwrap();
        assertNull(outside.userId());

        UserInfo relayed = RestClient.create("http://localhost:" + port).get().uri("/relay")
                .header("X-User-Id", "42").header("X-User-Name", "bob")
                .retrieve().body(new ParameterizedTypeReference<Resp<UserInfo>>() {}).unwrap();
        assertEquals("42", relayed.userId());
        assertEquals("bob", relayed.username());
    }
}
