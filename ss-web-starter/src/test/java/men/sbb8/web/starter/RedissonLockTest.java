package men.sbb8.web.starter;

import men.sbb8.web.starter.lock.DistributedLock;
import men.sbb8.web.starter.redis.SsRedissonAutoConfiguration;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.redisson.config.Config;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Redisson 客户端按 spring.data.redis.* 创建。配置转换不需要 Redis；加锁的用例需要本机 6379 有 Redis，没有则跳过
 */
class RedissonLockTest {

    private static boolean redisAvailable() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", 6379), 300);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    void configFollowsSpringDataRedisProperties() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(DataRedisAutoConfiguration.class))
                .withPropertyValues("spring.data.redis.host=redis.internal", "spring.data.redis.port=6380",
                        "spring.data.redis.password=pw", "spring.data.redis.database=3",
                        "spring.data.redis.ssl.enabled=true", "spring.data.redis.timeout=2s")
                .run(ctx -> {
                    var properties = ctx.getBean(org.springframework.boot.data.redis.autoconfigure.DataRedisProperties.class);
                    Config config = SsRedissonAutoConfiguration.toConfig(properties);
                    var single = config.useSingleServer();
                    assertEquals("rediss://redis.internal:6380", single.getAddress());
                    assertEquals("pw", single.getPassword());
                    assertEquals(3, single.getDatabase());
                    assertEquals(2000, single.getTimeout());
                });
    }

    @Test
    void distributedLockIsExposedAndWorksAgainstRealRedis() {
        Assumptions.assumeTrue(redisAvailable(), "本机 6379 没有 Redis，跳过");
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(DataRedisAutoConfiguration.class, SsRedissonAutoConfiguration.class))
                .run(ctx -> {
                    DistributedLock locks = ctx.getBean(DistributedLock.class);
                    String key = "ss-web-starter-test:lock";

                    // 可重入：同一线程嵌套加同一个 key
                    assertEquals("inner", locks.call(key, Duration.ofSeconds(1),
                            () -> locks.call(key, Duration.ofSeconds(1), () -> "inner")));

                    // 互斥：持有期间别的线程拿不到，tryRun 返回 false 且不执行
                    AtomicBoolean ran = new AtomicBoolean();
                    AtomicBoolean otherResult = new AtomicBoolean(true);
                    locks.run(key, Duration.ofSeconds(1), () -> {
                        Thread other = Thread.ofPlatform().start(
                                () -> otherResult.set(locks.tryRun(key, Duration.ofMillis(200), () -> ran.set(true))));
                        try {
                            other.join();
                        } catch (InterruptedException e) {
                            throw new IllegalStateException(e);
                        }
                    });
                    assertFalse(otherResult.get());
                    assertFalse(ran.get());

                    // 释放后可以再拿到
                    assertTrue(locks.tryRun(key, Duration.ZERO, () -> ran.set(true)));
                    assertTrue(ran.get());

                    // 业务异常原样抛出，锁照样释放
                    assertThrows(IllegalStateException.class, () -> locks.run(key, Duration.ZERO, () -> {
                        throw new IllegalStateException("boom");
                    }));
                    assertTrue(locks.tryRun(key, Duration.ZERO, () -> {
                    }));
                });
    }
}
