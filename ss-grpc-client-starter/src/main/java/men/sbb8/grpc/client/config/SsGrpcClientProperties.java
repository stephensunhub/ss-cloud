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

}
