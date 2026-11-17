package men.sbb8.grpc.core.config;

import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

import java.util.concurrent.atomic.LongAdder;

/**
 * GraalVM 原生镜像：补上 grpc-java 缺少的反射提示
 *
 * @author Stephen
 */
public class SsGrpcRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
        // grpc-java 的计数器（io.grpc.internal.ReflectionLongAdderCounter）通过反射创建并调用 LongAdder，
        // Boot / Spring gRPC 没有为它注册提示，缺了会在 gRPC 服务端 / 客户端启动时失败
        hints.reflection().registerType(LongAdder.class,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS, MemberCategory.INVOKE_PUBLIC_METHODS);
    }
}
