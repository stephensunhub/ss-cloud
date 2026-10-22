package men.sbb8.grpc.starter.config;

import men.sbb8.grpc.starter.server.BusinessExceptionHandler;
import men.sbb8.grpc.starter.server.FallbackExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.grpc.server.autoconfigure.GrpcServerAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.grpc.server.exception.GrpcExceptionHandler;

/**
 * gRPC starter 自动配置，所有 Bean 均可被应用同类型 Bean 覆盖。
 * 异常处理器的顺序：应用自己的 {@code @GrpcAdvice} / 处理器 → 业务异常处理 → 兜底。
 *
 * @author Stephen
 */
@AutoConfiguration(after = GrpcServerAutoConfiguration.class)
@ConditionalOnClass(GrpcExceptionHandler.class)
public class SsGrpcAutoConfiguration {

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE - 100)
    @ConditionalOnMissingBean
    public BusinessExceptionHandler ssBusinessExceptionHandler() {
        return new BusinessExceptionHandler();
    }

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    @ConditionalOnMissingBean
    public FallbackExceptionHandler ssFallbackExceptionHandler() {
        return new FallbackExceptionHandler();
    }
}
