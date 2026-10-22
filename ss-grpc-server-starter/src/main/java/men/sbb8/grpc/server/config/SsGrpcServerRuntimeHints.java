package men.sbb8.grpc.server.config;

import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

/**
 * GraalVM 原生镜像：把服务端默认配置文件打进镜像，否则 {@link SsGrpcServerEnvironmentPostProcessor} 运行期读不到它
 *
 * @author Stephen
 */
public class SsGrpcServerRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
        hints.resources().registerPattern(SsGrpcServerEnvironmentPostProcessor.DEFAULTS);
    }
}
