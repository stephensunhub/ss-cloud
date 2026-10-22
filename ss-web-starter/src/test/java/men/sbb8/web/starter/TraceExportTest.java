package men.sbb8.web.starter;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * 配置了 OTLP 地址后，span 会通过 OTLP/HTTP 发出，资源里带有 spring.application.name
 */
@SpringBootTest(properties = {
        "spring.application.name=trace-export-svc",
        "management.opentelemetry.tracing.export.schedule-delay=100ms"
})
@AutoConfigureMockMvc
class TraceExportTest {

    private static final List<String> BODIES = new CopyOnWriteArrayList<>();
    private static HttpServer receiver;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws IOException {
        receiver = HttpServer.create(new InetSocketAddress(0), 0);
        receiver.createContext("/", exchange -> {
            BODIES.add(exchange.getRequestURI().getPath() + "|" + exchange.getRequestHeaders().getFirst("content-type")
                    + "|" + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        receiver.start();
        registry.add("management.opentelemetry.tracing.export.otlp.endpoint",
                () -> "http://localhost:" + receiver.getAddress().getPort() + "/v1/traces");
    }

    @AfterAll
    static void stopReceiver() {
        receiver.stop(0);
    }

    @Autowired
    MockMvc mvc;

    @Test
    void spansAreExportedOverOtlpHttp() throws Exception {
        mvc.perform(get("/obj"));
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (BODIES.isEmpty() && System.nanoTime() < deadline) {
            Thread.sleep(50);
        }
        assertTrue(!BODIES.isEmpty(), "接收端没有收到 span");
        String received = BODIES.get(0);
        assertTrue(received.startsWith("/v1/traces|application/x-protobuf|"), received.substring(0, 60));
        assertTrue(received.contains("trace-export-svc"), "资源里应有服务名");
    }
}
