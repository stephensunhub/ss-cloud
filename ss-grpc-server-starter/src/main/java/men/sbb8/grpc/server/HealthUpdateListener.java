package men.sbb8.grpc.server;

import io.grpc.protobuf.services.HealthStatusManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.grpc.server.health.GrpcServerHealth;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;

/**
 * 应用的存活 / 就绪状态一变化，立刻把结果同步到 gRPC 健康服务，不等 Boot 的定时同步（默认延迟 5 秒首次、之后每 5 秒）。
 * <ul>
 *     <li>启动完成后立即可探测：否则启动后的前几秒 liveness / readiness 会是 NOT_FOUND，k8s 探针判为失败</li>
 *     <li>优雅停机时就绪状态翻转后立即变为未就绪，k8s 马上停止派发流量，而不是继续派发最多 5 秒</li>
 * </ul>
 * 定时同步仍然保留，用来反映数据库等健康贡献者的变化。
 *
 * @author Stephen
 */
@Slf4j
public class HealthUpdateListener implements ApplicationListener<ApplicationEvent>, Ordered {

    private final ObjectProvider<GrpcServerHealth> health;
    private final ObjectProvider<HealthStatusManager> manager;

    public HealthUpdateListener(ObjectProvider<GrpcServerHealth> health, ObjectProvider<HealthStatusManager> manager) {
        this.health = health;
        this.manager = manager;
    }

    /**
     * 最低优先级：要在 ApplicationAvailabilityBean 先更新状态之后再同步
     */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        if (!(event instanceof AvailabilityChangeEvent<?> || event instanceof ApplicationReadyEvent)) {
            return;
        }
        GrpcServerHealth grpcHealth = health.getIfAvailable();
        HealthStatusManager statusManager = manager.getIfAvailable();
        if (grpcHealth == null || statusManager == null) {
            return;
        }
        try {
            grpcHealth.update(statusManager);
        } catch (RuntimeException e) {
            log.debug("同步 gRPC 健康状态失败，等待下一次定时同步", e);
        }
    }
}
