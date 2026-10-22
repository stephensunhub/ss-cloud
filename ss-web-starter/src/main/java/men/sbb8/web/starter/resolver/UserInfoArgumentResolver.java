package men.sbb8.web.starter.resolver;

import jakarta.servlet.http.HttpServletRequest;
import men.sbb8.web.starter.dto.UserInfo;
import men.sbb8.web.starter.error.BusinessException;
import men.sbb8.web.starter.error.CommonCode;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * 从网关透传的请求头解析 {@link UserInfo}。
 *
 * <p><b>安全前提：</b>这些头不做任何签名校验，被直接信任。因此服务只能通过网关访问，
 * 网关必须丢弃客户端自带的同名头再写入真实值，服务端口不得对外网或其他不可信网络开放。
 * 配置 {@code ss.web.user-info.required=true} 后，缺少 X-User-Id 的请求会得到 401。
 *
 * @author Stephen
 */
public class UserInfoArgumentResolver implements HandlerMethodArgumentResolver {

    public static final String HEADER_CHANNEL_ID = "X-Channel-Id";
    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_NAME = "X-User-Name";
    public static final String HEADER_CLIENT = "X-Client";
    public static final String HEADER_LANGUAGE = "X-User-Language";
    public static final String HEADER_REQUEST_IP = "X-Request-Ip";

    private final boolean required;

    public UserInfoArgumentResolver(boolean required) {
        this.required = required;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType() == UserInfo.class;
    }

    @Override
    public @Nullable Object resolveArgument(MethodParameter parameter,
                                            @Nullable ModelAndViewContainer mavContainer,
                                            NativeWebRequest webRequest,
                                            @Nullable WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        if (request == null) {
            throw new IllegalStateException("当前不是 Servlet 请求");
        }
        String userId = request.getHeader(HEADER_USER_ID);
        if (required && (userId == null || userId.isBlank())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, CommonCode.UNAUTHORIZED,
                    CommonCode.UNAUTHORIZED.getMsg());
        }
        return new UserInfo(
                toInteger(request.getHeader(HEADER_CHANNEL_ID)),
                userId,
                request.getHeader(HEADER_USER_NAME),
                request.getHeader(HEADER_CLIENT),
                request.getHeader(HEADER_LANGUAGE),
                request.getHeader(HEADER_REQUEST_IP),
                System.currentTimeMillis());
    }

    private static @Nullable Integer toInteger(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
