package men.sbb8.web.starter.error;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatusCode;

/**
 * 调用下游 HTTP 服务返回非 2xx 时抛出。
 * 下游返回了 {@code Resp} 结构时带有 code 和 remoteMsg，否则只有状态码和原始响应体
 *
 * @author Stephen
 */
public class RemoteCallException extends RuntimeException {

    @Getter
    private final HttpStatusCode status;
    private final @Nullable String code;
    private final @Nullable String remoteMsg;
    @Getter
    private final String body;

    public RemoteCallException(String message,
                               HttpStatusCode status,
                               @Nullable String code,
                               @Nullable String remoteMsg,
                               String body) {
        super(message);
        this.status = status;
        this.code = code;
        this.remoteMsg = remoteMsg;
        this.body = body;
    }

    public @Nullable String getCode() {
        return code;
    }

    public @Nullable String getRemoteMsg() {
        return remoteMsg;
    }

}
