package men.sbb8.web.starter.config;

import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

/**
 * GraalVM 原生镜像：starter 需要的反射提示
 *
 * @author Stephen
 */
public class SsWebRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
        // Netty 的 ReferenceCountUtil 静态初始化时反射查找 touch 方法，原生镜像里不注册就初始化失败，
        // Redisson（以及其他直接用 Netty 的库）连接 Redis 时会报 NoClassDefFoundError: Could not initialize class
        hints.reflection().registerTypeIfPresent(classLoader, "io.netty.util.ReferenceCountUtil",
                MemberCategory.INVOKE_DECLARED_METHODS);
    }
}
