package men.sbb8.web.starter.lock;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 基于 Redisson {@code RLock} 的实现。加锁时不指定租期，由 Redisson 的看门狗续期：
 * 持有期间锁不会过期，持有者宕机后锁在看门狗超时（默认 30 秒）后自动过期
 *
 * @author Stephen
 */
@Slf4j
public class RedissonDistributedLock implements DistributedLock {

    private final RedissonClient redisson;

    public RedissonDistributedLock(RedissonClient redisson) {
        this.redisson = redisson;
    }

    @Override
    public boolean tryRun(String key, Duration wait, Runnable action) {
        RLock lock = redisson.getLock(key);
        if (!acquire(lock, wait)) {
            return false;
        }
        try {
            action.run();
            return true;
        } finally {
            release(lock, key);
        }
    }

    @Override
    public <T> T call(String key, Duration wait, Supplier<T> action) {
        RLock lock = redisson.getLock(key);
        if (!acquire(lock, wait)) {
            throw new LockNotAcquiredException(key);
        }
        try {
            return action.get();
        } finally {
            release(lock, key);
        }
    }

    private static boolean acquire(RLock lock, Duration wait) {
        try {
            return lock.tryLock(wait.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LockNotAcquiredException(lock.getName());
        }
    }

    private static void release(RLock lock, String key) {
        try {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        } catch (RuntimeException e) {
            // 释放失败（比如连接断开）不能盖过业务结果或业务异常；锁会在看门狗超时后自动过期
            log.warn("释放分布式锁失败: key={}", key, e);
        }
    }
}
