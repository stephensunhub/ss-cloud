package men.sbb8.web.starter.redis;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

/**
 * 分布式锁（Redisson）。应用引入 {@code org.redisson:redisson} 后，按 {@code spring.data.redis.*}
 * 创建单机模式的 {@link RedissonClient}，不用再配一遍地址和密码：
 * <pre>{@code
 * RLock lock = redissonClient.getLock("order:" + orderId);
 * if (lock.tryLock(3, TimeUnit.SECONDS)) {   // 不指定租期：看门狗自动续期，持有期间锁不会过期；实例宕机后锁自然过期
 *     try { ... } finally { lock.unlock(); }
 * }
 * }</pre>
 *
 * <p>只用来创建客户端：Redisson 的编解码器、其他数据结构不在这里配置，也没有在原生镜像里验证过。
 * 哨兵、集群模式或需要自定义 {@link Config} 时，自己提供 {@link RedissonClient} Bean 即可覆盖。
 * Redisson 创建时就会连接 Redis，连不上则应用启动失败；不需要时去掉依赖，或用
 * {@code spring.autoconfigure.exclude} 排除本类。
 *
 * @author Stephen
 */
@AutoConfiguration(after = DataRedisAutoConfiguration.class)
@ConditionalOnClass({Redisson.class, DataRedisProperties.class})
@EnableConfigurationProperties(DataRedisProperties.class)
public class SsRedissonAutoConfiguration {

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(RedissonClient.class)
    public RedissonClient ssRedissonClient(DataRedisProperties properties) {
        return Redisson.create(toConfig(properties));
    }

    /** 按 spring.data.redis.* 生成单机模式的配置；需要在此基础上调整时，可以拿它再自己创建 RedissonClient */
    public static Config toConfig(DataRedisProperties properties) {
        Config config = new Config();
        SingleServerConfig single = config.useSingleServer().setAddress(address(properties));
        if (StringUtils.hasText(properties.getUsername())) {
            single.setUsername(properties.getUsername());
        }
        if (StringUtils.hasText(properties.getPassword())) {
            single.setPassword(properties.getPassword());
        }
        single.setDatabase(properties.getDatabase());
        if (properties.getTimeout() != null) {
            single.setTimeout((int) properties.getTimeout().toMillis());
        }
        if (properties.getConnectTimeout() != null) {
            single.setConnectTimeout((int) properties.getConnectTimeout().toMillis());
        }
        return config;
    }

    private static String address(DataRedisProperties properties) {
        if (StringUtils.hasText(properties.getUrl())) {
            return properties.getUrl();
        }
        String scheme = properties.getSsl().isEnabled() ? "rediss" : "redis";
        return scheme + "://" + properties.getHost() + ":" + properties.getPort();
    }
}
