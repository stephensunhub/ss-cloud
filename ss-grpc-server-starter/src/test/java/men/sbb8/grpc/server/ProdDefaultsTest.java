package men.sbb8.grpc.server;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.grpc.test.autoconfigure.AutoConfigureTestGrpcTransport;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@AutoConfigureTestGrpcTransport
// 激活应用自己的生产 profile（测试资源里的 application-online.yml），它覆盖 starter 的 application.yaml
@org.springframework.test.context.ActiveProfiles("online")
class ProdDefaultsTest {

    @Autowired
    Environment env;

    @Test
    void reflectionOffAndStructuredLoggingInProd() {
        assertEquals("false", env.getProperty("spring.grpc.server.reflection.enabled"));
        assertEquals("ecs", env.getProperty("logging.structured.format.console"));
        assertEquals("0.1", env.getProperty("management.tracing.sampling.probability"));
    }
}
