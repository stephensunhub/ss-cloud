package men.sbb8.web.starter;

import men.sbb8.web.starter.config.SsJacksonAutoConfiguration;
import men.sbb8.web.starter.redis.SsRedisAutoConfiguration;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 真实 Redis 上验证 starter 的 redisTemplate 用在 Hash / Set / ZSet / List / 计数器 / Pub/Sub 上的行为（README 里的说明以此为准）。
 * 没有本机 Redis 则跳过
 */
class RedisStructuresTest {

    record Item(String id, Instant at) {
    }

    private static final String P = "ss-struct-test:";

    private static boolean redisAvailable() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", 6379), 300);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    void structuresWorkWithTheStarterTemplate() {
        Assumptions.assumeTrue(redisAvailable(), "本机 6379 没有 Redis，跳过");
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, SsJacksonAutoConfiguration.class,
                        SsRedisAutoConfiguration.class, DataRedisAutoConfiguration.class))
                .run(ctx -> {
                    @SuppressWarnings("unchecked")
                    RedisTemplate<String, Object> redis = ctx.getBean("redisTemplate", RedisTemplate.class);
                    StringRedisTemplate strings = ctx.getBean(StringRedisTemplate.class);
                    Item item = new Item("a", Instant.parse("2026-01-02T03:04:05Z"));
                    try {
                        // Hash：field 是字符串，值是 JSON，读回来是原类型
                        redis.opsForHash().put(P + "hash", "f1", item);
                        assertEquals(item, redis.opsForHash().get(P + "hash", "f1"));

                        // Set：成员是 JSON
                        redis.opsForSet().add(P + "set", item, "x");
                        assertEquals(Set.of(item, "x"), redis.opsForSet().members(P + "set"));

                        // ZSet
                        redis.opsForZSet().add(P + "zset", item, 2);
                        redis.opsForZSet().add(P + "zset", "low", 1);
                        assertEquals(List.of("low", item), List.copyOf(redis.opsForZSet().range(P + "zset", 0, -1)));

                        // List 当队列：先进先出
                        redis.opsForList().rightPush(P + "list", item);
                        redis.opsForList().rightPush(P + "list", "second");
                        assertEquals(item, redis.opsForList().leftPop(P + "list"));
                        assertEquals("second", redis.opsForList().leftPop(P + "list"));

                        // 计数器：数字存成纯数字，increment 可用，读回来是 Integer
                        assertEquals(1L, redis.opsForValue().increment(P + "counter"));
                        assertEquals(3L, redis.opsForValue().increment(P + "counter", 2));
                        assertEquals(3, redis.opsForValue().get(P + "counter"));

                        // String 在 redisTemplate 里存成带引号的 JSON；StringRedisTemplate 读到的是带引号的原文
                        redis.opsForValue().set(P + "str", "abc");
                        assertEquals("\"abc\"", strings.opsForValue().get(P + "str"));
                        strings.opsForValue().set(P + "raw", "abc");
                        Throwable mixed = null;
                        try {
                            redis.opsForValue().get(P + "raw");
                        } catch (Exception e) {
                            mixed = e;
                        }
                        assertNotNull(mixed, "StringRedisTemplate 写的原文，用 redisTemplate 读会失败");
                    } finally {
                        var keys = strings.keys(P + "*");
                        if (keys != null && !keys.isEmpty()) {
                            strings.delete(keys);
                        }
                    }
                });
    }

    @Test
    void pubSubDeliversToOnlineSubscribersOnly() {
        Assumptions.assumeTrue(redisAvailable(), "本机 6379 没有 Redis，跳过");
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, SsJacksonAutoConfiguration.class,
                        SsRedisAutoConfiguration.class, DataRedisAutoConfiguration.class))
                .run(ctx -> {
                    @SuppressWarnings("unchecked")
                    RedisTemplate<String, Object> redis = ctx.getBean("redisTemplate", RedisTemplate.class);
                    RedisConnectionFactory factory = ctx.getBean(RedisConnectionFactory.class);
                    String channel = P + "channel";
                    Item item = new Item("a", Instant.parse("2026-01-02T03:04:05Z"));

                    // 没有订阅者时发布：消息直接丢了（Pub/Sub 不持久化）
                    assertEquals(0L, redis.convertAndSend(channel, item));

                    BlockingQueue<Object> received = new LinkedBlockingQueue<>();
                    RedisMessageListenerContainer container = new RedisMessageListenerContainer();
                    container.setConnectionFactory(factory);
                    container.afterPropertiesSet();
                    container.start();
                    try {
                        container.addMessageListener((message, pattern) ->
                                received.add(redis.getValueSerializer().deserialize(message.getBody())), new ChannelTopic(channel));
                        // 订阅是异步建立的，等到发布能被收到
                        Object got = null;
                        for (int i = 0; i < 20 && got == null; i++) {
                            redis.convertAndSend(channel, item);
                            got = received.poll(200, TimeUnit.MILLISECONDS);
                        }
                        assertEquals(item, got, "在线订阅者收到消息，值读回来是原类型");
                    } finally {
                        container.stop();
                        container.destroy();
                    }
                });
    }
}
