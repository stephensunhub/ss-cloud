package men.sbb8.grpc.starter.error;

import io.grpc.Status;

/**
 * 业务码。gRPC 状态表达错误类别，code / msg 表达具体业务含义，一起返回给调用方。
 * 业务码通常用枚举实现，msg 里可以用 {@code {{name}}} 引用参数，见 {@link ErrorDetails#render}。
 *
 * @author Stephen
 */
public interface BusinessCode {

    /**
     * gRPC 状态，网关到 HTTP 的转换见
     * <a href="https://github.com/grpc/grpc/blob/master/doc/http-grpc-status-mapping.md">http-grpc-status-mapping</a>
     */
    Status status();

    String code();

    String msg();
}
