package men.sbb8.web.starter;

import men.sbb8.web.starter.config.SsJacksonAutoConfiguration;
import men.sbb8.web.starter.redis.SsRedisAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedisCacheSerializationTest {

    record Item(String id, Instant at) {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, SsJacksonAutoConfiguration.class,
                    SsRedisAutoConfiguration.class, DataRedisAutoConfiguration.class))
            .withBean(RedisConnectionFactory.class, () -> Mockito.mock(RedisConnectionFactory.class));

    @Test
    void cacheValuesUseJsonAndKeepOtherDefaults() throws Exception {
        runner.run(ctx -> {
            RedisCacheManager.RedisCacheManagerBuilder builder =
                    RedisCacheManager.builder(Mockito.mock(RedisConnectionFactory.class))
                            .cacheDefaults(RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofMinutes(5)));
            ctx.getBean(RedisCacheManagerBuilderCustomizer.class).customize(builder);

            RedisCacheConfiguration defaults = builder.cacheDefaults();
            assertEquals(Duration.ofMinutes(5), defaults.getTtlFunction().getTimeToLive(Item.class, null));

            Item item = new Item("a", Instant.parse("2026-01-02T03:04:05Z"));
            ByteBuffer bytes = defaults.getValueSerializationPair().write(item);
            String json = new String(bytes.array(), bytes.arrayOffset() + bytes.position(), bytes.remaining());
            assertTrue(json.contains("\"@class\""), json);
            assertTrue(json.contains("2026-01-02T03:04:05Z"), json);
            assertEquals(item, defaults.getValueSerializationPair().read(ByteBuffer.wrap(json.getBytes())));
        });
    }
}
