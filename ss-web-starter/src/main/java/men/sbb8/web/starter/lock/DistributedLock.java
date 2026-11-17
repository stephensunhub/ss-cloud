package men.sbb8.web.starter.lock;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * 分布式锁。业务代码只依赖这个接口，不直接用 Redisson（{@code RedissonClient}、{@code RLock} 等），
 * 以后换实现只需要提供另一个 {@code DistributedLock} Bean。
 *
 * <p>约定（实现必须满足）：
 * <ul>
 *     <li>同一个 key 在任意时刻只有一个持有者，跨进程、跨实例互斥</li>
 *     <li>可重入：持有锁的线程再次对同一个 key 加锁会成功</li>
 *     <li>持有期间锁不会因为超时而被别人抢走（执行时间不确定也安全）；持有者宕机后锁会自动过期，不会永久占用</li>
 *     <li>{@code action} 抛出的异常原样抛给调用方，锁一定会释放</li>
 *     <li>{@code action} 在调用线程里同步执行</li>
 * </ul>
 *
 * @author Stephen
 */
public interface DistributedLock {

    /**
     * 拿到锁就执行 {@code action} 并返回 {@code true}；等待 {@code wait} 仍拿不到锁则不执行，返回 {@code false}
     *
     * @param wait 最长等待时间，{@link Duration#ZERO} 表示拿不到立刻返回
     */
    boolean tryRun(String key, Duration wait, Runnable action);

    /**
     * 拿到锁就执行 {@code action} 并返回结果；等待 {@code wait} 仍拿不到锁则抛 {@link LockNotAcquiredException}
     *
     * @param wait 最长等待时间，{@link Duration#ZERO} 表示拿不到立刻失败
     */
    <T> T call(String key, Duration wait, Supplier<T> action);

    /**
     * 同 {@link #call}，没有返回值
     */
    default void run(String key, Duration wait, Runnable action) {
        call(key, wait, () -> {
            action.run();
            return null;
        });
    }
}
