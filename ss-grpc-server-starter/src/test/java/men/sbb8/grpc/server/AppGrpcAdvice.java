package men.sbb8.grpc.server;

import io.grpc.Status;
import org.springframework.grpc.server.advice.GrpcAdvice;
import org.springframework.grpc.server.advice.GrpcExceptionHandler;

/**
 * 应用自己的 @GrpcAdvice：优先于 starter 的业务异常处理与兜底。
 * 注意类和方法必须是 public，否则 Spring gRPC 反射调用失败，会被悄悄吞成通用的 INTERNAL。
 */
@GrpcAdvice
public class AppGrpcAdvice {

    @GrpcExceptionHandler(UnsupportedOperationException.class)
    public Status handle(UnsupportedOperationException e) {
        return Status.FAILED_PRECONDITION.withDescription("advice: " + e.getMessage());
    }
}
