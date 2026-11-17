package men.sbb8.web.starter;

import men.sbb8.web.starter.config.SsWebRuntimeHints;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;
import org.springframework.beans.factory.aot.AotServices;

import static org.assertj.core.api.Assertions.assertThat;

class SsWebRuntimeHintsTest {

    @Test
    void defaultsFileIsRegisteredAsResourceHint() {
        RuntimeHints hints = new RuntimeHints();
        new SsWebRuntimeHints().registerHints(hints, getClass().getClassLoader());

        assertThat(RuntimeHintsPredicates.resource().forResource("META-INF/ss-web-defaults.yaml")).accepts(hints);
    }

    @Test
    void nettyReferenceCountUtilIsRegisteredForReflection() {
        RuntimeHints hints = new RuntimeHints();
        new SsWebRuntimeHints().registerHints(hints, getClass().getClassLoader());

        assertThat(RuntimeHintsPredicates.reflection().onType(io.netty.util.ReferenceCountUtil.class)
                .withMemberCategory(org.springframework.aot.hint.MemberCategory.INVOKE_DECLARED_METHODS)).accepts(hints);
    }

    @Test
    void registrarIsDiscoverableThroughAotFactories() {
        // 与 Spring AOT 处理时一致的发现方式：META-INF/spring/aot.factories（不是 spring.factories）
        var registrars = AotServices.factories(getClass().getClassLoader())
                .load(RuntimeHintsRegistrar.class).stream().toList();

        assertThat(registrars).anyMatch(SsWebRuntimeHints.class::isInstance);
    }
}
