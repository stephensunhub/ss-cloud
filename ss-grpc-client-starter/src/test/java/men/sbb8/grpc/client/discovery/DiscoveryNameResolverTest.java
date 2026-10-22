package men.sbb8.grpc.client.discovery;

import io.grpc.EquivalentAddressGroup;
import io.grpc.NameResolver;
import io.grpc.Status;
import io.grpc.SynchronizationContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.discovery.event.HeartbeatEvent;

import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static men.sbb8.grpc.client.discovery.FakeDiscoveryClient.grpcInstance;
import static men.sbb8.grpc.client.discovery.FakeDiscoveryClient.instance;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

class DiscoveryNameResolverTest {

    /**
     * 记录 gRPC 收到的结果
     */
    static class Recorder extends NameResolver.Listener2 {
        final List<List<String>> results = new CopyOnWriteArrayList<>();
        final List<Status> errors = new CopyOnWriteArrayList<>();

        @Override
        public void onResult(NameResolver.ResolutionResult result) {
            List<String> addresses = new ArrayList<>();
            for (EquivalentAddressGroup group : result.getAddressesOrError().getValue()) {
                InetSocketAddress address = (InetSocketAddress) group.getAddresses().get(0);
                addresses.add(address.getAddress().getHostAddress() + ":" + address.getPort());
            }
            results.add(addresses);
        }

        @Override
        public void onError(Status error) {
            errors.add(error);
        }
    }

    private final FakeDiscoveryClient client = new FakeDiscoveryClient();
    private final Recorder recorder = new Recorder();
    private ScheduledExecutorService scheduler;
    private DiscoveryNameResolver resolver;

    @BeforeEach
    void setUp() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    @AfterEach
    void tearDown() {
        if (resolver != null) {
            resolver.shutdown();
        }
        scheduler.shutdownNow();
    }

    private static SynchronizationContext syncContext() {
        return new SynchronizationContext((t, e) -> {
            throw new AssertionError(e);
        });
    }

    private DiscoveryNameResolver resolver(Duration fallback, Executor lookupExecutor) {
        resolver = new DiscoveryNameResolver("svc", client, fallback, scheduler, lookupExecutor, syncContext(), r -> {
        });
        return resolver;
    }

    private DiscoveryNameResolver started() {
        DiscoveryNameResolver r = resolver(Duration.ofHours(1), Runnable::run);
        r.start(recorder);
        return r;
    }

    @Test
    void pushesInstancesWithGrpcPortFromMetadata() {
        client.set(grpcInstance("127.0.0.1", 8080, 9090), instance("127.0.0.1", 7000, Map.of()),
                instance("127.0.0.1", 7001, Map.of(DiscoveryNameResolver.PORT_METADATA_KEY, "abc")));
        started();
        assertThat(recorder.results).hasSize(1);
        // 声明了 gRPC_port 的用它；没声明或写错的用实例端口
        assertThat(recorder.results.get(0)).containsExactlyInAnyOrder("127.0.0.1:9090", "127.0.0.1:7000", "127.0.0.1:7001");
    }

    @Test
    void unchangedListIsNotPushedAgain() {
        client.set(instance("127.0.0.1", 9090, Map.of()));
        DiscoveryNameResolver r = started();
        r.refresh();
        r.refresh();
        assertThat(client.lookups).hasValue(3);
        assertThat(recorder.results).hasSize(1);
    }

    @Test
    void changedListIsPushed() {
        client.set(instance("127.0.0.1", 9090, Map.of()));
        DiscoveryNameResolver r = started();
        client.set(instance("127.0.0.1", 9090, Map.of()), instance("127.0.0.1", 9091, Map.of()));
        r.refresh();
        assertThat(recorder.results).hasSize(2);
        assertThat(recorder.results.get(1)).containsExactlyInAnyOrder("127.0.0.1:9090", "127.0.0.1:9091");
    }

    @Test
    void failureAfterSuccessKeepsTheLastList() {
        client.set(instance("127.0.0.1", 9090, Map.of()));
        DiscoveryNameResolver r = started();
        client.failWith(new IllegalStateException("consul down"));
        r.refresh();
        r.refresh();
        assertThat(recorder.results).hasSize(1);
        assertThat(recorder.errors).isEmpty();

        // 恢复后列表有变化，照常推送
        client.failWith(null);
        client.set(instance("127.0.0.1", 9091, Map.of()));
        r.refresh();
        assertThat(recorder.results).hasSize(2);
        assertThat(recorder.results.get(1)).containsExactly("127.0.0.1:9091");
    }

    @Test
    void failureBeforeAnySuccessReportsUnavailable() {
        client.failWith(new IllegalStateException("consul down"));
        started();
        assertThat(recorder.results).isEmpty();
        assertThat(recorder.errors).hasSize(1);
        assertThat(recorder.errors.get(0).getCode()).isEqualTo(Status.Code.UNAVAILABLE);
        assertThat(recorder.errors.get(0).getCause()).hasMessage("consul down");
    }

    @Test
    void successfulEmptyResultIsPushedOnce() {
        client.set(instance("127.0.0.1", 9090, Map.of()));
        DiscoveryNameResolver r = started();
        client.set();
        r.refresh();
        r.refresh();
        assertThat(recorder.results).hasSize(2);
        assertThat(recorder.results.get(1)).isEmpty();
    }

    @Test
    void concurrentRefreshesAreCoalescedIntoOneLookup() {
        List<Runnable> queued = new ArrayList<>();
        client.set(instance("127.0.0.1", 9090, Map.of()));
        DiscoveryNameResolver r = resolver(Duration.ofHours(1), queued::add);
        r.start(recorder);
        r.refresh();
        r.refresh();
        assertThat(queued).hasSize(1);
        queued.get(0).run();
        assertThat(client.lookups).hasValue(1);
        r.refresh();
        assertThat(queued).hasSize(2);
    }

    @Test
    void fallbackTimerRefreshesWithoutAnyEvent() {
        client.set(instance("127.0.0.1", 9090, Map.of()));
        resolver(Duration.ofMillis(50), Runnable::run).start(recorder);
        await().atMost(Duration.ofSeconds(5)).until(() -> client.lookups.get() >= 3);
        assertThat(recorder.results).hasSize(1);
    }

    @Test
    void nothingIsLookedUpAfterShutdown() throws Exception {
        client.set(instance("127.0.0.1", 9090, Map.of()));
        DiscoveryNameResolver r = resolver(Duration.ofMillis(50), Runnable::run);
        r.start(recorder);
        r.shutdown();
        r.refresh();
        int after = client.lookups.get();
        Thread.sleep(300);
        assertThat(client.lookups).hasValue(after);
    }

    @Test
    void heartbeatEventRefreshesEveryResolverOfTheProvider() {
        client.set(instance("127.0.0.1", 9090, Map.of()));
        DiscoveryNameResolverProvider provider = new DiscoveryNameResolverProvider(client, Duration.ofHours(1));
        NameResolver.Args args = NameResolver.Args.newBuilder()
                .setDefaultPort(80)
                .setProxyDetector(address -> null)
                .setSynchronizationContext(syncContext())
                .setServiceConfigParser(new NameResolver.ServiceConfigParser() {
                    @Override
                    public NameResolver.ConfigOrError parseServiceConfig(Map<String, ?> rawServiceConfig) {
                        return NameResolver.ConfigOrError.fromConfig(rawServiceConfig);
                    }
                })
                .setScheduledExecutorService(scheduler)
                .setOffloadExecutor(Runnable::run)
                .build();
        NameResolver nameResolver = provider.newNameResolver(URI.create("discovery:///svc"), args);
        assertThat(nameResolver.getServiceAuthority()).isEqualTo("svc");
        nameResolver.start(recorder);
        client.set(instance("127.0.0.1", 9091, Map.of()));

        provider.onApplicationEvent(new HeartbeatEvent(this, 1L));
        assertThat(recorder.results).hasSize(2);

        // 关闭后不再响应心跳
        nameResolver.shutdown();
        provider.onApplicationEvent(new HeartbeatEvent(this, 2L));
        assertThat(client.lookups).hasValue(2);
    }

    @Test
    void providerIgnoresOtherSchemes() {
        DiscoveryNameResolverProvider provider = new DiscoveryNameResolverProvider(client, Duration.ofSeconds(1));
        assertThat(provider.newNameResolver(URI.create("dns:///svc"), null)).isNull();
    }

    @Test
    void serviceIdFromTarget() {
        assertThat(DiscoveryNameResolverProvider.serviceId(URI.create("discovery:///order-service"))).isEqualTo("order-service");
        assertThat(DiscoveryNameResolverProvider.serviceId(URI.create("discovery://order-service"))).isEqualTo("order-service");
        assertThat(DiscoveryNameResolverProvider.serviceId(URI.create("discovery://order_service"))).isEqualTo("order_service");
        assertThatThrownBy(() -> DiscoveryNameResolverProvider.serviceId(URI.create("discovery:///")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refreshIntervalMustBePositive() {
        assertThatThrownBy(() -> new DiscoveryNameResolverProvider(client, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
