package men.sbb8.grpc.core;

import men.sbb8.grpc.core.config.SsGrpcRuntimeHints;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;
import org.springframework.beans.factory.aot.AotServices;

import static org.assertj.core.api.Assertions.assertThat;

class SsGrpcCoreRuntimeHintsTest {

    @Test
    void defaultsFileIsRegisteredAsResourceHint() {
        RuntimeHints hints = new RuntimeHints();
        new SsGrpcRuntimeHints().registerHints(hints, getClass().getClassLoader());

        assertThat(RuntimeHintsPredicates.resource().forResource("META-INF/ss-grpc-defaults.yaml")).accepts(hints);
    }

    @Test
    void grpcJavaLongAdderReflectionIsRegistered() {
        RuntimeHints hints = new RuntimeHints();
        new SsGrpcRuntimeHints().registerHints(hints, getClass().getClassLoader());

        assertThat(RuntimeHintsPredicates.reflection().onType(java.util.concurrent.atomic.LongAdder.class)
                .withMemberCategories(org.springframework.aot.hint.MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                        org.springframework.aot.hint.MemberCategory.INVOKE_PUBLIC_METHODS)).accepts(hints);
    }

    @Test
    void registrarIsDiscoverableThroughAotFactories() {
        // 与 Spring AOT 处理时一致的发现方式：META-INF/spring/aot.factories（不是 spring.factories）
        var registrars = AotServices.factories(getClass().getClassLoader())
                .load(RuntimeHintsRegistrar.class).stream().toList();

        assertThat(registrars).anyMatch(SsGrpcRuntimeHints.class::isInstance);
    }
}
