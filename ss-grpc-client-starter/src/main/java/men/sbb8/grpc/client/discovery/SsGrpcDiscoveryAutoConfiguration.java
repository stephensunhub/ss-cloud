package men.sbb8.grpc.client.discovery;

import io.grpc.ManagedChannelBuilder;
import io.grpc.NameResolverProvider;
import men.sbb8.grpc.client.config.SsGrpcClientProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.grpc.client.GrpcChannelBuilderCustomizer;

/**
 * 类路径上有 Spring Cloud 的 {@link DiscoveryClient} 实现时，启用 {@code discovery:///服务名} 目标。
 * 应用不引入 spring-cloud-commons，或设置 {@code ss.grpc.client.discovery.enabled=false} 时不生效。
 * 原生镜像的条件在构建期固定，运行时改这个开关不起作用。
 *
 * @author Stephen
 */
@AutoConfiguration(afterName = "org.springframework.cloud.client.discovery.composite.CompositeDiscoveryClientAutoConfiguration")
@ConditionalOnClass({DiscoveryClient.class, NameResolverProvider.class, GrpcChannelBuilderCustomizer.class})
@ConditionalOnBean(DiscoveryClient.class)
@ConditionalOnProperty(prefix = "ss.grpc.client.discovery", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(SsGrpcClientProperties.class)
public class SsGrpcDiscoveryAutoConfiguration {

    @Bean(destroyMethod = "deregister")
    @ConditionalOnMissingBean
    DiscoveryNameResolverProvider ssDiscoveryNameResolverProvider(DiscoveryClient discoveryClient,
                                                                  SsGrpcClientProperties properties) {
        return new DiscoveryNameResolverProvider(discoveryClient, properties.getDiscovery().getRefreshInterval());
    }

    /**
     * 每个 channel 创建之前确保解析器已注册，不依赖 Bean 的创建顺序
     */
    @Bean
    <T extends ManagedChannelBuilder<T>> GrpcChannelBuilderCustomizer<T> ssDiscoveryChannelCustomizer(
            DiscoveryNameResolverProvider provider) {
        return (target, builder) -> provider.register();
    }
}
