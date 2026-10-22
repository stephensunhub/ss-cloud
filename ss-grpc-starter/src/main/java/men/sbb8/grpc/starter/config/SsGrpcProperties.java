package men.sbb8.grpc.starter.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * gRPC starter 配置，前缀 {@code ss.grpc}，全部有默认值，引入依赖即可使用
 *
 * @author Stephen
 */
@Getter
@ConfigurationProperties(prefix = "ss.grpc")
public class SsGrpcProperties {

    private final Client client = new Client();

    @Setter
    @Getter
    public static class Client {

        /**
         * 一元调用没有显式设置 deadline 时使用的默认超时；设为 0 关闭。流式调用不受影响
         */
        private Duration defaultDeadline = Duration.ofSeconds(30);

    }
}
