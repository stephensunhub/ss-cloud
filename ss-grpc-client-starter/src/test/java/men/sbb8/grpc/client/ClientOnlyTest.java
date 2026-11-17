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
        // 服务端的默认值和客户端共用同一个 application.yaml（同名文件类路径上只会读到第一个），所以纯客户端应用也带着它们，
        // 但没有服务端 starter 就不会启动服务端，也不会有服务端的 Bean
        assertTrue(env.containsProperty("spring.grpc.server.port"));
        assertFalse(context.containsBean("ssBusinessExceptionHandler"));
    }

    @Test
    void commonDefaultsFromCoreApply() {
        assertEquals("1.0", env.getProperty("management.tracing.sampling.probability"));
        assertEquals(Duration.ofSeconds(30), context.getBean(men.sbb8.grpc.client.config.SsGrpcClientProperties.class).getDefaultDeadline());
        assertTrue(context.getBean(PrometheusScrapeServer.class).isRunning(), "客户端也应能被 Prometheus 抓取");
    }
}
