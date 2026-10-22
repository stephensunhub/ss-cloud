package men.sbb8.grpc.client.discovery;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiscoveryRequirementsTest {

    private static final ClassLoader LOADER = DiscoveryRequirementsTest.class.getClassLoader();
    private static final String PRESENT = "java.lang.String";
    private static final String ABSENT = "no.such.Clazz";

    @Test
    void failsWhenConsulIsPresentButJacksonIsNot() {
        assertThatThrownBy(() -> DiscoveryRequirements.verify(PRESENT, ABSENT, LOADER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tools.jackson.core:jackson-databind")
                .hasMessageContaining("Spring Cloud Consul");
    }

    @Test
    void passesWhenJacksonIsPresent() {
        assertThatCode(() -> DiscoveryRequirements.verify(PRESENT, PRESENT, LOADER)).doesNotThrowAnyException();
    }

    @Test
    void passesWithoutConsulWhateverJackson() {
        assertThatCode(() -> DiscoveryRequirements.verify(ABSENT, ABSENT, LOADER)).doesNotThrowAnyException();
        assertThatCode(() -> DiscoveryRequirements.verify(ABSENT, PRESENT, LOADER)).doesNotThrowAnyException();
    }

    @Test
    void thisModulesOwnClasspathPasses() {
        // 本模块的测试类路径没有 Consul，真实检查必须放行
        assertThat(DiscoveryRequirements.CONSUL_DISCOVERY_CLIENT).isEqualTo("org.springframework.cloud.consul.discovery.ConsulDiscoveryClient");
        assertThatCode(() -> DiscoveryRequirements.verify(LOADER)).doesNotThrowAnyException();
    }
}
