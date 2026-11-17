package men.sbb8.web.starter.utils;

import jakarta.servlet.http.HttpServletRequest;
import men.sbb8.web.starter.error.BusinessException;
import men.sbb8.web.starter.error.CommonCode;
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
     * 客户端 IP，即 {@code request.getRemoteAddr()}。
     * 不自行解析 X-Forwarded-For（最左边的值由客户端随意填写，可伪造）。
     * 位于代理之后时，由 {@code server.forward-headers-strategy=native}（starter 默认）让容器只信任内网代理，
     * 并据此还原真实地址。
     */
    public static String getRemoteIp() {
        return getRemoteIp(getRequest());
    }

    public static String getRemoteIp(HttpServletRequest request) {
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
     * 当前用户 ID（X-User-Id），缺失或不是数字时抛 401。
     * 该头由网关写入，服务必须只能经网关访问，见 {@link UserInfoArgumentResolver}
     */
    public static Long getCurrentUserId() {
        String userId = getHeader(UserInfoArgumentResolver.HEADER_USER_ID);
        if (userId == null || userId.isBlank()) {
            throw unauthorized();
        }
        try {
            return Long.parseLong(userId.trim());
        } catch (NumberFormatException e) {
            throw unauthorized();
        }
    }

    private static BusinessException unauthorized() {
        return new BusinessException(CommonCode.UNAUTHORIZED);
    }
}
