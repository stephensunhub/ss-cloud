package men.sbb8.web.starter;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.TaskRejectedException;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 关闭虚拟线程时用线程池：满了以后拒绝任务（抛出异常，不让调用线程代跑），并计数
 */
@SpringBootTest(properties = {
        "spring.threads.virtual.enabled=false",
        "spring.task.execution.pool.core-size=1",
        "spring.task.execution.pool.max-size=1",
        "spring.task.execution.pool.queue-capacity=1",
        "spring.task.execution.thread-name-prefix=reject-test-"
})
class RejectedTaskTest {

    @Autowired
    AsyncTaskExecutor applicationTaskExecutor;

    @Autowired
    MeterRegistry registry;

    @Test
    void fullPoolRejectsInsteadOfRunningOnTheCaller() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(1);
        try {
            applicationTaskExecutor.execute(() -> {
                started.countDown();
                await(release);
            });
            started.await(5, TimeUnit.SECONDS);
            applicationTaskExecutor.execute(() -> await(release));

            assertThrows(TaskRejectedException.class, () -> applicationTaskExecutor.execute(() -> { }));
            assertEquals(1.0, registry.counter("executor.rejected", "executor", "reject-test-").count());
        } finally {
            release.countDown();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
