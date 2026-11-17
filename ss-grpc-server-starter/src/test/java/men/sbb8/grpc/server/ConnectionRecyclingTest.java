package men.sbb8.grpc.server;

import io.grpc.EquivalentAddressGroup;
import io.grpc.ManagedChannel;
import io.grpc.NameResolver;
import io.grpc.NameResolverProvider;
import io.grpc.NameResolverRegistry;
import io.grpc.Server;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import men.sbb8.grpc.server.test.GreeterGrpc;
import men.sbb8.grpc.server.test.HelloReply;
import men.sbb8.grpc.server.test.HelloRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.grpc.test.autoconfigure.LocalGrpcServerPort;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * k8s 用 headless Service 做服务发现时，客户端靠"连接断开后重新解析 DNS"才能发现新扩容的 pod；
 * 服务端设置连接最长存活时间（max-age）就是让连接定期优雅断开，触发这次重新解析。
 * 用一个会统计 {@code refresh()} 次数的解析器代替 DNS，在真实网络上验证：设了 max-age 会重新解析，没设就不会。
 */
@SpringBootTest(properties = {
        "spring.grpc.server.port=0",
        // 测试里缩短：属性名和生效路径与生产一致，只是时间短
        "spring.grpc.server.keepalive.connection.max-age=1s",
        "spring.grpc.server.keepalive.connection.grace-period=500ms"
})
class ConnectionRecyclingTest {

    /** 统计 refresh() 次数的"DNS"：只解析到固定地址 */
    private static final class CountingProvider extends NameResolverProvider {

        final AtomicInteger refreshes = new AtomicInteger();
        volatile int port;

        @Override
        protected boolean isAvailable() {
            return true;
        }

        @Override
        protected int priority() {
            return 5;
        }

        @Override
        public String getDefaultScheme() {
            return "recount";
        }

        @Override
        public NameResolver newNameResolver(URI targetUri, NameResolver.Args args) {
            return new NameResolver() {
                private Listener2 listener;

                @Override
                public String getServiceAuthority() {
                    return "svc";
                }

                @Override
                public void start(Listener2 listener) {
                    this.listener = listener;
                    push();
                }

                @Override
                public void refresh() {
                    refreshes.incrementAndGet();
                    push();
                }

                private void push() {
                    listener.onResult(ResolutionResult.newBuilder()
                            .setAddresses(List.of(new EquivalentAddressGroup(new InetSocketAddress("127.0.0.1", port))))
                            .build());
                }

                @Override
                public void shutdown() {
                }
            };
        }
    }

    @LocalGrpcServerPort
    int springServerPort;

    private final CountingProvider dns = new CountingProvider();
    private ManagedChannel channel;
    private Server plainServer;

    @BeforeEach
    void register() {
        NameResolverRegistry.getDefaultRegistry().register(dns);
    }

    @AfterEach
    void cleanup() throws Exception {
        NameResolverRegistry.getDefaultRegistry().deregister(dns);
        if (channel != null) {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
        if (plainServer != null) {
            plainServer.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private ManagedChannel channelTo(int port) {
        dns.port = port;
        // round_robin 是 Spring gRPC 客户端的默认负载均衡策略
        return NettyChannelBuilder.forTarget("recount:///svc").defaultLoadBalancingPolicy("round_robin")
                .usePlaintext().build();
    }

    private static String hello(ManagedChannel channel) {
        return GreeterGrpc.newBlockingStub(channel).withDeadlineAfter(5, TimeUnit.SECONDS)
                .hello(HelloRequest.newBuilder().setName("x").build()).getMessage();
    }

    @Test
    void shippedDefaultIsTenMinutes() {
        // 本测试覆盖成了 1s；这里直接读 starter 的默认配置文件，确认发布的默认值
        var defaults = new org.springframework.boot.env.YamlPropertySourceLoader();
        try {
            var sources = defaults.load("d", new org.springframework.core.io.ClassPathResource(
                    "application.yaml"));
            var env2 = new org.springframework.core.env.StandardEnvironment();
            sources.forEach(env2.getPropertySources()::addLast);
            assertEquals(Duration.ofMinutes(10), Binder.get(env2)
                    .bind("spring.grpc.server.keepalive.connection.max-age", Duration.class).get());
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    @Test
    void maxAgeRecyclesTheConnectionAndTheClientResolvesAgain() {
        channel = channelTo(springServerPort);
        assertEquals("hi x", hello(channel));
        int before = dns.refreshes.get();

        // 连接到期被服务端优雅关闭，客户端的负载均衡策略随之要求重新解析
        await().atMost(Duration.ofSeconds(15)).until(() -> dns.refreshes.get() > before);

        // 重新解析后连接恢复，调用照常成功
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertEquals("hi x", hello(channel)));
    }

    @Test
    void withoutMaxAgeNothingTriggersAResolve() throws Exception {
        // 对照：没有 max-age 的服务端，连接一直不断，客户端不会重新解析（这正是不设 max-age 时新 pod 发现不了的原因）
        plainServer = NettyServerBuilder.forPort(0).addService(new GreeterGrpc.GreeterImplBase() {
            @Override
            public void hello(HelloRequest request, StreamObserver<HelloReply> out) {
                out.onNext(HelloReply.newBuilder().setMessage("hi " + request.getName()).build());
                out.onCompleted();
            }
        }).build().start();
        channel = channelTo(plainServer.getPort());
        assertEquals("hi x", hello(channel));
        int before = dns.refreshes.get();

        Thread.sleep(4_000);

        assertThat(dns.refreshes.get()).isEqualTo(before);
        assertEquals("hi x", hello(channel));
    }
}
