package men.sbb8.web.starter;

import men.sbb8.web.starter.config.SsJacksonAutoConfiguration;
import men.sbb8.web.starter.json.JacksonUtils;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JacksonUtils 是进程级的静态入口：容器启动时用容器的 mapper，容器关闭时还原。
 * 在独立的容器里验证，不依赖其他测试的容器创建顺序。
 */
class JacksonUtilsBindingTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, SsJacksonAutoConfiguration.class));

    @Test
    void usesTheContainersMapperWhileRunningAndReleasesItOnClose() {
        JsonMapper[] seen = new JsonMapper[1];
        runner.run(context -> {
            seen[0] = context.getBean(JsonMapper.class);
            assertThat(JacksonUtils.mapper()).isSameAs(seen[0]);
        });
        // 容器已关闭：不能继续引用它的 mapper
        assertThat(JacksonUtils.mapper()).isNotSameAs(seen[0]).isNotNull();
    }

    @Test
    void closingAnInnerContainerGoesBackToTheOneStillRunning() {
        runner.run(outer -> {
            JsonMapper outerMapper = outer.getBean(JsonMapper.class);
            JsonMapper[] innerMapper = new JsonMapper[1];
            runner.run(inner -> {
                innerMapper[0] = inner.getBean(JsonMapper.class);
                assertThat(JacksonUtils.mapper()).isSameAs(innerMapper[0]);
            });
            assertThat(JacksonUtils.mapper()).isSameAs(outerMapper).isNotSameAs(innerMapper[0]);
        });
    }

    @Test
    void releasingOutOfOrderKeepsTheContainerStillRunning() {
        // 关闭顺序和启动顺序不一致：先启动的 a 先关闭，仍在运行的 b 不能丢
        JsonMapper a = JsonMapper.builder().build();
        JsonMapper b = JsonMapper.builder().build();
        JacksonUtils.init(a);
        JacksonUtils.init(b);
        try {
            JacksonUtils.release(a);
            assertThat(JacksonUtils.mapper()).isSameAs(b);
            JacksonUtils.release(b);
            assertThat(JacksonUtils.mapper()).isNotSameAs(a).isNotSameAs(b);
        } finally {
            JacksonUtils.release(a);
            JacksonUtils.release(b);
        }
    }
}
