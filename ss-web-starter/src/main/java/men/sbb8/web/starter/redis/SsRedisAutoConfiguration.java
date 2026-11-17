package men.sbb8.web.starter.redis;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * Redis 序列化约定：key 用字符串，value 用 JSON。
 *
 * <p>JSON mapper 基于应用统一的 {@link JsonMapper} 派生（继承时间格式等约定），再做三处调整：
 * <ul>
 *     <li>开启类型信息（{@code @class}），读回来是原类型而不是 LinkedHashMap</li>
 *     <li>类型白名单：只允许本项目包与 java.time、集合、Map、数字、UUID、Date，防止反序列化任意类</li>
 *     <li>集合、Map 里的 null 元素原样保留，不应用全局的 NON_NULL</li>
 * </ul>
 * 最外层的 {@code Map.of}、{@code List.of} 等 JDK 内部集合写入前会换成普通集合（见 {@link RootCollectionNormalizingSerializer}）。
 * 需要缓存其他包下的类型时，自行提供 {@code redisTemplate} Bean 覆盖即可。
 *
 * @author Stephen
 */
@AutoConfiguration(after = JacksonAutoConfiguration.class, before = DataRedisAutoConfiguration.class)
@ConditionalOnClass(RedisOperations.class)
public class SsRedisAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "ssRedisJsonSerializer")
    public RedisSerializer<Object> ssRedisJsonSerializer(JsonMapper jsonMapper) {
        GenericJacksonJsonRedisSerializer json = GenericJacksonJsonRedisSerializer.builder(jsonMapper::rebuild)
                .enableDefaultTyping(BasicPolymorphicTypeValidator.builder()
                        .allowIfSubType("men.sbb8.")
                        .allowIfSubType("java.time.")
                        .allowIfSubType(Collection.class)
                        .allowIfSubType(Map.class)
                        .allowIfSubType(Number.class)
                        .allowIfSubType(UUID.class)
                        .allowIfSubType(Date.class)
                        .allowIfSubTypeIsArray()
                        .build())
                .customize(builder -> builder.changeDefaultPropertyInclusion(
                        incl -> incl.withContentInclusion(JsonInclude.Include.ALWAYS)))
                .build();
        return new RootCollectionNormalizingSerializer(json);
    }

    @Bean
    @ConditionalOnMissingBean(name = "redisTemplate")
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory,
                                                       @Qualifier("ssRedisJsonSerializer") RedisSerializer<Object> ssRedisJsonSerializer) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        StringRedisSerializer keySerializer = new StringRedisSerializer();
        template.setKeySerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);
        template.setValueSerializer(ssRedisJsonSerializer);
        template.setHashValueSerializer(ssRedisJsonSerializer);
        return template;
    }

    /**
     * Spring Cache（@Cacheable）的 Redis 缓存值同样用 JSON，不落回默认的 JDK 序列化。
     * 只替换值序列化，Boot 的 spring.cache.redis.*（过期时间、key 前缀等）照常生效。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass({RedisCacheManager.class, RedisCacheManagerBuilderCustomizer.class})
    static class SsRedisCacheConfiguration {

        @Bean
        RedisCacheManagerBuilderCustomizer ssRedisCacheValueSerializer(
                @Qualifier("ssRedisJsonSerializer") RedisSerializer<Object> ssRedisJsonSerializer,
                ObjectProvider<CacheTtlResolver> ttlResolvers) {
            return builder -> {
                RedisCacheConfiguration defaults = builder.cacheDefaults().serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(ssRedisJsonSerializer));
                List<CacheTtlResolver> resolvers = ttlResolvers.orderedStream().toList();
                if (!resolvers.isEmpty()) {
                    // 应用的 CacheTtlResolver 按缓存项决定过期时间；都返回 null 时用默认值（spring.cache.redis.time-to-live）
                    RedisCacheWriter.TtlFunction fallback = defaults.getTtlFunction();
                    defaults = defaults.entryTtl((key, value) -> {
                        for (CacheTtlResolver resolver : resolvers) {
                            Duration ttl = resolver.resolve(key, value);
                            if (ttl != null) {
                                return ttl;
                            }
                        }
                        return fallback.getTimeToLive(key, value);
                    });
                }
                builder.cacheDefaults(defaults);
            };
        }
    }
}
