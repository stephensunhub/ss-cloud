package men.sbb8.web.starter.config;

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
 * 以最低优先级加载 starter 的默认配置（META-INF/ss-web-defaults.yaml），
 * 应用自己的 application.yaml、环境变量、命令行都可以覆盖。
 * 激活 prod / production 环境时，另外默认：关闭 springdoc 接口文档和 Swagger UI，
 * actuator 监控端点使用独立端口 8081（不与业务接口同端口暴露），控制台日志使用 ECS 结构化 JSON
 *
 * @author Stephen
 */
public class SsWebEnvironmentPostProcessor implements EnvironmentPostProcessor {

    static final String DEFAULTS = "META-INF/ss-web-defaults.yaml";

    @Override
    public void postProcessEnvironment(@NonNull ConfigurableEnvironment environment,
                                       @NonNull SpringApplication application) {
        Resource resource = new ClassPathResource(DEFAULTS);
        try {
            for (PropertySource<?> source : new YamlPropertySourceLoader().load("ss-web-defaults", resource)) {
                environment.getPropertySources().addLast(source);
            }
        } catch (IOException e) {
            throw new IllegalStateException("无法加载 " + DEFAULTS, e);
        }
        if (environment.acceptsProfiles(Profiles.of("prod", "production"))) {
            // 生产环境默认：不暴露接口文档、监控端点独立端口、日志输出结构化 JSON。
            // 需要时在应用配置里覆盖对应属性
            environment.getPropertySources().addLast(new MapPropertySource("ss-web-prod-defaults", Map.of(
                    "springdoc.api-docs.enabled", false,
                    "springdoc.swagger-ui.enabled", false,
                    "management.server.port", 8081,
                    "logging.structured.format.console", "ecs")));
        }
    }
}
