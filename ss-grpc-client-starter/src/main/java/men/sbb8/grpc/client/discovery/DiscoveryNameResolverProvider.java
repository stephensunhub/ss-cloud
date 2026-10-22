package men.sbb8.grpc.client.discovery;

import io.grpc.NameResolver;
import io.grpc.NameResolverProvider;
import io.grpc.NameResolverRegistry;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.discovery.event.HeartbeatEvent;
import org.springframework.context.ApplicationListener;

import java.net.URI;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * {@code discovery:///服务名} 目标的名称解析：实例列表来自 Spring Cloud 的 {@link DiscoveryClient}，
 * 因此注册中心可以是 Consul、Nacos 等任何 Spring Cloud 支持的实现。
 * <p>
 * 实例的 gRPC 端口取 metadata 中的 {@code gRPC_port}，没有则用实例端口。
 * Spring Cloud 发布 {@link HeartbeatEvent} 时刷新全部解析器，另有兜底定时器防止事件缺失。
 * 只在 JVM 上可用：Spring Cloud 的各注册中心实现目前不支持原生镜像。
 *
 * @author Stephen
 */
public final class DiscoveryNameResolverProvider extends NameResolverProvider
        implements ApplicationListener<HeartbeatEvent> {

    public static final String SCHEME = "discovery";

    private final DiscoveryClient discoveryClient;
    private final Duration fallbackInterval;
    private final Set<DiscoveryNameResolver> resolvers = new CopyOnWriteArraySet<>();
    private final AtomicBoolean registered = new AtomicBoolean();

    public DiscoveryNameResolverProvider(DiscoveryClient discoveryClient, Duration fallbackInterval) {
        if (fallbackInterval.isZero() || fallbackInterval.isNegative()) {
            throw new IllegalArgumentException("ss.grpc.client.discovery.refresh-interval 必须大于 0：" + fallbackInterval);
        }
        this.discoveryClient = discoveryClient;
        this.fallbackInterval = fallbackInterval;
    }

    /**
     * 注册到 gRPC 的全局解析器表，重复调用无副作用。必须在创建 channel 之前完成
     */
    public void register() {
        if (registered.compareAndSet(false, true)) {
            NameResolverRegistry.getDefaultRegistry().register(this);
        }
    }

    boolean isRegistered() {
        return registered.get();
    }

    public void deregister() {
        if (registered.compareAndSet(true, false)) {
            NameResolverRegistry.getDefaultRegistry().deregister(this);
        }
    }

    @Override
    public NameResolver newNameResolver(URI targetUri, NameResolver.Args args) {
        if (!SCHEME.equals(targetUri.getScheme())) {
            return null;
        }
        String serviceId = serviceId(targetUri);
        DiscoveryNameResolver resolver = new DiscoveryNameResolver(serviceId, discoveryClient, fallbackInterval,
                args.getScheduledExecutorService(), args.getOffloadExecutor(), args.getSynchronizationContext(),
                resolvers::remove);
        resolvers.add(resolver);
        return resolver;
    }

    /**
     * {@code discovery:///name} 与 {@code discovery://name} 都支持；服务名含下划线时 URI 没有 host，只能取 authority
     */
    static String serviceId(URI targetUri) {
        String id = targetUri.getAuthority();
        if (id == null || id.isEmpty()) {
            String path = targetUri.getPath();
            id = path == null ? "" : path.startsWith("/") ? path.substring(1) : path;
        }
        if (id.isBlank()) {
            throw new IllegalArgumentException("discovery 目标缺少服务名，应为 discovery:///服务名：" + targetUri);
        }
        return id;
    }

    @Override
    public void onApplicationEvent(HeartbeatEvent event) {
        resolvers.forEach(DiscoveryNameResolver::refresh);
    }

    @Override
    public String getDefaultScheme() {
        return SCHEME;
    }

    @Override
    protected boolean isAvailable() {
        return true;
    }

    @Override
    protected int priority() {
        return 5;
    }
}
