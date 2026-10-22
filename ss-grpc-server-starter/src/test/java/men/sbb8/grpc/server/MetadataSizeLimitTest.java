package men.sbb8.grpc.server;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import men.sbb8.grpc.core.error.BusinessCode;
import men.sbb8.grpc.core.error.ErrorDetails;
import men.sbb8.grpc.server.test.GreeterGrpc;
import men.sbb8.grpc.server.test.HelloReply;
import men.sbb8.grpc.server.test.HelloRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 错误详情随 trailers 返回，客户端（grpc-java 默认）只接受 8KB 的 metadata，超出会让调用方收到
 * RESOURCE_EXHAUSTED 而看不到真正的错误。这里走真实的网络，按最坏情况（大量超长参数、消息里引用了全部参数、
 * 非 ASCII 字符）验证 {@link ErrorDetails} 的上限足够，数值有变动时这个测试会立刻发现。
 */
class MetadataSizeLimitTest {

    /** 消息里引用全部 20 个可能保留的参数：最坏情况 */
    private static final String TEMPLATE = IntStream.range(0, ErrorDetails.MAX_PARAMS)
            .mapToObj(i -> "{{k" + i + "}}").collect(Collectors.joining());

    private enum WorstCase implements BusinessCode {
        INSTANCE;

        @Override
        public Status status() {
            return Status.INVALID_ARGUMENT;
        }

        @Override
        public String code() {
            return "WORST_CASE";
        }

        @Override
        public String msg() {
            return TEMPLATE;
        }
    }

    private static Server server;
    private static ManagedChannel channel;
    private static volatile Map<String, Object> params = Map.of();

    @BeforeAll
    static void start() throws Exception {
        server = NettyServerBuilder.forPort(0).addService(new GreeterGrpc.GreeterImplBase() {
            @Override
            public void hello(HelloRequest request, StreamObserver<HelloReply> out) {
                // 开启追踪时详情里还有 32 位的 traceId，同样算进最坏情况
                MDC.put("traceId", "0123456789abcdef0123456789abcdef");
                try {
                    out.onError(ErrorDetails.toStatusRuntimeException(WorstCase.INSTANCE, params));
                } finally {
                    MDC.remove("traceId");
                }
            }
        }).build().start();
        // 客户端不做任何额外配置：就是 grpc-java 的默认 metadata 上限（8192）
        channel = NettyChannelBuilder.forAddress("localhost", server.getPort()).usePlaintext().build();
    }

    @AfterAll
    static void stop() throws Exception {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    private static void callWith(String unit) {
        Map<String, Object> huge = new LinkedHashMap<>();
        for (int i = 0; i < 100; i++) {
            huge.put("k" + i, unit.repeat(10_000));
        }
        params = huge;
        StatusRuntimeException e = assertThrows(StatusRuntimeException.class,
                () -> GreeterGrpc.newBlockingStub(channel).hello(HelloRequest.newBuilder().setName("x").build()));
        assertEquals(Status.Code.INVALID_ARGUMENT, e.getStatus().getCode(),
                "客户端应收到真正的错误，而不是 metadata 过大导致的 " + e.getStatus());
        assertTrue(ErrorDetails.find(e).isPresent(), "ErrorDetail 应完整到达");
    }

    @Test
    void chineseWorstCaseStillReachesTheClient() {
        callWith("汉");
    }

    @Test
    void asciiWorstCaseStillReachesTheClient() {
        callWith("x");
    }

    @Test
    void fourByteCharactersWorstCaseStillReachesTheClient() {
        callWith("😀");
    }
}
