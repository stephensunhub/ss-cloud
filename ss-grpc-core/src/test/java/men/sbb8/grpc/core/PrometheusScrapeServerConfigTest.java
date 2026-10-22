package men.sbb8.grpc.core;

import men.sbb8.grpc.core.metrics.PrometheusScrapeServer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrometheusScrapeServerConfigTest {

    @Test
    void invalidConfigurationFailsFastWithAClearMessage() {
        var noSlash = assertThrows(IllegalArgumentException.class, () -> new PrometheusScrapeServer(() -> null, 9091, "metrics"));
        assertTrue(noSlash.getMessage().contains("ss.grpc.metrics.prometheus.path"), noSlash.getMessage());
        assertThrows(IllegalArgumentException.class, () -> new PrometheusScrapeServer(() -> null, 9091, null));
        var badPort = assertThrows(IllegalArgumentException.class, () -> new PrometheusScrapeServer(() -> null, 70000, "/metrics"));
        assertTrue(badPort.getMessage().contains("ss.grpc.metrics.prometheus.port"), badPort.getMessage());
        assertThrows(IllegalArgumentException.class, () -> new PrometheusScrapeServer(() -> null, -1, "/metrics"));
    }

    @Test
    void validConfigurationIsAccepted() {
        assertDoesNotThrow(() -> new PrometheusScrapeServer(() -> null, 0, "/metrics"));
        assertDoesNotThrow(() -> new PrometheusScrapeServer(() -> null, 65535, "/a/b"));
    }
}
