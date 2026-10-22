package men.sbb8.grpc.server.config;

import org.jspecify.annotations.NonNull;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.Profiles;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.Map;

/**
 * 以最低优先级加载服务端默认配置（META-INF/ss-grpc-server-defaults.yaml），应用配置、环境变量、命令行都可以覆盖。
 * 激活 prod / production 环境时，另外默认关闭 gRPC 反射服务（反射会暴露所有接口定义）。
 * （不用 application.yml：类路径上同名资源只会取到第一个，会被应用自己的文件遮蔽）
 *
 * @author Stephen
 */
public class SsGrpcServerEnvironmentPostProcessor implements EnvironmentPostProcessor {

    static final String DEFAULTS = "META-INF/ss-grpc-server-defaults.yaml";

    @Override
    public void postProcessEnvironment(@NonNull ConfigurableEnvironment environment,
                                       @NonNull SpringApplication application) {
        // 先加入 prod 默认值：它与下面的通用默认值同为最低优先级，但排在前面，同名属性以 prod 为准
        if (environment.acceptsProfiles(Profiles.of("prod", "production"))) {
            environment.getPropertySources().addLast(new MapPropertySource("ss-grpc-server-prod-defaults", Map.of(
                    "spring.grpc.server.reflection.enabled", false)));
        }

        Resource resource = new ClassPathResource(DEFAULTS);
        try {
            for (PropertySource<?> source : new YamlPropertySourceLoader().load("ss-grpc-server-defaults", resource)) {
                environment.getPropertySources().addLast(source);
            }
        } catch (IOException e) {
            throw new IllegalStateException("无法加载 " + DEFAULTS, e);
        }
    }
}
