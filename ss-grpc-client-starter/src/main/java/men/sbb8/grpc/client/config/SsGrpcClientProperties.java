package men.sbb8.grpc.client.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * gRPC 客户端配置，前缀 {@code ss.grpc.client}，全部有默认值，引入依赖即可使用
 *
 * @author Stephen
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ss.grpc.client")
public class SsGrpcClientProperties {

    /**
     * 一元调用没有显式设置 deadline 时使用的默认超时；设为 0 关闭。流式调用不受影响
     */
    private Duration defaultDeadline = Duration.ofSeconds(30);

    /**
     * 基于 Spring Cloud DiscoveryClient 的服务发现，目标写作 {@code discovery:///服务名}
     */
    private Discovery discovery = new Discovery();

    @Getter
    @Setter
    public static class Discovery {

        /**
         * 是否启用 discovery 目标；类路径上没有 Spring Cloud 的 DiscoveryClient 时无论如何都不生效
         */
        private boolean enabled = true;

        /**
         * 兜底刷新间隔：正常由 Spring Cloud 的心跳事件触发刷新，这个定时器只在事件缺失时起作用。
         * 每个被调用的服务每个间隔最多查询一次注册中心，与请求量无关
         */
        private Duration refreshInterval = Duration.ofSeconds(10);
    }

}
