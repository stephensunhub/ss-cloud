package men.sbb8.grpc.client;

import men.sbb8.grpc.core.metrics.PrometheusScrapeServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class ClientOnlyTest {

    @Autowired
    ApplicationContext context;

    @Autowired
    Environment env;

    @Test
    void clientStarterWorksWithoutServerStarter() {
        assertEquals(1, context.getBeansOfType(DefaultDeadlineClientInterceptor.class).size());
        assertThrows(ClassNotFoundException.class, () -> Class.forName("men.sbb8.grpc.server.BusinessExceptionHandler"));
        assertFalse(context.containsBean("ssBusinessExceptionHandler"));
        assertFalse(env.containsProperty("spring.grpc.server.port"), "客户端不应带有服务端端口默认值");
    }

    @Test
    void commonDefaultsFromCoreApply() {
        assertEquals("1.0", env.getProperty("management.tracing.sampling.probability"));
        assertEquals(Duration.ofSeconds(30), context.getBean(men.sbb8.grpc.client.config.SsGrpcClientProperties.class).getDefaultDeadline());
        assertTrue(context.getBean(PrometheusScrapeServer.class).isRunning(), "客户端也应能被 Prometheus 抓取");
    }
}
