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
import org.springframework.grpc.server.GlobalServerInterceptor;
import org.springframework.grpc.server.exception.CompositeGrpcExceptionHandler;
import org.springframework.grpc.server.exception.GrpcExceptionHandler;
import org.springframework.grpc.server.exception.GrpcExceptionHandlerInterceptor;

import java.util.ArrayList;
import java.util.List;

/**
 * gRPC 服务端自动配置，所有 Bean 均可被应用同类型 Bean 覆盖。
 * 异常处理器的顺序：应用自己的 {@code @GrpcAdvice} / 处理器 → 业务异常处理 → 兜底。
 *
 * @author Stephen
 */
// before GrpcServerAutoConfiguration：我们提供的异常拦截器要先注册，Boot 自己的那个（@ConditionalOnMissingBean）才会让位
// afterName 可用性自动配置：HealthUpdateListener 要在 ApplicationAvailabilityBean 更新完状态之后再同步，
// 同顺序的监听器按 Bean 注册先后执行
@AutoConfiguration(before = GrpcServerAutoConfiguration.class,
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
     * 异常拦截器的顺序：紧贴观测拦截器（{@code @Order(0)}）的内侧。
     * <p>Boot 默认把异常拦截器放在最外层，异常是在观测范围之外被处理的，于是：服务端指标 / span 看不到失败调用的真实状态
     * （业务异常、未知异常都记成 UNKNOWN），异常处理时打的日志也没有追踪号。放进观测范围内就都正常了。
     * <p>顺序比它小的拦截器（更靠外）抛出的异常不会经过这里的处理器。
     */
    static final int EXCEPTION_INTERCEPTOR_ORDER = 1;

    @Bean
    @Order(EXCEPTION_INTERCEPTOR_ORDER)
    @GlobalServerInterceptor
    @ConditionalOnMissingBean
    GrpcExceptionHandlerInterceptor ssGrpcExceptionHandlerInterceptor(ObjectProvider<GrpcExceptionHandler> handlers) {
        return new GrpcExceptionHandlerInterceptor(new CompositeGrpcExceptionHandler(
                inPrecedenceOrder(handlers.orderedStream().toList()).toArray(GrpcExceptionHandler[]::new)));
    }

    /**
     * 处理器的先后：应用自己的（{@code @GrpcAdvice} 等）→ 业务异常处理 → 兜底。
     * 兜底对任何异常都返回结果，必须排在最后；这里显式保证，不依赖 Bean 的注册先后（顺序值相同时按注册先后）。
     */
    static List<GrpcExceptionHandler> inPrecedenceOrder(List<GrpcExceptionHandler> handlers) {
        List<GrpcExceptionHandler> result = new ArrayList<>();
        GrpcExceptionHandler business = null;
        GrpcExceptionHandler fallback = null;
        for (GrpcExceptionHandler handler : handlers) {
            if (handler instanceof FallbackExceptionHandler) {
                fallback = handler;
            } else if (handler instanceof BusinessExceptionHandler) {
                business = handler;
            } else {
                result.add(handler);
            }
        }
        if (business != null) {
            result.add(business);
        }
        if (fallback != null) {
            result.add(fallback);
        }
        return result;
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
