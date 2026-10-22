package men.sbb8.web.starter;

import men.sbb8.web.starter.dto.resp.Resp;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.client.loadbalancer.LoadBalancerRequest;
import org.springframework.cloud.client.loadbalancer.LoadBalancerUriTools;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ss.web.client.load-balanced-groups：base-url 里写服务名（http://wallet），请求经 Spring Cloud 的负载均衡解析到实例。
 * 这里用一个固定把 wallet 指向本机测试服务的 LoadBalancerClient 代替真实的服务发现。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = {
        "ss.web.client.load-balanced-groups=lb"
})
@Import(LoadBalancedGroupTest.StubLoadBalancer.class)
class LoadBalancedGroupTest {

    private static final int PORT = freePort();
    static final AtomicInteger RESOLVED = new AtomicInteger();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("server.port", () -> PORT);
        registry.add("management.server.port", () -> PORT);
        registry.add("spring.http.serviceclient.demo.base-url", () -> "http://localhost:" + PORT);
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @TestConfiguration
    static class StubLoadBalancer {
        @Bean
        LoadBalancerClient loadBalancerClient() {
            return new LoadBalancerClient() {
                private ServiceInstance instance(String serviceId) {
                    RESOLVED.incrementAndGet();
                    return new DefaultServiceInstance(serviceId + "-1", serviceId, "localhost", PORT, false);
                }

                @Override
                public ServiceInstance choose(String serviceId) {
                    return instance(serviceId);
                }

                @Override
                public <T> ServiceInstance choose(String serviceId, org.springframework.cloud.client.loadbalancer.Request<T> request) {
                    return instance(serviceId);
                }

                @Override
                public <T> T execute(String serviceId, LoadBalancerRequest<T> request) throws IOException {
                    return execute(serviceId, instance(serviceId), request);
                }

                @Override
                public <T> T execute(String serviceId, ServiceInstance serviceInstance, LoadBalancerRequest<T> request) throws IOException {
                    try {
                        return request.apply(serviceInstance);
                    } catch (IOException | RuntimeException e) {
                        throw e;
                    } catch (Exception e) {
                        throw new IOException(e);
                    }
                }

                @Override
                public URI reconstructURI(ServiceInstance instance, URI original) {
                    return LoadBalancerUriTools.reconstructURI(instance, original);
                }
            };
        }
    }

    @Test
    void groupWithServiceNameAsHostIsResolvedThroughTheLoadBalancer() {
        int before = RESOLVED.get();
        Resp<Map<String, Object>> resp = RestClient.create("http://localhost:" + PORT).get().uri("/relay-lb")
                .retrieve().body(new ParameterizedTypeReference<Resp<Map<String, Object>>>() {});
        assertEquals("SUCCESS", resp.getCode());
        assertEquals(1, resp.getData().get("n"));
        assertTrue(RESOLVED.get() > before, "请求应经过负载均衡解析服务名 wallet");
    }

    @Test
    void groupsNotListedAreLeftAlone() {
        // down 分组没有列入 load-balanced-groups，base-url 是普通地址，不经负载均衡，照常报"连不上"
        RestClient plain = RestClient.builder().baseUrl("http://localhost:" + PORT)
                .defaultStatusHandler(HttpStatusCode::isError, (req, res) -> { }).build();
        int before = RESOLVED.get();
        var response = plain.get().uri("/relay-down").retrieve().toBodilessEntity();
        assertEquals(502, response.getStatusCode().value());
        assertEquals(before, RESOLVED.get(), "没有列入的分组不应经过负载均衡");
    }
}
