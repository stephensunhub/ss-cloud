package men.sbb8.web.starter;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 默认（虚拟线程）模式：@Async 里能拿到调用方的 traceId；停机等待等默认值生效
 */
@SpringBootTest
class AsyncDefaultsTest {

    @TestConfiguration
    @EnableAsync
    static class Config {
        @Bean
        AsyncWork asyncWork() {
            return new AsyncWork();
        }
    }

    static class AsyncWork {
        @Async
        CompletableFuture<String> traceIdInsideAsync() {
            return CompletableFuture.completedFuture(MDC.get("traceId"));
        }
    }

    @Autowired
    AsyncWork work;

    @Autowired
    ObservationRegistry observations;

    @Autowired
    Tracer tracer;

    @Autowired
    Environment env;

    @Test
    void asyncTasksInheritTheCallersTraceId() throws Exception {
        // 真实请求是在 Observation 里处理的（HTTP 服务端观测），Boot 传递的就是这个上下文
        Observation observation = Observation.start("caller", observations);
        try (Observation.Scope ignored = observation.openScope()) {
            String expected = tracer.currentSpan().context().traceId();
            String inside = work.traceIdInsideAsync().get(5, TimeUnit.SECONDS);
            assertEquals(expected, inside);
        } finally {
            observation.stop();
        }
    }

    @Test
    void shutdownWaitsForRunningAsyncTasks() {
        assertEquals("true", env.getProperty("spring.task.execution.shutdown.await-termination"));
        assertEquals("true", env.getProperty("spring.task.scheduling.shutdown.await-termination"));
    }
}
