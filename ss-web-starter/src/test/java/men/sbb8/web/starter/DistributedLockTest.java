package men.sbb8.web.starter;

import men.sbb8.web.starter.lock.DistributedLock;
import men.sbb8.web.starter.lock.LockNotAcquiredException;
import men.sbb8.web.starter.lock.RedissonDistributedLock;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Redisson 实现的边界行为（用模拟的 RLock，不需要 Redis）
 */
class DistributedLockTest {

    private final RedissonClient redisson = mock(RedissonClient.class);
    private final RLock lock = mock(RLock.class);
    private final DistributedLock locks = new RedissonDistributedLock(redisson);

    DistributedLockTest() {
        when(redisson.getLock("k")).thenReturn(lock);
        when(lock.getName()).thenReturn("k");
    }

    @Test
    void notAcquiredDoesNotRunAndDoesNotUnlock() throws Exception {
        when(lock.tryLock(anyLong(), org.mockito.ArgumentMatchers.eq(TimeUnit.MILLISECONDS))).thenReturn(false);
        AtomicBoolean ran = new AtomicBoolean();

        assertFalse(locks.tryRun("k", Duration.ofMillis(10), () -> ran.set(true)));
        assertFalse(ran.get());
        LockNotAcquiredException e = assertThrows(LockNotAcquiredException.class,
                () -> locks.call("k", Duration.ofMillis(10), () -> "x"));
        assertEquals("k", e.getKey());
        verify(lock, never()).unlock();
    }

    @Test
    void acquiredRunsAndUnlocks() throws Exception {
        when(lock.tryLock(anyLong(), org.mockito.ArgumentMatchers.eq(TimeUnit.MILLISECONDS))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        assertEquals("ok", locks.call("k", Duration.ofSeconds(1), () -> "ok"));
        verify(lock).unlock();
    }

    @Test
    void actionExceptionIsRethrownAndLockReleased() throws Exception {
        when(lock.tryLock(anyLong(), org.mockito.ArgumentMatchers.eq(TimeUnit.MILLISECONDS))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> locks.run("k", Duration.ZERO, () -> {
            throw new IllegalStateException("boom");
        }));
        verify(lock).unlock();
    }

    @Test
    void failureToUnlockDoesNotHideTheResult() throws Exception {
        when(lock.tryLock(anyLong(), org.mockito.ArgumentMatchers.eq(TimeUnit.MILLISECONDS))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        doThrow(new RuntimeException("connection lost")).when(lock).unlock();

        assertEquals("ok", locks.call("k", Duration.ZERO, () -> "ok"));
    }

    @Test
    void interruptedWhileWaitingKeepsTheInterruptFlag() throws Exception {
        when(lock.tryLock(anyLong(), org.mockito.ArgumentMatchers.eq(TimeUnit.MILLISECONDS)))
                .thenThrow(new InterruptedException());
        try {
            assertThrows(LockNotAcquiredException.class, () -> locks.call("k", Duration.ofSeconds(1), () -> "x"));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }
}
