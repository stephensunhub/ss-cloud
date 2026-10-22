package men.sbb8.grpc.server;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.micrometer.core.instrument.MeterRegistry;
import io.grpc.stub.StreamObserver;
import io.micrometer.core.instrument.Timer;
import men.sbb8.grpc.client.GrpcCalls;
import men.sbb8.grpc.core.error.ErrorDetails;
import men.sbb8.grpc.core.error.GrpcRemoteException;
import men.sbb8.grpc.server.test.GreeterGrpc;
import men.sbb8.grpc.server.test.HelloReply;
import men.sbb8.grpc.server.test.HelloRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.grpc.test.autoconfigure.AutoConfigureTestGrpcTransport;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.grpc.client.ImportGrpcClients;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 流式调用中途出错：已经发出的消息照常收到，随后收到带 ErrorDetail 的正确状态，服务端指标记为真实状态
 */
@SpringBootTest
@AutoConfigureTestGrpcTransport
@ImportGrpcClients(types = {GreeterGrpc.GreeterBlockingStub.class, GreeterGrpc.GreeterStub.class})
class StreamingTest {

    @Autowired
    GreeterGrpc.GreeterBlockingStub stub;

    @Autowired
    GreeterGrpc.GreeterStub asyncStub;

    @Autowired
    MeterRegistry registry;

    /** 收集响应观察者收到的消息和结局，供测试线程等待 */
    private static final class Collector implements StreamObserver<HelloReply> {
        final List<String> messages = new CopyOnWriteArrayList<>();
        final CompletableFuture<Throwable> outcome = new CompletableFuture<>();

        @Override
        public void onNext(HelloReply value) {
            messages.add(value.getMessage());
        }

        @Override
        public void onError(Throwable t) {
            outcome.complete(t);
        }

        @Override
        public void onCompleted() {
            outcome.complete(null);
        }

        Throwable await() throws Exception {
            return outcome.get(5, TimeUnit.SECONDS);
        }
    }

    private static HelloRequest named(String name) {
        return HelloRequest.newBuilder().setName(name).build();
    }

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
    void grpcCallsStreamTurnsMidStreamFailureIntoRemoteException() {
        Iterator<HelloReply> replies = GrpcCalls.stream(
                () -> stub.stream(HelloRequest.newBuilder().setName("stream-business").build()));
        assertEquals("part-1", replies.next().getMessage());
        assertEquals("part-2", replies.next().getMessage());
        GrpcRemoteException e = assertThrows(GrpcRemoteException.class, replies::hasNext);
        assertEquals("NAME_TAKEN", e.getCode());
        assertEquals(Status.Code.ALREADY_EXISTS, e.getStatus().getCode());
    }

    @Test
    void grpcCallsStreamPassesNormalStreamsThrough() {
        List<String> received = new ArrayList<>();
        GrpcCalls.stream(() -> stub.stream(HelloRequest.newBuilder().setName("ok").build()))
                .forEachRemaining(r -> received.add(r.getMessage()));
        assertEquals(List.of("part-1", "part-2"), received);
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

    @Test
    void clientStreamingCompletesNormally() throws Exception {
        Collector replies = new Collector();
        StreamObserver<HelloRequest> requests = asyncStub.collect(GrpcCalls.observer(replies));
        requests.onNext(named("a"));
        requests.onNext(named("b"));
        requests.onCompleted();
        assertNull(replies.await());
        assertEquals(List.of("got 2"), replies.messages);
    }

    @Test
    void clientStreamingFailureBecomesRemoteExceptionWithDetail() throws Exception {
        Collector replies = new Collector();
        StreamObserver<HelloRequest> requests = asyncStub.collect(GrpcCalls.observer(replies));
        requests.onNext(named("a"));
        requests.onNext(named("fail"));
        GrpcRemoteException e = assertInstanceOf(GrpcRemoteException.class, replies.await());
        assertEquals("NAME_TAKEN", e.getCode());
        assertEquals("名称 collected 已被占用", e.getRemoteMsg());
        assertEquals(Status.Code.ALREADY_EXISTS, e.getStatus().getCode());
    }

    @Test
    void bidirectionalStreamKeepsEarlierRepliesAndFailsWithRemoteException() throws Exception {
        Collector replies = new Collector();
        StreamObserver<HelloRequest> requests = asyncStub.chat(GrpcCalls.observer(replies));
        requests.onNext(named("a"));
        requests.onNext(named("b"));
        requests.onNext(named("fail"));
        GrpcRemoteException e = assertInstanceOf(GrpcRemoteException.class, replies.await());
        assertEquals("NAME_TAKEN", e.getCode());
        assertEquals(List.of("echo a", "echo b"), replies.messages);
    }

    @Test
    void bidirectionalStreamHidesInternalDetailsOfUnknownExceptions() throws Exception {
        Collector replies = new Collector();
        StreamObserver<HelloRequest> requests = asyncStub.chat(GrpcCalls.observer(replies));
        requests.onNext(named("boom"));
        GrpcRemoteException e = assertInstanceOf(GrpcRemoteException.class, replies.await());
        assertEquals("SYSTEM_ERROR", e.getCode());
        assertEquals(Status.Code.INTERNAL, e.getStatus().getCode());
        assertEquals(false, String.valueOf(e.getRemoteMsg()).contains("secret"));
    }

    @Test
    void observerPassesOtherFailuresThroughUnchanged() throws Exception {
        Collector replies = new Collector();
        IllegalStateException failure = new IllegalStateException("local");
        GrpcCalls.observer(replies).onError(failure);
        assertEquals(failure, replies.await());
    }
}
