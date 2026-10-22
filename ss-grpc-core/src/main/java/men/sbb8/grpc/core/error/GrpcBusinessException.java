package men.sbb8.grpc.core.error;

import java.util.Map;

/**
 * 业务异常。在 gRPC 服务实现里抛出，由 starter 转成对应的 gRPC 状态和 {@code ErrorDetail}。
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
        super(ErrorDetails.render(code.msg(), params));
        this.code = code;
        this.params = params;
    }

    public BusinessCode getCode() {
        return code;
    }

    public Map<String, ?> getParams() {
        return params;
    }
}
