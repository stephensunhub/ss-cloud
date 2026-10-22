package men.sbb8.web.starter;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * prod 默认值里的路径配置（base-path=/、prometheus 映射为 metrics）确实能让指标出现在 /metrics，
 * 与 gRPC 服务的抓取路径一致。这里把它们应用到同一个端口上验证。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "management.endpoints.web.base-path=/",
        "management.endpoints.web.path-mapping.prometheus=metrics"
})
class MetricsPathTest {

    @LocalServerPort
    int port;

    @Test
    void prometheusIsServedAtMetrics() throws Exception {
        HttpClient http = HttpClient.newHttpClient();
        HttpResponse<String> metrics = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/metrics")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, metrics.statusCode());
        assertTrue(metrics.body().contains("jvm_memory_used_bytes"));

        HttpResponse<String> health = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/health/liveness")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, health.statusCode());
    }
}
