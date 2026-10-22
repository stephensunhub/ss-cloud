package men.sbb8.grpc.core;

import men.sbb8.grpc.core.config.SsGrpcRuntimeHints;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;
import org.springframework.core.io.support.SpringFactoriesLoader;

import static org.assertj.core.api.Assertions.assertThat;

class SsGrpcCoreRuntimeHintsTest {

    @Test
    void defaultsFileIsRegisteredAsResourceHint() {
        RuntimeHints hints = new RuntimeHints();
        new SsGrpcRuntimeHints().registerHints(hints, getClass().getClassLoader());

        assertThat(RuntimeHintsPredicates.resource().forResource("META-INF/ss-grpc-defaults.yaml")).accepts(hints);
    }

    @Test
    void registrarIsDiscoverableThroughSpringFactories() {
        var registrars = SpringFactoriesLoader.forDefaultResourceLocation(getClass().getClassLoader())
                .load(RuntimeHintsRegistrar.class);

        assertThat(registrars).anyMatch(SsGrpcRuntimeHints.class::isInstance);
    }
}
