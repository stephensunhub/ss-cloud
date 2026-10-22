package men.sbb8.grpc.client.config;

import men.sbb8.grpc.client.DefaultDeadlineClientInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.grpc.client.GlobalClientInterceptor;

/**
 * gRPC 客户端自动配置：一元调用没有显式 deadline 时补 ss.grpc.client.default-deadline。
 * 所有 Bean 均可被应用同类型 Bean 覆盖。
 *
 * @author Stephen
 */
@AutoConfiguration
@ConditionalOnClass(GlobalClientInterceptor.class)
@EnableConfigurationProperties(SsGrpcClientProperties.class)
public class SsGrpcClientAutoConfiguration {

    @Bean
    @GlobalClientInterceptor
    @ConditionalOnMissingBean
    DefaultDeadlineClientInterceptor ssDefaultDeadlineClientInterceptor(SsGrpcClientProperties properties) {
        return new DefaultDeadlineClientInterceptor(properties.getDefaultDeadline());
    }
}
