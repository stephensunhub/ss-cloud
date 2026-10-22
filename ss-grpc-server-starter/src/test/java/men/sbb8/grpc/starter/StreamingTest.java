package men.sbb8.grpc.starter;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import men.sbb8.grpc.core.error.ErrorDetails;
import men.sbb8.grpc.starter.test.GreeterGrpc;
import men.sbb8.grpc.starter.test.HelloReply;
import men.sbb8.grpc.starter.test.HelloRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.grpc.test.autoconfigure.AutoConfigureTestGrpcTransport;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.grpc.client.ImportGrpcClients;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 流式调用中途出错：已经发出的消息照常收到，随后收到带 ErrorDetail 的正确状态，服务端指标记为真实状态
 */
@SpringBootTest
@AutoConfigureTestGrpcTransport
@ImportGrpcClients(types = GreeterGrpc.GreeterBlockingStub.class)
class StreamingTest {

    @Autowired
    GreeterGrpc.GreeterBlockingStub stub;

    @Autowired
    MeterRegistry registry;

    private long serverStreamCalls(String status) {
        return registry.find("grpc.server").tag("grpc.status_code", status).tag("rpc.method", "Stream")
                .timers().stream().mapToLong(Timer::count).sum();
    }

    @Test
    void streamCompletesNormally() {
        List<String> received = new ArrayList<>();
        stub.stream(HelloRequest.newBuilder().setName("ok").build()).forEachRemaining(r -> received.add(r.getMessage()));
        assertEquals(List.of("part-1", "part-2"), received);
    }

    @Test
    void businessExceptionMidStreamKeepsEarlierMessagesAndCarriesDetail() {
        long before = serverStreamCalls("ALREADY_EXISTS");
        Iterator<HelloReply> replies = stub.stream(HelloRequest.newBuilder().setName("stream-business").build());
        assertEquals("part-1", replies.next().getMessage());
        assertEquals("part-2", replies.next().getMessage());
        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, replies::hasNext);
        assertEquals(Status.Code.ALREADY_EXISTS, e.getStatus().getCode());
        assertEquals("名称 streamed 已被占用", ErrorDetails.find(e).orElseThrow().getMsg());
        assertEquals(before + 1, serverStreamCalls("ALREADY_EXISTS"));
    }

    @Test
    void unknownExceptionMidStreamDoesNotLeak() {
        Iterator<HelloReply> replies = stub.stream(HelloRequest.newBuilder().setName("stream-boom").build());
        replies.next();
        replies.next();
        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, replies::hasNext);
        assertEquals(Status.Code.INTERNAL, e.getStatus().getCode());
        assertEquals("SYSTEM_ERROR", ErrorDetails.find(e).orElseThrow().getCode());
    }
}
