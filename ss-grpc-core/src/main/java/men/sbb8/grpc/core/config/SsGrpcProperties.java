package men.sbb8.grpc.core.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * gRPC 公共配置，前缀 {@code ss.grpc}（服务端、客户端共用的部分），全部有默认值，引入依赖即可使用
 *
 * @author Stephen
 */
@Getter
@ConfigurationProperties(prefix = "ss.grpc")
public class SsGrpcProperties {

    private final Metrics metrics = new Metrics();

    @Getter
    public static class Metrics {

        private final Prometheus prometheus = new Prometheus();

    }

    /**
     * 没有 web 容器，所以用 JDK 自带的 HttpServer 在独立端口提供 Prometheus 抓取
     */
    @Setter
    @Getter
    public static class Prometheus {

        /**
         * 是否开启抓取端口；关闭后不监听任何 HTTP 端口，指标仍在内存里（可改用其他方式上报）
         */
        private boolean enabled = true;

        /**
         * 监听端口，0 表示随机端口（测试用）
         */
        private int port = 9091;

        /**
         * 抓取路径
         */
        private String path = "/metrics";

    }
}
