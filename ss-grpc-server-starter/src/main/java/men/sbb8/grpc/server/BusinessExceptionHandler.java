package men.sbb8.grpc.server;

import io.grpc.StatusException;
import lombok.extern.slf4j.Slf4j;
import men.sbb8.grpc.core.error.CommonCode;
import men.sbb8.grpc.core.error.ErrorDetails;
import men.sbb8.grpc.core.error.GrpcBusinessException;
import men.sbb8.grpc.core.error.GrpcRemoteException;
import men.sbb8.grpc.core.proto.ErrorDetail;
import org.jspecify.annotations.Nullable;
import org.springframework.grpc.server.exception.GrpcExceptionHandler;

import java.util.Map;

/**
 * 业务异常转 gRPC 状态：
 * <ul>
 *     <li>{@link GrpcBusinessException}：使用业务码自带的状态，并返回 {@code ErrorDetail}</li>
 *     <li>{@link GrpcRemoteException}：下游返回了业务错误且属于调用方错误类（参数、权限、不存在等）时原样透传；
 *     其余（下游内部错误、不可用、超时、无详情）统一为 UNAVAILABLE，不暴露下游细节</li>
 * </ul>
 * 业务异常被 CompletionException 等包装时，沿 cause 链查找。
 *
 * @author Stephen
 */
@Slf4j
public class BusinessExceptionHandler implements GrpcExceptionHandler {

    @Override
    public @Nullable StatusException handleException(Throwable exception) {
        GrpcBusinessException business = CauseChain.find(exception, GrpcBusinessException.class);
        if (business != null) {
            log.warn("业务异常: code={}, msg={}", business.getCode().code(), business.getMessage());
            return ErrorDetails.toStatusException(business.getCode(), business.getParams());
        }
        GrpcRemoteException remote = CauseChain.find(exception, GrpcRemoteException.class);
        if (remote != null) {
            ErrorDetail detail = remote.getDetail();
            if (detail != null && CallerStatuses.isCallerError(remote.getStatus().getCode())) {
                log.warn("下游业务错误: status={}, code={}, msg={}", remote.getStatus().getCode(), detail.getCode(), detail.getMsg());
                return remote.getStatus().withDescription(detail.getMsg()).asException(ErrorDetails.trailers(detail));
            }
            if (CallerStatuses.isTransient(remote.getStatus().getCode())) {
                log.warn("下游暂时不可用: {}", remote.getMessage());
            } else {
                log.error("下游调用失败: {}", remote.getMessage(), remote);
            }
            return ErrorDetails.toStatusException(CommonCode.DOWNSTREAM_ERROR, Map.of());
        }
        return null;
    }
}
