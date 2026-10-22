package men.sbb8.grpc.starter;

import men.sbb8.grpc.starter.metrics.PrometheusScrapeServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.grpc.test.autoconfigure.AutoConfigureTestGrpcTransport;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "ss.grpc.metrics.prometheus.enabled=false")
@AutoConfigureTestGrpcTransport
class MetricsDisabledTest {

    @Autowired
    ApplicationContext context;

    @Test
    void switchedOffMeansNoServerBeanAndNoPort() {
        assertTrue(context.getBeansOfType(PrometheusScrapeServer.class).isEmpty());
    }
}
