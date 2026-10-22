package men.sbb8.web.starter;

import men.sbb8.web.starter.client.NoInstanceAsIoErrorInterceptor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NoInstanceAsIoErrorInterceptorTest {

    private final NoInstanceAsIoErrorInterceptor interceptor = new NoInstanceAsIoErrorInterceptor();

    @Test
    void noInstancesBecomesAnIoErrorSoItIsTreatedAsUnreachable() {
        IOException e = assertThrows(IOException.class, () -> interceptor.intercept(null, new byte[0],
                (request, body) -> {
                    throw new IllegalStateException("No instances available for wallet");
                }));
        assertEquals("No instances available for wallet", e.getMessage());
        assertInstanceOf(IllegalStateException.class, e.getCause());
    }

    @Test
    void otherIllegalStateExceptionsAreNotSwallowed() {
        IllegalStateException original = new IllegalStateException("something else");
        assertSame(original, assertThrows(IllegalStateException.class, () -> interceptor.intercept(null, new byte[0],
                (request, body) -> {
                    throw original;
                })));
    }

    @Test
    void ioErrorsFromDownstreamAreUntouched() {
        IOException original = new IOException("boom");
        assertSame(original, assertThrows(IOException.class, () -> interceptor.intercept(null, new byte[0],
                (request, body) -> {
                    throw original;
                })));
    }
}
