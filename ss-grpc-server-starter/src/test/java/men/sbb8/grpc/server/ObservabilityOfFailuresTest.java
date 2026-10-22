package men.sbb8.grpc.server;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import men.sbb8.grpc.server.test.GreeterGrpc;
import men.sbb8.grpc.server.test.HelloRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.grpc.test.autoconfigure.AutoConfigureTestGrpcTransport;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.grpc.client.ImportGrpcClients;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 失败的调用也要被观测到：指标里是真实的 gRPC 状态，异常处理时打的日志带有追踪号。
 * 前提是异常处理发生在观测拦截器的范围之内（见 SsGrpcServerAutoConfiguration）。
 */
@SpringBootTest
@AutoConfigureTestGrpcTransport
@ImportGrpcClients(types = GreeterGrpc.GreeterBlockingStub.class)
@ExtendWith(OutputCaptureExtension.class)
class ObservabilityOfFailuresTest {

    @Autowired
    GreeterGrpc.GreeterBlockingStub stub;

    @Autowired
    MeterRegistry registry;

    @Autowired
    Tracer tracer;

    private void callExpectingFailure(String name) {
        assertThrows(StatusRuntimeException.class, () -> stub.hello(HelloRequest.newBuilder().setName(name).build()));
    }

    private long serverCalls(String status) {
        return registry.find("grpc.server").tag("grpc.status_code", status).timers().stream().mapToLong(Timer::count).sum();
    }

    @Test
    void serverMetricsRecordTheRealStatusOfFailedCalls() {
        long alreadyExists = serverCalls("ALREADY_EXISTS");
        long internal = serverCalls("INTERNAL");
        callExpectingFailure("business");
        callExpectingFailure("boom");
        assertEquals(alreadyExists + 1, serverCalls("ALREADY_EXISTS"), "业务异常应记为 ALREADY_EXISTS");
        assertEquals(internal + 1, serverCalls("INTERNAL"), "未知异常应记为 INTERNAL");
        assertEquals(0, serverCalls("UNKNOWN"), "不应出现 UNKNOWN");
    }

    @Test
    void exceptionHandlingLogsCarryTheTraceId(CapturedOutput output) {
        Span span = tracer.nextSpan().name("client-operation").start();
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            callExpectingFailure("business");
        } finally {
            span.end();
        }
        String line = output.getAll().lines().filter(l -> l.contains("BusinessExceptionHandler")).reduce((a, b) -> b).orElse("");
        assertTrue(line.contains(span.context().traceId()), "日志应带追踪号，实际: " + line);
    }

    @Test
    void applicationGrpcAdviceTakesPrecedenceOverStarterHandlers() {
        StatusRuntimeException e = assertThrows(StatusRuntimeException.class,
                () -> stub.hello(HelloRequest.newBuilder().setName("advice").build()));
        assertEquals(Status.Code.FAILED_PRECONDITION, e.getStatus().getCode());
        assertEquals("advice: not supported here", e.getStatus().getDescription());
    }
}
