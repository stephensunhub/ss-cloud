package men.sbb8.grpc.core.config;

import com.sun.net.httpserver.HttpServer;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import men.sbb8.grpc.core.metrics.PrometheusScrapeServer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * gRPC 服务端、客户端共用的自动配置：Prometheus 抓取端口。所有 Bean 均可被应用同类型 Bean 覆盖。
 *
 * @author Stephen
 */
@AutoConfiguration
@EnableConfigurationProperties(SsGrpcProperties.class)
public class SsGrpcCoreAutoConfiguration {

    /**
     * Prometheus 抓取端口：ss.grpc.metrics.prometheus.enabled=false 关闭
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass({PrometheusMeterRegistry.class, HttpServer.class})
    @ConditionalOnProperty(prefix = "ss.grpc.metrics.prometheus", name = "enabled", matchIfMissing = true)
    static class MetricsConfiguration {

        @Bean
        @ConditionalOnMissingBean
        PrometheusScrapeServer ssPrometheusScrapeServer(ObjectProvider<PrometheusMeterRegistry> registry,
                                                       SsGrpcProperties properties) {
            SsGrpcProperties.Prometheus prometheus = properties.getMetrics().getPrometheus();
            return new PrometheusScrapeServer(registry::getIfAvailable, prometheus.getPort(), prometheus.getPath());
        }
    }
}
