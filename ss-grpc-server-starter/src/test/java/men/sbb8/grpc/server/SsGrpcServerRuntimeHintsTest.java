package men.sbb8.grpc.server;

import men.sbb8.grpc.server.config.SsGrpcServerRuntimeHints;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;
import org.springframework.beans.factory.aot.AotServices;

import static org.assertj.core.api.Assertions.assertThat;

class SsGrpcServerRuntimeHintsTest {

    @Test
    void defaultsFileIsRegisteredAsResourceHint() {
        RuntimeHints hints = new RuntimeHints();
        new SsGrpcServerRuntimeHints().registerHints(hints, getClass().getClassLoader());

        assertThat(RuntimeHintsPredicates.resource().forResource("META-INF/ss-grpc-server-defaults.yaml")).accepts(hints);
    }

    @Test
    void registrarIsDiscoverableThroughAotFactories() {
        // 与 Spring AOT 处理时一致的发现方式：META-INF/spring/aot.factories（不是 spring.factories）
        var registrars = AotServices.factories(getClass().getClassLoader())
                .load(RuntimeHintsRegistrar.class).stream().toList();

        assertThat(registrars).anyMatch(SsGrpcServerRuntimeHints.class::isInstance);
    }
}
