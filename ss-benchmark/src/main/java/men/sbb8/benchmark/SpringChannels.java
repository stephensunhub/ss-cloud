package men.sbb8.benchmark;

import io.grpc.ManagedChannel;
import io.grpc.netty.NettyChannelBuilder;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.ChannelBuilderOptions;
import org.springframework.grpc.client.GrpcChannelBuilderCustomizer;
import org.springframework.grpc.client.GrpcChannelFactory;

/**
 * 通过 ss-grpc-client-starter（以及 Spring gRPC 的客户端、观测、指标、追踪）创建压测用的连接，
 * 和 {@link BenchClient} 里原生创建的连接用同样的线程模型（directExecutor），这样两者的差别只来自 starter。
 * 观测、采样率等开关用 {@code -D} 系统属性切换（和服务端一样），例如 {@code -Dspring.profiles.active=prod}。
 * 这个 JVM 里不需要服务端和抓取端口（模块里同时有服务端 starter），所以显式关闭。
 *
 * @author Stephen
 */
final class SpringChannels implements AutoCloseable {

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class App {
    }

    private final ConfigurableApplicationContext context;
    private final ManagedChannel[] channels;

    SpringChannels(String host, int port, int count) {
        this.context = new SpringApplicationBuilder(App.class).web(WebApplicationType.NONE)
                .run("--spring.grpc.server.enabled=false", "--ss.grpc.metrics.prometheus.enabled=false",
                        "--spring.application.name=ss-benchmark-client", "--spring.main.banner-mode=off",
                        "--logging.level.root=WARN");
        GrpcChannelFactory factory = context.getBean(GrpcChannelFactory.class);
        GrpcChannelBuilderCustomizer<NettyChannelBuilder> direct = (name, builder) -> builder.directExecutor();
        ChannelBuilderOptions options = ChannelBuilderOptions.defaults().withCustomizer(direct);
        this.channels = new ManagedChannel[count];
        for (int i = 0; i < count; i++) {
            channels[i] = factory.createChannel(host + ":" + port, options);
        }
    }

    ManagedChannel[] channels() {
        return channels;
    }

    /** 自检：客户端指标确实在记录（观测开着时），否则比较的就不是完整的 starter */
    String describe() {
        io.micrometer.core.instrument.MeterRegistry registry =
                context.getBeanProvider(io.micrometer.core.instrument.MeterRegistry.class).getIfAvailable();
        long timed = registry == null ? -1 : registry.find("grpc.client").timers().stream()
                .mapToLong(io.micrometer.core.instrument.Timer::count).sum();
        return "grpc.client 计时器记录数=" + timed;
    }

    @Override
    public void close() {
        for (ManagedChannel channel : channels) {
            channel.shutdownNow();
        }
        context.close();
    }
}
