package men.sbb8.grpc.starter.error;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import men.sbb8.grpc.starter.proto.ErrorDetail;
import org.jspecify.annotations.Nullable;

/**
 * 调用下游 gRPC 服务失败。下游返回了 {@code ErrorDetail} 时带有业务码和消息，否则只有 gRPC 状态。
 * 通过 {@link men.sbb8.grpc.starter.client.GrpcCalls} 调用 stub 时，{@link StatusRuntimeException} 会被转成它。
 *
 * @author Stephen
 */
public class GrpcRemoteException extends RuntimeException {

    private final Status status;
    private final @Nullable ErrorDetail detail;

    public GrpcRemoteException(StatusRuntimeException cause) {
        super("调用下游失败: " + cause.getStatus(), cause);
        this.status = cause.getStatus();
        this.detail = ErrorDetails.find(cause).orElse(null);
    }

    public Status getStatus() {
        return status;
    }

    public @Nullable ErrorDetail getDetail() {
        return detail;
    }

    public @Nullable String getCode() {
        return detail == null ? null : detail.getCode();
    }

    public @Nullable String getRemoteMsg() {
        return detail == null ? null : detail.getMsg();
    }
}
