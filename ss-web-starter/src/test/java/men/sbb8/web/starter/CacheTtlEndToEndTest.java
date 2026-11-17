package men.sbb8.web.starter;

import men.sbb8.web.starter.redis.CacheTtlResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实 Redis 上验证：starter 默认 30 分钟，CacheTtlResolver 能按缓存项改，返回 null 时仍是默认值。没有本机 Redis 则跳过
 */
// 测试类路径上有 JCache 实现，Boot 会优先选它，所以明确指定用 Redis
@SpringBootTest(properties = "spring.cache.type=redis")
@Import(CacheTtlEndToEndTest.Config.class)
class CacheTtlEndToEndTest {

    private static final String CACHE = "ss-ttl-test";

    @TestConfiguration
    @EnableCaching
    static class Config {

        @Bean
        Items items() {
            return new Items();
        }

        /** key 以 100 开头的缓存 2 小时，其余交给默认值 */
        @Bean
        CacheTtlResolver itemTtl() {
            return (key, value) -> key instanceof Integer id && id >= 100 ? Duration.ofHours(2) : null;
        }
    }

    @Service
    static class Items {
        @Cacheable(cacheNames = CACHE, key = "#id")
        public String get(int id) {
            return "item-" + id;
        }
    }

    @Autowired
    Items items;
    @Autowired
    StringRedisTemplate redis;

    private static boolean redisAvailable() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", 6379), 300);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 缓存写入刚返回时，key 偶尔还查不到（全量测试里出现过几次），等它出现再看 TTL */
    private void awaitKey(String key) {
        for (int i = 0; i < 50 && !Boolean.TRUE.equals(redis.hasKey(key)); i++) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    @AfterEach
    void cleanUp() {
        if (redisAvailable()) {
            var keys = redis.keys(CACHE + "::*");
            if (keys != null && !keys.isEmpty()) {
                redis.delete(keys);
            }
        }
    }

    @Test
    void defaultIs30MinutesAndResolverOverridesPerKey() {
        Assumptions.assumeTrue(redisAvailable(), "本机 6379 没有 Redis，跳过");
        items.get(1);
        items.get(100);

        awaitKey(CACHE + "::1");
        awaitKey(CACHE + "::100");
        long normal = redis.getExpire(CACHE + "::1");
        long longer = redis.getExpire(CACHE + "::100");
        assertTrue(normal > 1700 && normal <= 1800, "默认 30 分钟，实际 " + normal + " 秒");
        assertTrue(longer > 7100 && longer <= 7200, "resolver 指定 2 小时，实际 " + longer + " 秒");
        assertEquals("item-1", items.get(1));
    }
}
