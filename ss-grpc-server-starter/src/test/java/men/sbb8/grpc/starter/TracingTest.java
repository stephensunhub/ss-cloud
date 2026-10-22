package men.sbb8.grpc.starter;

import com.sun.net.httpserver.HttpServer;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import men.sbb8.grpc.starter.test.GreeterGrpc;
import men.sbb8.grpc.starter.test.HelloRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.grpc.test.autoconfigure.AutoConfigureTestGrpcTransport;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.grpc.client.ImportGrpcClients;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 调用链经 gRPC metadata 从客户端延续到服务端；配置了 OTLP 地址后 span 会发出
 */
@SpringBootTest(properties = {
        "spring.application.name=grpc-trace-svc",
        "management.opentelemetry.tracing.export.schedule-delay=100ms"
})
@AutoConfigureTestGrpcTransport
@ImportGrpcClients(types = GreeterGrpc.GreeterBlockingStub.class)
class TracingTest {

    private static final List<String> BODIES = new CopyOnWriteArrayList<>();
    private static HttpServer receiver;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws IOException {
        receiver = HttpServer.create(new InetSocketAddress(0), 0);
        receiver.createContext("/", exchange -> {
            BODIES.add(exchange.getRequestURI().getPath() + "|"
                    + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1));
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
    GreeterGrpc.GreeterBlockingStub stub;

    @Autowired
    Tracer tracer;

    @Test
    void traceContinuesFromClientToServer() {
        Span span = tracer.nextSpan().name("client-operation").start();
        String serverTraceId;
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            serverTraceId = stub.hello(HelloRequest.newBuilder().setName("trace").build()).getMessage();
        } finally {
            span.end();
        }
        assertTrue(serverTraceId.matches("[0-9a-f]{32}"), serverTraceId);
        assertEquals(span.context().traceId(), serverTraceId);
    }

    @Test
    void spansAreExportedOverOtlpHttp() throws Exception {
        stub.hello(HelloRequest.newBuilder().setName("tom").build());
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (BODIES.isEmpty() && System.nanoTime() < deadline) {
            Thread.sleep(50);
        }
        assertTrue(!BODIES.isEmpty(), "接收端没有收到 span");
        String received = BODIES.get(0);
        assertTrue(received.startsWith("/v1/traces|"), received.substring(0, 30));
        assertTrue(received.contains("grpc-trace-svc"), "资源里应有服务名");
    }
}
