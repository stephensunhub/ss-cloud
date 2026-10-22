package men.sbb8.web.starter;

import men.sbb8.web.starter.client.NoInstanceAsIoErrorInterceptor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class NoInstanceAsIoErrorInterceptorTest {

    @Test
    void noInstancesBecomesAnIoErrorSoItIsTreatedAsUnreachable() {
        var interceptor = new NoInstanceAsIoErrorInterceptor((request, body, execution) -> {
            throw new IllegalStateException("No instances available for wallet");
        });
        IOException e = assertThrows(IOException.class, () -> interceptor.intercept(null, new byte[0], null));
        assertEquals("No instances available for wallet", e.getMessage());
        assertInstanceOf(IllegalStateException.class, e.getCause());
    }

    @Test
    void otherIllegalStateExceptionsAreNotSwallowed() {
        IllegalStateException original = new IllegalStateException("something else");
        var interceptor = new NoInstanceAsIoErrorInterceptor((request, body, execution) -> {
            throw original;
        });
        assertSame(original, assertThrows(IllegalStateException.class, () -> interceptor.intercept(null, new byte[0], null)));
    }

    @Test
    void ioErrorsFromTheDelegateAreUntouched() {
        IOException original = new IOException("boom");
        var interceptor = new NoInstanceAsIoErrorInterceptor((request, body, execution) -> {
            throw original;
        });
        assertSame(original, assertThrows(IOException.class, () -> interceptor.intercept(null, new byte[0], null)));
    }
}
