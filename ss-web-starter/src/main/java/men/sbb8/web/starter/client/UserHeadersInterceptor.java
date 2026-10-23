package men.sbb8.web.starter.client;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import men.sbb8.web.starter.resolver.UserInfoArgumentResolver;

import java.io.IOException;
import java.util.Map;

/**
 * 把当前用户上下文头（X-User-Id 等，见 {@link UserHeadersContext}）透传给下游内部服务。
 * <ul>
 *     <li>请求线程、以及通过 Context Propagation 带上了用户头的异步线程都会透传；</li>
 *     <li>调用方已经显式设置的同名头不覆盖；</li>
 *     <li>当前没有 X-Request-Ip（没有请求、或网关没写）时，透传 {@value UserHeadersContext#UNKNOWN_IP}，
 *     下游永远能拿到一个 IP 值；其它头缺失则不补。</li>
 * </ul>
 *
 * @author Stephen
 */
public class UserHeadersInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        Map<String, String> context = UserHeadersContext.current();
        for (String name : UserHeadersContext.HEADERS) {
            String value = context.get(name);
            if ((value == null || value.isBlank()) && UserInfoArgumentResolver.HEADER_REQUEST_IP.equals(name)) {
                value = UserHeadersContext.UNKNOWN_IP;
            }
            if (value != null && !request.getHeaders().containsHeader(name)) {
                request.getHeaders().set(name, value);
            }
        }
        return execution.execute(request, body);
    }
}
