package men.sbb8.grpc.client;

import io.grpc.StatusRuntimeException;
import men.sbb8.grpc.core.error.GrpcRemoteException;

import java.util.function.Supplier;

/**
 * 调用下游 gRPC 的便捷封装：失败时把 {@link StatusRuntimeException} 转成带业务码的 {@link GrpcRemoteException}。
 * <pre>{@code
 * Reply reply = GrpcCalls.call(() -> stub.sayHello(request));
 * }</pre>
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

    public static void run(Runnable call) {
        try {
            call.run();
        } catch (StatusRuntimeException e) {
            throw new GrpcRemoteException(e);
        }
    }
}
