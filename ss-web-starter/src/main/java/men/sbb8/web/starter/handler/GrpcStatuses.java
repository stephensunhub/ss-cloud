package men.sbb8.web.starter.handler;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.springframework.util.ClassUtils;

/**
 * 识别"下游 gRPC 服务不可达（UNAVAILABLE）"和"下游 gRPC 调用超时（DEADLINE_EXCEEDED）"。gRPC 是可选依赖：类路径上没有 grpc-api 时直接返回 false，
 * 嵌套类只在确认 gRPC 存在后才会被加载，所以不引入 gRPC 的应用不会因为缺类而出错。
 * 沿 cause 链查找，裸的 {@code StatusRuntimeException} 和被 ss-grpc-client-starter 的
 * {@code GrpcRemoteException} 包装过的都能识别。
 *
 * @author Stephen
 */
final class GrpcStatuses {

    private static final boolean GRPC_PRESENT =
            ClassUtils.isPresent("io.grpc.StatusRuntimeException", GrpcStatuses.class.getClassLoader());

    private GrpcStatuses() {
    }

    /**
     * 下游 gRPC 调用的失败类型，不依赖 gRPC 的类，所以调用方在没有 gRPC 时也能引用
     */
    enum Failure {
        /** 连不上：宕机、地址解析失败、连接中断 */
        UNAVAILABLE,
        /** 超过 deadline */
        TIMEOUT
    }

    /**
     * 沿 cause 链从外到内找第一个 UNAVAILABLE / DEADLINE_EXCEEDED 的 gRPC 状态；都没有、或类路径上没有 gRPC 时返回 {@code null}
     */
    static Failure find(Throwable throwable) {
        return GRPC_PRESENT ? Detector.find(throwable) : null;
    }

    private static final class Detector {

        static Failure find(Throwable throwable) {
            Throwable current = throwable;
            for (int depth = 0; current != null && depth < 10; depth++, current = current.getCause()) {
                if (current instanceof StatusRuntimeException e) {
                    Status.Code code = e.getStatus().getCode();
                    if (code == Status.Code.UNAVAILABLE) {
                        return Failure.UNAVAILABLE;
                    }
                    if (code == Status.Code.DEADLINE_EXCEEDED) {
                        return Failure.TIMEOUT;
                    }
                }
            }
            return null;
        }
    }
}
