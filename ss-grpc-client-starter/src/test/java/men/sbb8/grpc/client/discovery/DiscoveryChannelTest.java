package men.sbb8.grpc.client.discovery;

import io.grpc.CallOptions;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerServiceDefinition;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.ServerCalls;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.discovery.event.HeartbeatEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.grpc.client.GrpcChannelFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static men.sbb8.grpc.client.discovery.FakeDiscoveryClient.grpcInstance;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * 真实的两个 gRPC 服务端 + Spring 创建的 channel：证明 discovery 目标能被 channel 工厂识别、
 * 按 metadata 里的 gRPC 端口连接、轮询负载均衡，并在心跳之后跟随实例变化
 */
@SpringBootTest
@Import(DiscoveryChannelTest.Config.class)
class DiscoveryChannelTest {

    static final MethodDescriptor.Marshaller<String> TEXT = new MethodDescriptor.Marshaller<>() {
        @Override
        public InputStream stream(String value) {
            return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public String parse(InputStream stream) {
            try {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
    };

    static final MethodDescriptor<String, String> WHO = MethodDescriptor.<String, String>newBuilder()
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("test.Who/Who")
            .setRequestMarshaller(TEXT)
            .setResponseMarshaller(TEXT)
            .build();

    static final Server SERVER_A = start("A");
    static final Server SERVER_B = start("B");
    static final FakeDiscoveryClient CLIENT = new FakeDiscoveryClient();

    static Server start(String name) {
        try {
            return ServerBuilder.forPort(0)
                    .addService(ServerServiceDefinition.builder("test.Who")
                            .addMethod(WHO, ServerCalls.asyncUnaryCall((request, observer) -> {
                                observer.onNext(name);
                                observer.onCompleted();
                            }))
                            .build())
                    .build().start();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @AfterAll
    static void stopServers() throws InterruptedException {
        SERVER_A.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        SERVER_B.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    @TestConfiguration
    static class Config {
        @Bean
        DiscoveryClient fakeDiscoveryClient() {
            return CLIENT;
        }
    }

    @Autowired
    GrpcChannelFactory channels;

    @Autowired
    ApplicationContext context;

    private static Set<String> callMany(ManagedChannel channel, int times) {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < times; i++) {
            seen.add(ClientCalls.blockingUnaryCall(channel, WHO, CallOptions.DEFAULT.withDeadlineAfter(5, TimeUnit.SECONDS), "?"));
        }
        return seen;
    }

    @Test
    void balancesAcrossInstancesAndFollowsChanges() {
        // HTTP 端口故意写一个连不上的，证明连接用的是 metadata 里的 gRPC 端口
        CLIENT.set(grpcInstance("127.0.0.1", 1, SERVER_A.getPort()), grpcInstance("127.0.0.1", 1, SERVER_B.getPort()));
        ManagedChannel channel = channels.createChannel("discovery:///svc");
        try {
            await().atMost(Duration.ofSeconds(10)).untilAsserted(
                    () -> assertThat(callMany(channel, 20)).containsExactlyInAnyOrder("A", "B"));

            CLIENT.set(grpcInstance("127.0.0.1", 1, SERVER_B.getPort()));
            context.publishEvent(new HeartbeatEvent(this, 2L));
            await().atMost(Duration.ofSeconds(10)).untilAsserted(
                    () -> assertThat(callMany(channel, 20)).containsExactly("B"));
        } finally {
            channel.shutdownNow();
        }
    }
}
