package men.sbb8.web.starter;

import men.sbb8.web.starter.dto.resp.Resp;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 按服务名调用用 Spring Cloud 官方的写法：{@code base-url: lb://wallet}。
 * 这里用<b>真实的</b> Spring Cloud LoadBalancer，实例来自静态实例列表（wallet 指向本机测试服务，ghost 没有任何实例），
 * 验证三件事：服务名被正确解析；没有可用实例时是 502 而不是 500（这要求本 starter 的拦截器排在负载均衡拦截器之外）；
 * 普通地址的分组不受负载均衡影响。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
class LoadBalancedGroupTest {

    private static final int PORT = freePort();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("server.port", () -> PORT);
        registry.add("management.server.port", () -> PORT);
        registry.add("spring.http.serviceclient.demo.base-url", () -> "http://localhost:" + PORT);
        registry.add("spring.cloud.discovery.client.simple.instances.wallet[0].uri", () -> "http://localhost:" + PORT);
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 不对错误状态抛异常，便于断言 502 的响应体
     */
    private static RestClient client() {
        return RestClient.builder().baseUrl("http://localhost:" + PORT)
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> { }).build();
    }

    @Test
    void serviceNameInLbSchemeIsResolvedByTheRealLoadBalancer() {
        Resp<Map<String, Object>> resp = RestClient.create("http://localhost:" + PORT).get().uri("/relay-lb")
                .retrieve().body(new ParameterizedTypeReference<Resp<Map<String, Object>>>() {});
        assertEquals("SUCCESS", resp.getCode());
        assertEquals(1, resp.getData().get("n"));
    }

    @Test
    void noAvailableInstanceIs502NotInternalError() {
        var response = client().get().uri("/relay-ghost").retrieve().toEntity(String.class);
        assertEquals(502, response.getStatusCode().value());
        assertTrue(response.getBody().contains("\"code\":\"BAD_GATEWAY\""), response.getBody());
        assertTrue(response.getBody().contains("下游服务异常"), response.getBody());
    }

    @Test
    void groupsWithPlainAddressesAreLeftAlone() {
        // demo 分组的 base-url 是普通地址（http://localhost:端口），负载均衡存在时也不应拿 localhost 当服务名去找实例
        var response = client().get().uri("/relay").retrieve().toEntity(String.class);
        assertEquals(200, response.getStatusCode().value(), response.getBody());
        assertTrue(response.getBody().contains("\"code\":\"SUCCESS\""), response.getBody());
    }

    @Test
    void unreachablePlainAddressIsStill502() {
        var response = client().get().uri("/relay-down").retrieve().toEntity(String.class);
        assertEquals(502, response.getStatusCode().value());
    }
}
