package men.sbb8.web.starter.client;

import jakarta.servlet.http.HttpServletRequest;
import men.sbb8.web.starter.resolver.UserInfoArgumentResolver;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.util.List;

/**
 * 把当前请求的用户上下文头（X-User-Id 等）透传给下游内部服务，没有当前请求时不做任何事
 *
 * @author Stephen
 */
public class UserHeadersInterceptor implements ClientHttpRequestInterceptor {

    private static final List<String> HEADERS = List.of(
            UserInfoArgumentResolver.HEADER_CHANNEL_ID,
            UserInfoArgumentResolver.HEADER_USER_ID,
            UserInfoArgumentResolver.HEADER_USER_NAME,
            UserInfoArgumentResolver.HEADER_CLIENT,
            UserInfoArgumentResolver.HEADER_LANGUAGE,
            UserInfoArgumentResolver.HEADER_REQUEST_IP);

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest current = attributes.getRequest();
            for (String name : HEADERS) {
                String value = current.getHeader(name);
                if (value != null && !request.getHeaders().containsHeader(name)) {
                    request.getHeaders().set(name, value);
                }
            }
        }
        return execution.execute(request, body);
    }
}
