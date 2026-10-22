package men.sbb8.grpc.core.config;

import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

/**
 * GraalVM 原生镜像：把 starter 的默认配置文件打进镜像，否则 {@link SsGrpcEnvironmentPostProcessor} 运行期读不到它
 *
 * @author Stephen
 */
public class SsGrpcRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
        hints.resources().registerPattern(SsGrpcEnvironmentPostProcessor.DEFAULTS);
    }
}
