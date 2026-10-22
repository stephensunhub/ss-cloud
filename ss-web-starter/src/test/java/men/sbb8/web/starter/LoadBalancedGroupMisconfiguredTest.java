package men.sbb8.web.starter;

import men.sbb8.web.starter.config.SsHttpClientAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.client.loadbalancer.DeferringLoadBalancerInterceptor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 配置了 load-balanced-groups 却没有引入 Spring Cloud：启动就失败并说明原因，不会悄悄把服务名当主机名去解析
 */
class LoadBalancedGroupMisconfiguredTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SsHttpClientAutoConfiguration.class))
            .withBean(tools.jackson.databind.json.JsonMapper.class, () -> tools.jackson.databind.json.JsonMapper.builder().build());

    @Test
    void failsFastWhenSpringCloudIsMissing() {
        runner.withClassLoader(new FilteredClassLoader(DeferringLoadBalancerInterceptor.class))
                .withPropertyValues("ss.web.client.load-balanced-groups=wallet")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure().getMessage() + rootMessage(context.getStartupFailure()))
                            .contains("load-balanced-groups").contains("wallet").contains("spring-cloud-starter-loadbalancer");
                });
    }

    @Test
    void nothingHappensWhenNoGroupsAreConfigured() {
        runner.withClassLoader(new FilteredClassLoader(DeferringLoadBalancerInterceptor.class))
                .run(context -> assertThat(context).hasNotFailed());
    }

    private static String rootMessage(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return String.valueOf(root.getMessage());
    }
}
