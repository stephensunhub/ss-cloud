package men.sbb8.web.starter.utils;

import jakarta.servlet.http.HttpServletRequest;
import men.sbb8.web.starter.error.BusinessException;
import men.sbb8.web.starter.resolver.UserInfoArgumentResolver;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Servlet 工具类，仅可在请求线程中使用
 *
 * @author Stephen
 */
public final class WebUtils {

    private WebUtils() {
    }

    public static HttpServletRequest getRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        throw new IllegalStateException("当前线程没有绑定 Servlet 请求");
    }

    public static String getHeader(String name) {
        return getRequest().getHeader(name);
    }

    /**
     * 客户端 IP：X-Forwarded-For 首个地址 → X-Real-IP → 连接地址。
     * 请求头可被客户端伪造，仅在服务位于可信网关之后时可信。
     */
    public static String getRemoteIp() {
        return getRemoteIp(getRequest());
    }

    public static String getRemoteIp(HttpServletRequest request) {
        for (String header : new String[]{"X-Forwarded-For", "X-Real-IP"}) {
            String value = request.getHeader(header);
            if (value != null && !value.isBlank() && !"unknown".equalsIgnoreCase(value.trim())) {
                return value.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }

    /**
     * 接口标识，形如 {@code GET:/api/users}
     */
    public static String getApiPath() {
        HttpServletRequest request = getRequest();
        return request.getMethod() + ":" + request.getRequestURI();
    }

    /**
     * 当前用户 ID（X-User-Id），缺失时抛业务异常
     */
    public static Long getCurrentUserId() {
        String userId = getHeader(UserInfoArgumentResolver.HEADER_USER_ID);
        if (userId == null || userId.isBlank()) {
            throw new BusinessException("Current user userId is empty");
        }
        try {
            return Long.parseLong(userId.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException("Current user userId is invalid: " + userId);
        }
    }
}
