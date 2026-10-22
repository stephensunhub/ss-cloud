package men.sbb8.grpc.starter.error;

import io.grpc.Status;

/**
 * 通用业务码，禁止添加具有业务含义的枚举
 *
 * @author Stephen
 */
public enum CommonCode implements BusinessCode {

    PARAMS_ERROR(Status.INVALID_ARGUMENT, "参数错误"),
    UNAUTHENTICATED(Status.UNAUTHENTICATED, "未登录或身份无效"),
    PERMISSION_DENIED(Status.PERMISSION_DENIED, "无权限"),
    SYSTEM_ERROR(Status.INTERNAL, "系统异常"),
    DOWNSTREAM_ERROR(Status.UNAVAILABLE, "下游服务异常");

    private final Status status;
    private final String msg;

    CommonCode(Status status, String msg) {
        this.status = status;
        this.msg = msg;
    }

    @Override
    public Status status() {
        return status;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public String msg() {
        return msg;
    }
}
