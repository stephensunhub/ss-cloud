package men.sbb8.grpc.starter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.grpc.test.autoconfigure.AutoConfigureTestGrpcTransport;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@AutoConfigureTestGrpcTransport
@ActiveProfiles("prod")
class ProdDefaultsTest {

    @Autowired
    Environment env;

    @Test
    void reflectionOffAndStructuredLoggingInProd() {
        assertEquals("false", env.getProperty("spring.grpc.server.reflection.enabled"));
        assertEquals("ecs", env.getProperty("logging.structured.format.console"));
    }
}
