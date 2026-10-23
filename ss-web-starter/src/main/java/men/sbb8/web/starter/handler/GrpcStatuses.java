package men.sbb8.web.starter.handler;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.springframework.util.ClassUtils;

/**
 * 识别"下游 gRPC 服务不可达"。gRPC 是可选依赖：类路径上没有 grpc-api 时直接返回 false，
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

    static boolean isUnavailable(Throwable throwable) {
        return GRPC_PRESENT && Detector.isUnavailable(throwable);
    }

    private static final class Detector {

        static boolean isUnavailable(Throwable throwable) {
            Throwable current = throwable;
            for (int depth = 0; current != null && depth < 10; depth++, current = current.getCause()) {
                if (current instanceof StatusRuntimeException e && e.getStatus().getCode() == Status.Code.UNAVAILABLE) {
                    return true;
                }
            }
            return false;
        }
    }
}
