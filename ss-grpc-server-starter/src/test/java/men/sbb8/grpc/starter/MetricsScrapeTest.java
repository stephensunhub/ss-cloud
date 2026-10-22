package men.sbb8.grpc.starter;

import men.sbb8.grpc.core.metrics.PrometheusScrapeServer;
import men.sbb8.grpc.starter.test.GreeterGrpc;
import men.sbb8.grpc.starter.test.HelloRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.grpc.test.autoconfigure.AutoConfigureTestGrpcTransport;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.grpc.client.ImportGrpcClients;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@AutoConfigureTestGrpcTransport
@ImportGrpcClients(types = GreeterGrpc.GreeterBlockingStub.class)
class MetricsScrapeTest {

    private final HttpClient http = HttpClient.newHttpClient();

    @Autowired
    GreeterGrpc.GreeterBlockingStub stub;

    @Autowired
    PrometheusScrapeServer server;

    private HttpResponse<String> send(String method, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + server.getPort() + path))
                .method(method, HttpRequest.BodyPublishers.noBody()).build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void scrapeExposesJvmAndGrpcMetricsWithHistogramAndApplicationTag() throws Exception {
        stub.hello(HelloRequest.newBuilder().setName("tom").build());

        HttpResponse<String> response = send("GET", "/metrics");
        assertEquals(200, response.statusCode());
        assertEquals("text/plain; version=0.0.4; charset=utf-8", response.headers().firstValue("content-type").orElse(""));
        String body = response.body();
        assertTrue(body.contains("jvm_memory_used_bytes"), "缺少 JVM 指标");
        assertTrue(body.contains("grpc_server_seconds_bucket"), "gRPC 服务端耗时应有直方图桶");
        assertTrue(body.contains("grpc_client_seconds_bucket"), "gRPC 客户端耗时应有直方图桶");
        assertTrue(body.contains("application=\"grpc-test-app\""), "指标应带 application 标签");
    }

    @Test
    void onlyGetOnConfiguredPath() throws Exception {
        assertEquals(404, send("GET", "/").statusCode());
        assertEquals(404, send("GET", "/metrics/extra").statusCode());
        assertEquals(405, send("POST", "/metrics").statusCode());
        assertEquals("GET", send("POST", "/metrics").headers().firstValue("allow").orElse(""));
    }

    @Test
    void runsOnItsOwnPortWithDaemonThreads() {
        assertTrue(server.isRunning());
        assertTrue(server.getPort() > 0);
        assertTrue(Thread.getAllStackTraces().keySet().stream()
                .filter(thread -> thread.getName().startsWith("ss-metrics-"))
                .allMatch(Thread::isDaemon));
    }
}
