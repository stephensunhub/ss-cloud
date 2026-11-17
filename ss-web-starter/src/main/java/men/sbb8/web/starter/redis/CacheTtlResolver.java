package men.sbb8.web.starter.redis;

import org.jspecify.annotations.Nullable;

import java.time.Duration;

/**
 * 按每个缓存项（key、值）决定 Spring Cache（{@code @Cacheable} 等）的过期时间。
 * 应用把实现注册成 Bean 即可；没有 Bean，或者返回 {@code null} 时，用默认过期时间
 * （{@code spring.cache.redis.time-to-live}，starter 默认 30 分钟）。有多个 Bean 时按顺序（{@code @Order}）问，第一个返回非 null 的生效。
 *
 * <p>{@code key} 是 {@code @Cacheable} 解析出来的原始 key 对象（不是带缓存名前缀的字符串），
 * {@code value} 是要缓存的值，读取缓存时不会调用这里。例如：
 * <pre>{@code
 * @Bean
 * CacheTtlResolver ttlResolver() {
 *     return (key, value) -> value instanceof VipUser ? Duration.ofHours(6) : null;
 * }
 * }</pre>
 * 想让某个缓存项永不过期，返回 {@link Duration#ZERO}。
 * 每次调用才知道过期时间的（比如调用方传入）用 {@code RedisTemplate} 直接写，注解做不到。
 *
 * @author Stephen
 */
@FunctionalInterface
public interface CacheTtlResolver {

    @Nullable Duration resolve(Object key, @Nullable Object value);
}
