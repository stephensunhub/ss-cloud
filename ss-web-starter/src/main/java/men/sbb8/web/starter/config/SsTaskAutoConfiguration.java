package men.sbb8.web.starter.config;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.task.ThreadPoolTaskExecutorCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.RejectedExecutionException;

/**
 * 异步任务线程池的补充。线程池本身的参数、MDC / traceId 传递、停机等待都用 Boot 的
 * {@code spring.task.*} 配置（默认值见 application.yaml），这里只补 Boot 没有的一项：
 * 线程池（关闭虚拟线程时 Boot 创建的 {@link ThreadPoolTaskExecutor}）满了拒绝任务时，记一条错误日志并计数
 * {@code executor.rejected}（标签 {@code executor} 为线程名前缀），行为仍和默认的拒绝策略一样抛出异常，
 * 由调用方（HTTP 层、{@code @Async} 框架）决定降级，不让请求线程替线程池干活。
 * 虚拟线程模式下没有队列，只有设置了 {@code spring.task.execution.simple.concurrency-limit} 且
 * {@code reject-tasks-when-limit-reached=true} 时才会拒绝，不经过这里。
 *
 * @author Stephen
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(ThreadPoolTaskExecutorCustomizer.class)
public class SsTaskAutoConfiguration {

    @Bean
    ThreadPoolTaskExecutorCustomizer ssRejectedTaskCustomizer(ObjectProvider<MeterRegistry> registry) {
        return executor -> executor.setRejectedExecutionHandler((task, pool) -> {
            String name = executor.getThreadNamePrefix();
            MeterRegistry meters = registry.getIfAvailable();
            if (meters != null) {
                meters.counter("executor.rejected", "executor", name).increment();
            }
            log.error("线程池拒绝任务: executor={}, active={}, pool={}/{}, queue={}", name, pool.getActiveCount(),
                    pool.getPoolSize(), pool.getMaximumPoolSize(), pool.getQueue().size());
            throw new RejectedExecutionException("线程池已满: " + name);
        });
    }
}
