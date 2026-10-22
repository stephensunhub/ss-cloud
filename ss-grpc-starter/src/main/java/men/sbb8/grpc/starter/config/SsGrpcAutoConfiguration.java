package men.sbb8.grpc.starter.config;

import men.sbb8.grpc.starter.client.DefaultDeadlineClientInterceptor;
import men.sbb8.grpc.starter.server.BusinessExceptionHandler;
import men.sbb8.grpc.starter.server.FallbackExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.grpc.server.autoconfigure.GrpcServerAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.grpc.client.GlobalClientInterceptor;
import org.springframework.grpc.server.exception.GrpcExceptionHandler;

/**
 * gRPC starter 自动配置，所有 Bean 均可被应用同类型 Bean 覆盖。
 * 异常处理器的顺序：应用自己的 {@code @GrpcAdvice} / 处理器 → 业务异常处理 → 兜底。
 *
 * @author Stephen
 */
@AutoConfiguration(after = GrpcServerAutoConfiguration.class)
@EnableConfigurationProperties(SsGrpcProperties.class)
public class SsGrpcAutoConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(GrpcExceptionHandler.class)
    static class ServerConfiguration {

        @Bean
        @Order(Ordered.LOWEST_PRECEDENCE - 100)
        @ConditionalOnMissingBean
        BusinessExceptionHandler ssBusinessExceptionHandler() {
            return new BusinessExceptionHandler();
        }

        @Bean
        @Order(Ordered.LOWEST_PRECEDENCE)
        @ConditionalOnMissingBean
        FallbackExceptionHandler ssFallbackExceptionHandler() {
            return new FallbackExceptionHandler();
        }
    }

    /**
     * 客户端默认超时：一元调用没有显式 deadline 时补 ss.grpc.client.default-deadline
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(GlobalClientInterceptor.class)
    static class ClientConfiguration {

        @Bean
        @GlobalClientInterceptor
        @ConditionalOnMissingBean
        DefaultDeadlineClientInterceptor ssDefaultDeadlineClientInterceptor(SsGrpcProperties properties) {
            return new DefaultDeadlineClientInterceptor(properties.getClient().getDefaultDeadline());
        }
    }
}
