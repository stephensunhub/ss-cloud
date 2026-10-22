package men.sbb8.grpc.starter.server;

import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import men.sbb8.grpc.starter.error.CommonCode;
import men.sbb8.grpc.starter.error.ErrorDetails;
import org.springframework.grpc.server.exception.GrpcExceptionHandler;

import java.util.Map;

/**
 * 兜底：显式抛出的 {@link StatusException} / {@link StatusRuntimeException} 原样返回；
 * 其余未知异常统一为 INTERNAL + SYSTEM_ERROR，不向调用方暴露异常详情。
 * 优先级最低，应用自己的处理先生效。
 *
 * @author Stephen
 */
@Slf4j
public class FallbackExceptionHandler implements GrpcExceptionHandler {

    @Override
    public StatusException handleException(Throwable exception) {
        if (exception instanceof StatusException e) {
            return e;
        }
        if (exception instanceof StatusRuntimeException e) {
            return new StatusException(e.getStatus(), e.getTrailers());
        }
        log.error("未知异常", exception);
        return ErrorDetails.toStatusException(CommonCode.SYSTEM_ERROR, Map.of());
    }
}
