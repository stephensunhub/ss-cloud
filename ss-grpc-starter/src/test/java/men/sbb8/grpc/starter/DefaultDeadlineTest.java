package men.sbb8.grpc.starter;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.Deadline;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import men.sbb8.grpc.starter.client.DefaultDeadlineClientInterceptor;
import men.sbb8.grpc.starter.test.GreeterGrpc;
import men.sbb8.grpc.starter.test.HelloRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.grpc.test.autoconfigure.AutoConfigureTestGrpcTransport;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.grpc.client.ImportGrpcClients;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "ss.grpc.client.default-deadline=300ms")
@AutoConfigureTestGrpcTransport
@ImportGrpcClients(types = GreeterGrpc.GreeterBlockingStub.class)
class DefaultDeadlineTest {

    @Autowired
    GreeterGrpc.GreeterBlockingStub stub;

    @Test
    void globalInterceptorAppliesDefaultDeadlineToUnaryCalls() {
        StatusRuntimeException e = assertThrows(StatusRuntimeException.class,
                () -> stub.hello(HelloRequest.newBuilder().setName("slow").build()));
        assertEquals(Status.Code.DEADLINE_EXCEEDED, e.getStatus().getCode());
    }

    @Test
    void callerDeadlineWins() {
        assertEquals("slow done", stub.withDeadlineAfter(10, TimeUnit.SECONDS)
                .hello(HelloRequest.newBuilder().setName("slow").build()).getMessage());
    }

    private static CallOptions intercepted(Duration configured, MethodDescriptor.MethodType type, CallOptions options) {
        AtomicReference<CallOptions> seen = new AtomicReference<>();
        Channel next = new Channel() {
            @Override
            public <ReqT, RespT> io.grpc.ClientCall<ReqT, RespT> newCall(MethodDescriptor<ReqT, RespT> method, CallOptions callOptions) {
                seen.set(callOptions);
                return null;
            }

            @Override
            public String authority() {
                return "test";
            }
        };
        MethodDescriptor<?, ?> method = GreeterGrpc.getHelloMethod().toBuilder().setType(type).build();
        new DefaultDeadlineClientInterceptor(configured).interceptCall(method, options, next);
        return seen.get();
    }

    @Test
    void unaryGetsDefaultButStreamingAndExplicitDeadlinesAreLeftAlone() {
        assertNotNull(intercepted(Duration.ofSeconds(5), MethodDescriptor.MethodType.UNARY, CallOptions.DEFAULT).getDeadline());
        assertNull(intercepted(Duration.ofSeconds(5), MethodDescriptor.MethodType.SERVER_STREAMING, CallOptions.DEFAULT).getDeadline());
        assertNull(intercepted(Duration.ofSeconds(5), MethodDescriptor.MethodType.BIDI_STREAMING, CallOptions.DEFAULT).getDeadline());
        assertNull(intercepted(Duration.ZERO, MethodDescriptor.MethodType.UNARY, CallOptions.DEFAULT).getDeadline());

        Deadline explicit = Deadline.after(1, TimeUnit.HOURS);
        CallOptions seen = intercepted(Duration.ofSeconds(5), MethodDescriptor.MethodType.UNARY,
                CallOptions.DEFAULT.withDeadline(explicit));
        assertSame(explicit, seen.getDeadline());
        assertTrue(seen.getDeadline().timeRemaining(TimeUnit.MINUTES) > 50);
    }
}
