package men.sbb8.grpc.client.discovery;

import io.grpc.EquivalentAddressGroup;
import io.grpc.NameResolver;
import io.grpc.Status;
import io.grpc.SynchronizationContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 把 Spring Cloud 的 {@link DiscoveryClient} 接到 gRPC 的名称解析：按服务名取实例列表，推给负载均衡策略。
 * <p>
 * 请求路径上不查注册中心：地址只在这里更新，每个 RPC 由负载均衡策略在本地挑选连接。
 * 更新由 {@link #refresh()} 触发（心跳事件、gRPC 连接失败、兜底定时器），同一时刻只有一次查询在进行，
 * 查询在 gRPC 的 offload 线程里做，不占用请求线程和 gRPC 的同步上下文；
 * 结果再交回 channel 的同步上下文回调（gRPC 要求如此），查询线程等回调执行完后才结束这一轮。
 * <ul>
 * <li>列表没有变化就不推送；</li>
 * <li>查询失败：已有列表时保留不动，从未成功过则报 UNAVAILABLE，让调用快速失败；</li>
 * <li>查询成功但没有实例：如实推送空列表。</li>
 * </ul>
 *
 * @author Stephen
 */
@Slf4j
final class DiscoveryNameResolver extends NameResolver {

    /**
     * 实例 metadata 里声明 gRPC 端口的键；没有则用实例自身的端口
     */
    static final String PORT_METADATA_KEY = "gRPC_port";

    private static final int WARN_EVERY_N_FAILURES = 20;
    private static final long LISTENER_TIMEOUT_SECONDS = 30;

    private final String serviceId;
    private final DiscoveryClient discoveryClient;
    private final long fallbackIntervalNanos;
    private final ScheduledExecutorService scheduler;
    private final Executor lookupExecutor;
    private final SynchronizationContext syncContext;
    private final Consumer<DiscoveryNameResolver> onShutdown;

    private final AtomicBoolean lookingUp = new AtomicBoolean();
    private volatile boolean shutdown;
    private volatile Listener2 listener;
    private volatile ScheduledFuture<?> fallback;

    // 以下状态只在单次查询内（lookingUp 保证同一时刻只有一个）被修改
    private Set<InetSocketAddress> lastPushed;
    private int consecutiveFailures;

    DiscoveryNameResolver(String serviceId, DiscoveryClient discoveryClient, Duration fallbackInterval,
                          ScheduledExecutorService scheduler, Executor lookupExecutor,
                          SynchronizationContext syncContext, Consumer<DiscoveryNameResolver> onShutdown) {
        this.serviceId = serviceId;
        this.discoveryClient = discoveryClient;
        this.fallbackIntervalNanos = fallbackInterval.toNanos();
        this.scheduler = scheduler;
        this.lookupExecutor = lookupExecutor;
        this.syncContext = syncContext;
        this.onShutdown = onShutdown;
    }

    @Override
    public String getServiceAuthority() {
        return serviceId;
    }

    @Override
    public void start(Listener2 listener) {
        this.listener = listener;
        refresh();
    }

    /**
     * 如果已有一次查询在进行，这次请求被合并：那次查询之后兜底定时器与下一次心跳会再覆盖
     */
    @Override
    public void refresh() {
        if (shutdown || listener == null || !lookingUp.compareAndSet(false, true)) {
            return;
        }
        try {
            lookupExecutor.execute(this::lookup);
        } catch (RejectedExecutionException e) {
            lookingUp.set(false);
        }
    }

    @Override
    public void shutdown() {
        shutdown = true;
        ScheduledFuture<?> future = fallback;
        if (future != null) {
            future.cancel(false);
        }
        onShutdown.accept(this);
    }

    private void lookup() {
        try {
            if (shutdown) {
                return;
            }
            List<InetSocketAddress> addresses;
            try {
                addresses = toAddresses(discoveryClient.getInstances(serviceId));
            } catch (RuntimeException e) {
                onLookupFailed(e);
                return;
            }
            if (consecutiveFailures > 0) {
                log.info("服务 {} 的实例查询已恢复（此前连续失败 {} 次）", serviceId, consecutiveFailures);
                consecutiveFailures = 0;
            }
            push(new LinkedHashSet<>(addresses));
        } finally {
            lookingUp.set(false);
            scheduleFallback();
        }
    }

    private List<InetSocketAddress> toAddresses(List<ServiceInstance> instances) {
        return instances.stream()
                .map(this::toAddress)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(InetSocketAddress::toString))
                .toList();
    }

    private InetSocketAddress toAddress(ServiceInstance instance) {
        int port = instance.getPort();
        String declared = instance.getMetadata() == null ? null : instance.getMetadata().get(PORT_METADATA_KEY);
        if (declared != null) {
            try {
                port = Integer.parseInt(declared.trim());
            } catch (NumberFormatException e) {
                log.warn("服务 {} 的实例 {}:{} 的 metadata {}={} 不是端口号，改用实例端口", serviceId, instance.getHost(),
                        instance.getPort(), PORT_METADATA_KEY, declared);
            }
        }
        if (port <= 0 || port > 65535) {
            log.warn("服务 {} 的实例 {} 没有可用端口（{}），已忽略", serviceId, instance.getHost(), port);
            return null;
        }
        InetSocketAddress address = new InetSocketAddress(instance.getHost(), port);
        if (address.isUnresolved()) {
            log.warn("服务 {} 的实例地址 {}:{} 无法解析，已忽略", serviceId, instance.getHost(), port);
            return null;
        }
        return address;
    }

    private void push(Set<InetSocketAddress> next) {
        if (next.equals(lastPushed)) {
            return;
        }
        List<EquivalentAddressGroup> groups = next.stream().map(EquivalentAddressGroup::new).toList();
        ResolutionResult result = ResolutionResult.newBuilder().setAddresses(groups).build();
        Status status = callListener(l -> l.onResult2(result));
        // 空列表被 gRPC 判为错误是它的正常语义（它自己会退避重试），不用重复推送；
        // 其他情况被拒绝时不记录，下次查询再推
        if (status.isOk() || next.isEmpty()) {
            if (next.isEmpty()) {
                log.warn("服务 {} 当前没有可用实例", serviceId);
            } else {
                log.info("服务 {} 的实例列表更新为 {}", serviceId, next);
            }
            lastPushed = next;
        } else {
            log.warn("服务 {} 的实例列表被 gRPC 拒绝：{}", serviceId, status);
        }
    }

    private void onLookupFailed(RuntimeException e) {
        consecutiveFailures++;
        boolean noisy = consecutiveFailures == 1 || consecutiveFailures % WARN_EVERY_N_FAILURES == 0;
        String message = "查询服务 " + serviceId + " 的实例失败（连续第 " + consecutiveFailures + " 次）";
        if (noisy) {
            log.warn("{}：{}{}", message, e, lastPushed == null ? "" : "，保留上次的实例列表");
        } else {
            log.debug(message, e);
        }
        if (lastPushed == null) {
            Status error = Status.UNAVAILABLE.withDescription("无法查询服务 " + serviceId + " 的实例").withCause(e);
            callListener(l -> {
                l.onError(error);
                return Status.OK;
            });
        }
    }

    /**
     * gRPC 要求在 channel 的同步上下文里回调监听器；本方法在 offload 线程上调用，等回调执行完再返回
     */
    private Status callListener(Function<Listener2, Status> call) {
        CompletableFuture<Status> result = new CompletableFuture<>();
        syncContext.execute(() -> {
            try {
                result.complete(call.apply(listener));
            } catch (RuntimeException e) {
                result.completeExceptionally(e);
            }
        });
        try {
            return result.get(LISTENER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Status.CANCELLED.withCause(e);
        } catch (ExecutionException | TimeoutException e) {
            log.warn("服务 {} 的名称解析回调失败：{}", serviceId, e.toString());
            return Status.INTERNAL.withDescription("名称解析回调失败").withCause(e);
        }
    }

    private void scheduleFallback() {
        if (shutdown) {
            return;
        }
        ScheduledFuture<?> previous = fallback;
        if (previous != null) {
            previous.cancel(false);
        }
        try {
            fallback = scheduler.schedule(this::refresh, fallbackIntervalNanos, TimeUnit.NANOSECONDS);
        } catch (RejectedExecutionException e) {
            // channel 正在关闭
            return;
        }
        if (shutdown) {
            fallback.cancel(false);
        }
    }
}
