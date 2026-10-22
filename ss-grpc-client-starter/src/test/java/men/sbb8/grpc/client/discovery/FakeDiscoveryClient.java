package men.sbb8.grpc.client.discovery;

import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 可控的 DiscoveryClient：随时改实例列表、让下一次查询失败，并统计被查询的次数
 */
class FakeDiscoveryClient implements DiscoveryClient {

    final AtomicInteger lookups = new AtomicInteger();
    private volatile List<ServiceInstance> instances = List.of();
    private volatile RuntimeException failure;

    void set(ServiceInstance... instances) {
        this.instances = List.of(instances);
    }

    void failWith(RuntimeException failure) {
        this.failure = failure;
    }

    static ServiceInstance instance(String host, int port, Map<String, String> metadata) {
        return new DefaultServiceInstance(host + ":" + port, "svc", host, port, false, metadata);
    }

    static ServiceInstance grpcInstance(String host, int httpPort, int grpcPort) {
        return instance(host, httpPort, Map.of(DiscoveryNameResolver.PORT_METADATA_KEY, String.valueOf(grpcPort)));
    }

    @Override
    public String description() {
        return "fake";
    }

    @Override
    public List<ServiceInstance> getInstances(String serviceId) {
        lookups.incrementAndGet();
        RuntimeException failure = this.failure;
        if (failure != null) {
            throw failure;
        }
        return instances;
    }

    @Override
    public List<String> getServices() {
        return List.of("svc");
    }
}
