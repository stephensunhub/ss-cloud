package men.sbb8.web.starter.client;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * 按服务名调用（{@code base-url: lb://wallet}）时，Spring Cloud LoadBalancer 在没有可用实例时抛的是普通的
 * {@code IllegalStateException("No instances available for ...")}，不是网络异常。
 * 这里把它转成 {@link IOException}，{@code RestClient} 会把它包成 {@code ResourceAccessException}，
 * 于是和"下游连不上"一样由全局异常处理返回 502，而不是被当成未知异常变成 500。
 * <p>
 * 必须是拦截器链里最外层的一个（排在负载均衡拦截器之前），否则捕获不到它抛出的异常，
 * 见 {@code SsHttpClientAutoConfiguration#ORDER}。没有使用负载均衡的分组不受影响。
 *
 * @author Stephen
 */
public class NoInstanceAsIoErrorInterceptor implements ClientHttpRequestInterceptor {

    /**
     * Spring Cloud LoadBalancer 的固定措辞，测试里用真实的 LoadBalancer 校验，措辞变了会立刻发现
     */
    static final String NO_INSTANCES = "No instances available";

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        try {
            return execution.execute(request, body);
        } catch (IllegalStateException e) {
            if (e.getMessage() != null && e.getMessage().startsWith(NO_INSTANCES)) {
                throw new IOException(e.getMessage(), e);
            }
            throw e;
        }
    }
}
