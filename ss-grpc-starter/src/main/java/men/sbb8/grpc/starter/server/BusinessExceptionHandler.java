package men.sbb8.grpc.starter.server;

import io.grpc.Status;
import io.grpc.StatusException;
import lombok.extern.slf4j.Slf4j;
import men.sbb8.grpc.starter.error.CommonCode;
import men.sbb8.grpc.starter.error.ErrorDetails;
import men.sbb8.grpc.starter.error.GrpcBusinessException;
import men.sbb8.grpc.starter.error.GrpcRemoteException;
import men.sbb8.grpc.starter.proto.ErrorDetail;
import org.jspecify.annotations.Nullable;
import org.springframework.grpc.server.exception.GrpcExceptionHandler;

import java.util.Map;
import java.util.Set;

/**
 * 业务异常转 gRPC 状态：
 * <ul>
 *     <li>{@link GrpcBusinessException}：使用业务码自带的状态，并返回 {@code ErrorDetail}</li>
 *     <li>{@link GrpcRemoteException}：下游返回了业务错误且属于调用方错误类（参数、权限、不存在等）时原样透传；
 *     其余（下游内部错误、不可用、超时、无详情）统一为 UNAVAILABLE，不暴露下游细节</li>
 * </ul>
 *
 * @author Stephen
 */
@Slf4j
public class BusinessExceptionHandler implements GrpcExceptionHandler {

    private static final Set<Status.Code> PASS_THROUGH = Set.of(
            Status.Code.INVALID_ARGUMENT, Status.Code.NOT_FOUND, Status.Code.ALREADY_EXISTS,
            Status.Code.PERMISSION_DENIED, Status.Code.UNAUTHENTICATED, Status.Code.FAILED_PRECONDITION,
            Status.Code.OUT_OF_RANGE);

    @Override
    public @Nullable StatusException handleException(Throwable exception) {
        if (exception instanceof GrpcBusinessException e) {
            log.warn("业务异常: code={}, msg={}", e.getCode().code(), e.getMessage());
            return ErrorDetails.toStatusException(e.getCode(), e.getParams());
        }
        if (exception instanceof GrpcRemoteException e) {
            ErrorDetail detail = e.getDetail();
            if (detail != null && PASS_THROUGH.contains(e.getStatus().getCode())) {
                log.warn("下游业务错误: status={}, code={}, msg={}", e.getStatus().getCode(), detail.getCode(), detail.getMsg());
                return e.getStatus().withDescription(detail.getMsg()).asException(ErrorDetails.trailers(detail));
            }
            log.error("下游调用失败: {}", e.getMessage(), e);
            return ErrorDetails.toStatusException(CommonCode.DOWNSTREAM_ERROR, Map.of());
        }
        return null;
    }
}
