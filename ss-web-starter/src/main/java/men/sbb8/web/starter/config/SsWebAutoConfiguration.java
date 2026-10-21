package men.sbb8.web.starter.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import men.sbb8.web.starter.handler.GlobalExceptionHandler;
import men.sbb8.web.starter.handler.ResponseWrapAdvice;
import men.sbb8.web.starter.resolver.UserInfoArgumentResolver;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * web starter 自动配置，所有 Bean 均可被应用同类型 Bean 覆盖
 *
 * @author Stephen
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(SsWebProperties.class)
public class SsWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "ss.web.response-wrap", name = "enabled", matchIfMissing = true)
    public ResponseWrapAdvice responseWrapAdvice(JsonMapper jsonMapper) {
        return new ResponseWrapAdvice(jsonMapper);
    }

    @Bean
    public WebMvcConfigurer ssWebMvcConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addArgumentResolvers(@NonNull List<HandlerMethodArgumentResolver> resolvers) {
                resolvers.add(new UserInfoArgumentResolver());
            }
        };
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(OpenAPI.class)
    static class OpenApiConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public OpenAPI openAPI(SsWebProperties properties,
                               @Value("${spring.application.name:application}") String applicationName) {
            SsWebProperties.OpenApi doc = properties.getOpenApi();
            String title = doc.getTitle() != null ? doc.getTitle() : applicationName;
            String description = doc.getDescription() != null ? doc.getDescription() : title + " 接口文档";
            return new OpenAPI().info(new Info().title(title).description(description).version(doc.getVersion()));
        }
    }
}
