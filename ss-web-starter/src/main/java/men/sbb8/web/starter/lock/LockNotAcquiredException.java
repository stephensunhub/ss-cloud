package men.sbb8.web.starter.lock;

import lombok.Getter;

/**
 * 在等待时间内没有拿到分布式锁。需要把它转成业务提示（如"操作太频繁"）的调用方自己捕获；
 * 没有捕获时按未知异常处理（500，不暴露细节）
 *
 * @author Stephen
 */
@Getter
public class LockNotAcquiredException extends RuntimeException {

    private final String key;

    public LockNotAcquiredException(String key) {
        super("获取锁失败: " + key);
        this.key = key;
    }
}
