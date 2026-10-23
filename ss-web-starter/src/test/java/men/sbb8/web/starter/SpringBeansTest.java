package men.sbb8.web.starter;

import men.sbb8.web.starter.config.SsSpringBeansAutoConfiguration;
import men.sbb8.web.starter.utils.SpringBeans;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpringBeansTest {

    record Marker(String name) {
    }

    private ApplicationContextRunner runner(String name) {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SsSpringBeansAutoConfiguration.class))
                .withBean("marker", Marker.class, () -> new Marker(name));
    }

    @Test
    void getsBeansWhileRunningAndFailsClearlyAfterClose() {
        runner("a").run(context -> {
            assertThat(SpringBeans.getBean(Marker.class).name()).isEqualTo("a");
            assertThat(SpringBeans.getBean("marker", Marker.class)).isNotNull();
            assertThat(SpringBeans.getBeans(Marker.class)).hasSize(1);
            assertThat(SpringBeans.getEnvironment()).isSameAs(context.getEnvironment());
        });
        // 已关闭容器里的 Bean 取不到；同一个 JVM 里别的测试缓存的容器仍在运行时会回到它们，所以只断言取不到这个 Marker
        assertThatThrownBy(() -> SpringBeans.getBean(Marker.class)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void closingAnInnerContainerGoesBackToTheOneStillRunning() {
        runner("outer").run(outer -> {
            runner("inner").run(inner ->
                    assertThat(SpringBeans.getBean(Marker.class).name()).isEqualTo("inner"));
            assertThat(SpringBeans.getBean(Marker.class).name()).isEqualTo("outer");
        });
    }
}
