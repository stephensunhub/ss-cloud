package men.sbb8.web.starter.client;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * 包在负载均衡拦截器外面：Spring Cloud 在没有可用实例时抛的是普通的
 * {@code IllegalStateException("No instances available for ...")}，不是网络异常。
 * 这里把它转成 {@link IOException}，{@code RestClient} 会把它包成 {@code ResourceAccessException}，
 * 于是和"下游连不上"一样由全局异常处理返回 502，而不是被当成未知异常变成 500。
 *
 * @author Stephen
 */
public class NoInstanceAsIoErrorInterceptor implements ClientHttpRequestInterceptor {

    /**
     * Spring Cloud LoadBalancer 的固定措辞
     */
    private static final String NO_INSTANCES = "No instances available";

    private final ClientHttpRequestInterceptor delegate;

    public NoInstanceAsIoErrorInterceptor(ClientHttpRequestInterceptor delegate) {
        this.delegate = delegate;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        try {
            return delegate.intercept(request, body, execution);
        } catch (IllegalStateException e) {
            if (e.getMessage() != null && e.getMessage().startsWith(NO_INSTANCES)) {
                throw new IOException(e.getMessage(), e);
            }
            throw e;
        }
    }
}
