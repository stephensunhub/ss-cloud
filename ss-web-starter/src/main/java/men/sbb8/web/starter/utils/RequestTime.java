package men.sbb8.web.starter.utils;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * 当前请求到达本服务的时间（毫秒时间戳）。由 {@code RequestTimeFilter} 在请求进来时记到请求属性里，
 * 信封 {@code Resp} 的 requestTime 从这里取
 *
 * @author Stephen
 */
public final class RequestTime {

    /** 请求属性名 */
    public static final String ATTRIBUTE = RequestTime.class.getName() + ".millis";

    private RequestTime() {
    }

    /**
     * 当前请求的到达时间；不在请求线程里（定时任务、异步线程没有传递请求上下文）或过滤器没生效时返回 fallback
     */
    public static long currentOr(long fallback) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return fallback;
        }
        try {
            return attributes.getAttribute(ATTRIBUTE, RequestAttributes.SCOPE_REQUEST) instanceof Long millis ? millis : fallback;
        } catch (IllegalStateException e) {
            // 请求已经结束（异步线程晚于请求完成）
            return fallback;
        }
    }
}
