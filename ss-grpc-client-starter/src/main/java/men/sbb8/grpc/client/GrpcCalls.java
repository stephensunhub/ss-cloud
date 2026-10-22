package men.sbb8.grpc.client;

import io.grpc.StatusRuntimeException;
import men.sbb8.grpc.core.error.GrpcRemoteException;

import java.util.Iterator;
import java.util.function.Supplier;

/**
 * 调用下游 gRPC 的便捷封装：失败时把 {@link StatusRuntimeException} 转成带业务码的 {@link GrpcRemoteException}。
 * <pre>{@code
 * Reply reply = GrpcCalls.call(() -> stub.sayHello(request));
 * }</pre>
 * 服务端流式调用用 {@link #stream}：阻塞 stub 返回的迭代器是在迭代时才抛出失败的，不在 {@code call} 的范围内。
 *
 * @author Stephen
 */
public final class GrpcCalls {

    private GrpcCalls() {
    }

    public static <T> T call(Supplier<T> call) {
        try {
            return call.get();
        } catch (StatusRuntimeException e) {
            throw new GrpcRemoteException(e);
        }
    }

    /**
     * 服务端流式调用：返回的迭代器在 {@code hasNext} / {@code next} 里遇到失败时同样转成 {@link GrpcRemoteException}，
     * 已经收到的消息不受影响。
     * <pre>{@code
     * Iterator<Reply> replies = GrpcCalls.stream(() -> stub.listReplies(request));
     * }</pre>
     */
    public static <T> Iterator<T> stream(Supplier<Iterator<T>> call) {
        Iterator<T> delegate = call(call);
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return call(delegate::hasNext);
            }

            @Override
            public T next() {
                return call(delegate::next);
            }
        };
    }

    public static void run(Runnable call) {
        try {
            call.run();
        } catch (StatusRuntimeException e) {
            throw new GrpcRemoteException(e);
        }
    }
}
