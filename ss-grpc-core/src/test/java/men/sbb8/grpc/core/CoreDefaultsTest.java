package men.sbb8.grpc.core;

import men.sbb8.grpc.core.config.SsGrpcEnvironmentPostProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CoreDefaultsTest {

    private final SsGrpcEnvironmentPostProcessor processor = new SsGrpcEnvironmentPostProcessor();

    @Test
    void defaultsAreLowestPriority() {
        MockEnvironment env = new MockEnvironment().withProperty("management.tracing.sampling.probability", "0.5");
        processor.postProcessEnvironment(env, new SpringApplication());

        assertEquals("0.5", env.getProperty("management.tracing.sampling.probability"));
        assertEquals("30s", env.getProperty("spring.lifecycle.timeout-per-shutdown-phase"));
    }

    @Test
    void prodSamplesLessThanOtherEnvironments() {
        MockEnvironment dev = new MockEnvironment();
        processor.postProcessEnvironment(dev, new SpringApplication());
        assertEquals("1.0", dev.getProperty("management.tracing.sampling.probability"));

        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");
        processor.postProcessEnvironment(prod, new SpringApplication());
        assertEquals("0.1", prod.getProperty("management.tracing.sampling.probability"));
        assertEquals("ecs", prod.getProperty("logging.structured.format.console"));
    }
}
