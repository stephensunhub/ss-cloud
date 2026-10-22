package men.sbb8.grpc.client;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.Deadline;
import io.grpc.MethodDescriptor;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultDeadlineClientInterceptorTest {

    private static final MethodDescriptor.Marshaller<String> TEXT = new MethodDescriptor.Marshaller<>() {
        @Override
        public InputStream stream(String value) {
            return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public String parse(InputStream stream) {
            return "";
        }
    };

    private static CallOptions intercepted(Duration configured, MethodDescriptor.MethodType type, CallOptions options) {
        AtomicReference<CallOptions> seen = new AtomicReference<>();
        Channel next = new Channel() {
            @Override
            public <ReqT, RespT> ClientCall<ReqT, RespT> newCall(MethodDescriptor<ReqT, RespT> method, CallOptions callOptions) {
                seen.set(callOptions);
                return null;
            }

            @Override
            public String authority() {
                return "test";
            }
        };
        MethodDescriptor<String, String> method = MethodDescriptor.<String, String>newBuilder()
                .setType(type).setFullMethodName("svc/method")
                .setRequestMarshaller(TEXT).setResponseMarshaller(TEXT).build();
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
