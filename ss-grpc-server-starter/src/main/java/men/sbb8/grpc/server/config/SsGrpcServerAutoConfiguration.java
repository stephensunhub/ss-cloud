package men.sbb8.grpc.server.config;

import men.sbb8.grpc.server.BusinessExceptionHandler;
import io.grpc.protobuf.services.HealthStatusManager;
import men.sbb8.grpc.server.FallbackExceptionHandler;
import men.sbb8.grpc.server.HealthUpdateListener;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.grpc.server.autoconfigure.GrpcServerAutoConfiguration;
import org.springframework.boot.grpc.server.health.GrpcServerHealth;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.grpc.server.exception.GrpcExceptionHandler;

/**
 * gRPC 服务端自动配置，所有 Bean 均可被应用同类型 Bean 覆盖。
 * 异常处理器的顺序：应用自己的 {@code @GrpcAdvice} / 处理器 → 业务异常处理 → 兜底。
 *
 * @author Stephen
 */
// 排在可用性自动配置之后：HealthUpdateListener 要在 ApplicationAvailabilityBean 更新完状态之后再同步，
// 同顺序的监听器按 Bean 注册先后执行
@AutoConfiguration(after = GrpcServerAutoConfiguration.class,
        afterName = "org.springframework.boot.autoconfigure.availability.ApplicationAvailabilityAutoConfiguration")
@ConditionalOnClass(GrpcExceptionHandler.class)
public class SsGrpcServerAutoConfiguration {

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

    /**
     * 存活 / 就绪状态变化时立即同步 gRPC 健康状态，没有健康服务时什么也不做
     */
    @Bean
    @ConditionalOnMissingBean
    HealthUpdateListener ssHealthUpdateListener(ObjectProvider<GrpcServerHealth> health,
                                                ObjectProvider<HealthStatusManager> manager) {
        return new HealthUpdateListener(health, manager);
    }
}
