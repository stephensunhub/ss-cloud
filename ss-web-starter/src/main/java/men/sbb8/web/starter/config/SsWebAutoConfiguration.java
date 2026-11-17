package men.sbb8.web.starter.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import men.sbb8.web.starter.dto.UserInfo;
import men.sbb8.web.starter.filter.RequestTimeFilter;
import men.sbb8.web.starter.handler.GlobalExceptionHandler;
import men.sbb8.web.starter.handler.ResponseWrapAdvice;
import men.sbb8.web.starter.resolver.UserInfoArgumentResolver;
import org.jspecify.annotations.NonNull;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

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
    public GlobalExceptionHandler globalExceptionHandler(SsWebProperties properties) {
        return new GlobalExceptionHandler(properties.getErrorFormat());
    }

    /**
     * 成功响应自动包装；包名运行期读取，没配置 ss.web.wrap-response.base-packages 时什么都不包装
     */
    @Bean
    @ConditionalOnMissingBean
    public ResponseWrapAdvice ssResponseWrapAdvice(SsWebProperties properties) {
        return new ResponseWrapAdvice(properties.getWrapResponse().getBasePackages());
    }

    /**
     * 记下请求到达时间，供信封 Resp 的 requestTime 用；排在最前面，时间才是「到达」的时间
     */
    @Bean
    public FilterRegistrationBean<RequestTimeFilter> ssRequestTimeFilter() {
        FilterRegistrationBean<RequestTimeFilter> registration = new FilterRegistrationBean<>(new RequestTimeFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean
    public WebMvcConfigurer ssWebMvcConfigurer(SsWebProperties properties) {
        return new WebMvcConfigurer() {
            @Override
            public void addArgumentResolvers(@NonNull List<HandlerMethodArgumentResolver> resolvers) {
                resolvers.add(new UserInfoArgumentResolver(properties.getUserInfo().isRequired()));
            }
        };
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(OpenAPI.class)
    static class OpenApiConfiguration {

        static {
            // UserInfo 来自网关请求头，不是接口的请求参数，不出现在文档里
            SpringDocUtils.getConfig().addRequestWrapperToIgnore(UserInfo.class);
        }

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
