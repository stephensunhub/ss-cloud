package men.sbb8.web.starter;

import men.sbb8.web.starter.config.SsJacksonAutoConfiguration;
import men.sbb8.web.starter.redis.SsRedisAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RedisSerializationTest {

    record Order(String id, LocalDateTime createdAt, Instant paidAt, List<String> tags, Map<String, Object> extra) {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, SsJacksonAutoConfiguration.class,
                    SsRedisAutoConfiguration.class, DataRedisAutoConfiguration.class))
            .withBean(RedisConnectionFactory.class, () -> Mockito.mock(RedisConnectionFactory.class));

    @Test
    void templateUsesJsonValueSerializer() {
        runner.run(ctx -> {
            RedisTemplate<?, ?> template = ctx.getBean("redisTemplate", RedisTemplate.class);
            assertSame(ctx.getBean("ssRedisJsonSerializer"), template.getValueSerializer());
            assertSame(ctx.getBean("ssRedisJsonSerializer"), template.getHashValueSerializer());
            assertInstanceOf(org.springframework.data.redis.serializer.StringRedisSerializer.class, template.getKeySerializer());
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void roundTripKeepsTypesAndNulls() {
        runner.run(ctx -> {
            RedisSerializer<Object> serializer = ctx.getBean("ssRedisJsonSerializer", RedisSerializer.class);
            Map<String, Object> extra = new HashMap<>();
            extra.put("k", null);
            extra.put("n", 1L);
            Order order = new Order("o1", LocalDateTime.of(2026, 1, 2, 3, 4, 5),
                    Instant.parse("2026-01-02T03:04:05Z"), new ArrayList<>(List.of("a", "b")), extra);

            Object back = serializer.deserialize(serializer.serialize(order));

            assertEquals(order, back);
            Map<String, Object> backExtra = ((Order) back).extra();
            assertEquals(true, backExtra.containsKey("k"));
            assertEquals(1L, backExtra.get("n"));
        });
    }

    @Test
    void rejectsTypesOutsideAllowList() {
        runner.run(ctx -> {
            RedisSerializer<Object> serializer = ctx.getBean("ssRedisJsonSerializer", RedisSerializer.class);
            byte[] evil = "{\"@class\":\"java.lang.ProcessBuilder\",\"command\":[\"ls\"]}".getBytes(StandardCharsets.UTF_8);
            assertThrows(SerializationException.class, () -> serializer.deserialize(evil));
        });
    }

    @Test
    void applicationCanOverrideTemplate() {
        runner.withBean("redisTemplate", RedisTemplate.class, () -> {
            RedisTemplate<String, Object> custom = new RedisTemplate<>();
            custom.setConnectionFactory(Mockito.mock(RedisConnectionFactory.class));
            return custom;
        }).run(ctx -> {
            assertNotNull(ctx.getBean("redisTemplate"));
            assertNotSame(ctx.getBean("ssRedisJsonSerializer"), ctx.getBean("redisTemplate", RedisTemplate.class).getValueSerializer());
        });
    }
}
