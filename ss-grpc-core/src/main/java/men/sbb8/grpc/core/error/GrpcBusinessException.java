package men.sbb8.grpc.core.error;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 业务异常。在 gRPC 服务实现里抛出，由 starter 转成对应的 gRPC 状态和 {@code ErrorDetail}。
 * 参数会原样发给调用方，不要放敏感值；参数数量和长度有上限，见 {@link ErrorDetails#bound}。
 *
 * @author Stephen
 */
public class GrpcBusinessException extends RuntimeException {

    private final BusinessCode code;
    private final Map<String, ?> params;

    public GrpcBusinessException(BusinessCode code) {
        this(code, Map.of());
    }

    public GrpcBusinessException(BusinessCode code, Map<String, ?> params) {
        super(ErrorDetails.render(code.msg(), ErrorDetails.bound(params)));
        this.code = code;
        // 复制一份：调用方之后改动自己的 Map，不应影响异常的内容
        this.params = Collections.unmodifiableMap(new LinkedHashMap<>(params));
    }

    public BusinessCode getCode() {
        return code;
    }

    public Map<String, ?> getParams() {
        return params;
    }
}
