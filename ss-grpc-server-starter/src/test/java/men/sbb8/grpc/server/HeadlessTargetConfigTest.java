package men.sbb8.grpc.server;

import men.sbb8.grpc.server.test.GreeterGrpc;
import men.sbb8.grpc.server.test.HelloRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.grpc.client.autoconfigure.GrpcClientProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.grpc.client.ImportGrpcClients;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * README 里 k8s headless Service 的客户端用法：通道用 {@code dns:///服务名:端口} 作为目标，默认 round_robin。
 * 这里把 headless 的服务名换成 localhost，验证文档里的配置项和注解写法是有效的。
 */
@SpringBootTest
@ImportGrpcClients(target = "peer", types = GreeterGrpc.GreeterBlockingStub.class)
class HeadlessTargetConfigTest {

    private static final int PORT = freePort();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.grpc.server.port", () -> PORT);
        registry.add("spring.grpc.client.channel.peer.target", () -> "dns:///localhost:" + PORT);
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Autowired
    GreeterGrpc.GreeterBlockingStub stub;

    @Autowired
    GrpcClientProperties properties;

    @Test
    void dnsTargetFromConfigurationWorks() {
        assertEquals("hi headless", stub.hello(HelloRequest.newBuilder().setName("headless").build()).getMessage());
    }

    @Test
    void defaultLoadBalancingPolicyIsRoundRobin() {
        assertEquals("round_robin", properties.getChannel().get("peer").getDefault().getLoadBalancingPolicy());
    }
}
