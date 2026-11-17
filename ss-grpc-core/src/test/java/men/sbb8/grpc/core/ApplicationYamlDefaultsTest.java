package men.sbb8.grpc.core;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 默认值放在 ss-grpc-core 的 application.yaml 里：没人配置时生效，应用（application.yml、命令行等）写了同名属性则以应用为准；
 * 服务端的默认值也在这个文件里（不是 ss-grpc-server-starter 里另放一个同名文件：类路径上同名文件只会读到第一个）
 */
class ApplicationYamlDefaultsTest {

    @Configuration
    static class Empty {
    }

    private ConfigurableApplicationContext start(String... args) {
        return new SpringApplicationBuilder(Empty.class).web(WebApplicationType.NONE).run(args);
    }

    @Test
    void defaultsApplyWhenNothingElseIsConfigured() {
        try (ConfigurableApplicationContext ctx = start()) {
            var env = ctx.getEnvironment();
            assertEquals("30s", env.getProperty("spring.lifecycle.timeout-per-shutdown-phase"));
            assertEquals("1.0", env.getProperty("management.tracing.sampling.probability"));
            // 服务端的默认值也在
            assertEquals("10m", env.getProperty("spring.grpc.server.keepalive.connection.max-age"));
            assertEquals("9001", env.getProperty("spring.grpc.server.port"));
        }
    }

    @Test
    void applicationConfigurationOverridesTheDefaults() {
        try (ConfigurableApplicationContext ctx = start("--management.tracing.sampling.probability=0.5",
                "--spring.grpc.server.keepalive.connection.max-age=1h")) {
            var env = ctx.getEnvironment();
            assertEquals("0.5", env.getProperty("management.tracing.sampling.probability"));
            assertEquals("1h", env.getProperty("spring.grpc.server.keepalive.connection.max-age"));
            // 没覆盖的仍是默认值
            assertEquals("30s", env.getProperty("spring.lifecycle.timeout-per-shutdown-phase"));
        }
    }
}
