package men.sbb8.grpc.server;

import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import men.sbb8.grpc.core.error.BusinessCode;
import men.sbb8.grpc.core.error.CommonCode;
import men.sbb8.grpc.core.error.ErrorDetails;
import org.jspecify.annotations.Nullable;
import org.springframework.grpc.server.exception.GrpcExceptionHandler;

import java.util.Map;

/**
 * 兜底，优先级最低，应用自己的处理先生效：
 * <ul>
 *     <li>显式抛出的 {@link StatusException} / {@link StatusRuntimeException}：带 {@code ErrorDetail} 的、
 *     或属于调用方错误类（参数、不存在、权限等）的原样返回；其余（INTERNAL、UNAVAILABLE 等，多半来自下游或内部故障）
 *     保留状态码，但丢弃描述和 trailers，换成通用的 {@code ErrorDetail}，避免把内部细节传给调用方</li>
 *     <li>其余未知异常：INTERNAL + SYSTEM_ERROR，不暴露异常详情</li>
 * </ul>
 *
 * @author Stephen
 */
@Slf4j
public class FallbackExceptionHandler implements GrpcExceptionHandler {

    @Override
    public StatusException handleException(Throwable exception) {
        StatusException status = findStatus(exception);
        if (status != null) {
            return sanitize(status);
        }
        log.error("未知异常", exception);
        return ErrorDetails.toStatusException(CommonCode.SYSTEM_ERROR, Map.of());
    }

    private static @Nullable StatusException findStatus(Throwable exception) {
        StatusException checked = CauseChain.find(exception, StatusException.class);
        if (checked != null) {
            return checked;
        }
        StatusRuntimeException unchecked = CauseChain.find(exception, StatusRuntimeException.class);
        return unchecked == null ? null : new StatusException(unchecked.getStatus(), unchecked.getTrailers());
    }

    private static StatusException sanitize(StatusException e) {
        Status.Code code = e.getStatus().getCode();
        if (ErrorDetails.find(e).isPresent() || CallerStatuses.isSafeToExpose(code)) {
            return e;
        }
        log.error("对外隐藏的 gRPC 错误: {}", e.getStatus(), e);
        BusinessCode generic = code == Status.Code.UNAVAILABLE || code == Status.Code.DEADLINE_EXCEEDED
                ? CommonCode.DOWNSTREAM_ERROR : CommonCode.SYSTEM_ERROR;
        return code.toStatus().withDescription(generic.msg())
                .asException(ErrorDetails.trailers(ErrorDetails.toDetail(generic, Map.of())));
    }
}
