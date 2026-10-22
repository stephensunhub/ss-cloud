package men.sbb8.grpc.server;

import io.grpc.Status;

import java.util.Set;

/**
 * 属于"调用方的问题"的 gRPC 状态：参数不对、资源不存在、无权限等。
 * 这类错误的描述是开发者有意写给调用方看的，可以原样往上游传；其余状态多半是服务端内部或下游故障，不能暴露细节。
 *
 * @author Stephen
 */
final class CallerStatuses {

    private static final Set<Status.Code> CALLER_ERRORS = Set.of(
            Status.Code.INVALID_ARGUMENT, Status.Code.NOT_FOUND, Status.Code.ALREADY_EXISTS,
            Status.Code.PERMISSION_DENIED, Status.Code.UNAUTHENTICATED, Status.Code.FAILED_PRECONDITION,
            Status.Code.OUT_OF_RANGE);

    /**
     * 框架自己产生、不带业务信息的状态，原样保留无害
     */
    private static final Set<Status.Code> FRAMEWORK = Set.of(Status.Code.CANCELLED, Status.Code.UNIMPLEMENTED);

    private CallerStatuses() {
    }

    /**
     * 下游暂时不可用或超时：这类故障在下游宕机或抖动时会成批出现，日志里只需要一行，不需要每个调用一份堆栈
     */
    static boolean isTransient(Status.Code code) {
        return code == Status.Code.UNAVAILABLE || code == Status.Code.DEADLINE_EXCEEDED;
    }

    static boolean isCallerError(Status.Code code) {
        return CALLER_ERRORS.contains(code);
    }

    static boolean isSafeToExpose(Status.Code code) {
        return CALLER_ERRORS.contains(code) || FRAMEWORK.contains(code);
    }
}
