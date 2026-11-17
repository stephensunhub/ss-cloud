package men.sbb8.web.starter;

import men.sbb8.web.starter.redis.SsRedissonAutoConfiguration;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void mutualExclusionAndReentrancy() {
        Assumptions.assumeTrue(redisAvailable(), "本机 6379 没有 Redis，跳过");
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(DataRedisAutoConfiguration.class, SsRedissonAutoConfiguration.class))
                .run(ctx -> {
                    RedissonClient redisson = ctx.getBean(RedissonClient.class);
                    RLock lock = redisson.getLock("ss-web-starter-test:lock");
                    assertTrue(lock.tryLock(1, 10, TimeUnit.SECONDS));
                    assertTrue(lock.tryLock(1, 10, TimeUnit.SECONDS), "同一线程可重入");
                    assertEquals(2, lock.getHoldCount());

                    boolean[] otherGotIt = new boolean[1];
                    Thread other = Thread.ofPlatform().start(() -> {
                        try {
                            otherGotIt[0] = redisson.getLock("ss-web-starter-test:lock").tryLock(200, 5000, TimeUnit.MILLISECONDS);
                        } catch (InterruptedException ignored) {
                        }
                    });
                    other.join();
                    assertFalse(otherGotIt[0], "别的线程拿不到同一把锁");

                    lock.unlock();
                    lock.unlock();
                    assertFalse(lock.isLocked());
                });
    }
}
