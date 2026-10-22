package men.sbb8.grpc.client.discovery;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.grpc.client.GrpcChannelBuilderCustomizer;

import static org.assertj.core.api.Assertions.assertThat;

class SsGrpcDiscoveryAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SsGrpcDiscoveryAutoConfiguration.class))
            .withBean(DiscoveryClient.class, FakeDiscoveryClient::new);

    @Test
    void enabledWhenDiscoveryClientPresent() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(DiscoveryNameResolverProvider.class);
            assertThat(context).hasSingleBean(GrpcChannelBuilderCustomizer.class);
        });
    }

    @Test
    void registeredWhenAChannelIsCustomizedAndDeregisteredOnClose() {
        DiscoveryNameResolverProvider[] seen = new DiscoveryNameResolverProvider[1];
        runner.run(context -> {
            seen[0] = context.getBean(DiscoveryNameResolverProvider.class);
            assertThat(seen[0].isRegistered()).isFalse();
            context.getBean(GrpcChannelBuilderCustomizer.class).customize("discovery:///svc", null);
            assertThat(seen[0].isRegistered()).isTrue();
        });
        // 容器关闭后注销，不污染同一 JVM 里的其他容器
        assertThat(seen[0].isRegistered()).isFalse();
    }

    @Test
    void disabledByProperty() {
        runner.withPropertyValues("ss.grpc.client.discovery.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(DiscoveryNameResolverProvider.class));
    }

    @Test
    void absentWithoutSpringCloudOnClasspath() {
        runner.withClassLoader(new FilteredClassLoader(DiscoveryClient.class))
                .run(context -> assertThat(context).doesNotHaveBean(DiscoveryNameResolverProvider.class));
    }

    @Test
    void absentWithoutAnyDiscoveryClientBean() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SsGrpcDiscoveryAutoConfiguration.class))
                .run(context -> assertThat(context).doesNotHaveBean(DiscoveryNameResolverProvider.class));
    }

    @Test
    void refreshIntervalComesFromProperties() {
        runner.withPropertyValues("ss.grpc.client.discovery.refresh-interval=0s")
                .run(context -> assertThat(context).hasFailed());
    }
}
