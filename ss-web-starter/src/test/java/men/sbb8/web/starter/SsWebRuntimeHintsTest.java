package men.sbb8.web.starter;

import men.sbb8.web.starter.config.SsWebRuntimeHints;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;
import org.springframework.core.io.support.SpringFactoriesLoader;

import static org.assertj.core.api.Assertions.assertThat;

class SsWebRuntimeHintsTest {

    @Test
    void defaultsFileIsRegisteredAsResourceHint() {
        RuntimeHints hints = new RuntimeHints();
        new SsWebRuntimeHints().registerHints(hints, getClass().getClassLoader());

        assertThat(RuntimeHintsPredicates.resource().forResource("META-INF/ss-web-defaults.yaml")).accepts(hints);
    }

    @Test
    void registrarIsDiscoverableThroughSpringFactories() {
        var registrars = SpringFactoriesLoader.forDefaultResourceLocation(getClass().getClassLoader())
                .load(RuntimeHintsRegistrar.class);

        assertThat(registrars).anyMatch(SsWebRuntimeHints.class::isInstance);
    }
}
