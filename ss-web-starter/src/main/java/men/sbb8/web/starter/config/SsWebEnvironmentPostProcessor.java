package men.sbb8.web.starter.config;

import org.jspecify.annotations.NonNull;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.IOException;

/**
 * 以最低优先级加载 starter 的默认配置（META-INF/ss-web-defaults.yaml），
 * 应用自己的 application.yaml、环境变量、命令行都可以覆盖
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
    }
}
