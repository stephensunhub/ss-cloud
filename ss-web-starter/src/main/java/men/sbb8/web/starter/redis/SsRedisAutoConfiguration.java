package men.sbb8.web.starter.redis;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.json.JsonMapper;

import java.util.Collection;
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
        return GenericJacksonJsonRedisSerializer.builder(jsonMapper::rebuild)
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
                        incl -> incl.withContentInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)))
                .build();
    }

    @Bean
    @ConditionalOnMissingBean(name = "redisTemplate")
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory,
                                                       RedisSerializer<Object> ssRedisJsonSerializer) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        StringRedisSerializer keySerializer = new StringRedisSerializer();
        template.setKeySerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);
        template.setValueSerializer(ssRedisJsonSerializer);
        template.setHashValueSerializer(ssRedisJsonSerializer);
        return template;
    }
}
