package men.sbb8.web.starter.config;

import men.sbb8.web.starter.client.NoInstanceAsIoErrorInterceptor;
import men.sbb8.web.starter.client.RemoteErrorHandler;
import men.sbb8.web.starter.client.UserHeadersContext;
import men.sbb8.web.starter.client.UserHeadersInterceptor;
import io.micrometer.context.ContextRegistry;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.registry.HttpServiceGroupConfigurer;
import tools.jackson.databind.json.JsonMapper;

/**
 * HTTP Service Client（@HttpExchange）的统一约定：
 * <ul>
 *     <li>所有 @HttpExchange 客户端的非 2xx 响应统一转成 RemoteCallException（不影响应用自己创建的 RestClient）</li>
 *     <li>下游连不上、超时统一由全局异常处理返回 502 / 504；按服务名调用时"没有可用实例"同样如此</li>
 *     <li>按 ss.web.client.propagate-user-headers 透传当前用户请求头（默认 all，所有分组；含 @Async 等异步线程；缺失的 X-Request-Ip 用 0.0.0.0）</li>
 *     <li>超时等通用参数用 spring.http.clients.* 配置（默认值见 ss-web-defaults.yaml）</li>
 * </ul>
 * 使用方式：在配置类上 {@code @ImportHttpServices(group = "wallet", types = WalletClient.class)}，
 * 并配置 {@code spring.http.serviceclient.wallet.base-url}。
 * <p>
 * 按服务名调用用 Spring Cloud 官方的写法：引入 {@code spring-cloud-starter-loadbalancer} 和服务发现后，
 * {@code base-url} 写成 {@code lb://wallet}，不需要这里再做任何配置。
 *
 * @author Stephen
 */
@AutoConfiguration
@ConditionalOnClass(RestClient.class)
@EnableConfigurationProperties(SsWebProperties.class)
public class SsHttpClientAutoConfiguration {

    /**
     * 分组配置器的顺序，必须小于 Spring Cloud LoadBalancer 的分组配置器（顺序 10）：
     * 先执行的配置器加入的拦截器在拦截器链的外层，{@link NoInstanceAsIoErrorInterceptor} 必须在负载均衡拦截器之外才能捕获它的异常。
     * 测试用真实的 LoadBalancer 校验了这一点。
     */
    static final int ORDER = 0;

    @Bean
    public RestClientHttpServiceGroupConfigurer ssHttpServiceGroupConfigurer(JsonMapper jsonMapper,
                                                                            SsWebProperties properties) {
        return new SsHttpServiceGroupConfigurer(new RemoteErrorHandler(jsonMapper), properties.getClient());
    }

    /**
     * 把用户头注册为可跨线程传递的上下文：Boot 的 @Async 线程池（propagate-context 默认开启）提交任务时复制、
     * 执行时恢复，异步线程里的 @HttpExchange 调用也能透传。同一个键只注册一次（多个应用上下文并存时不重复）
     */
    @Bean
    public InitializingBean ssUserHeadersContextPropagation() {
        return () -> {
            ContextRegistry registry = ContextRegistry.getInstance();
            boolean registered = registry.getThreadLocalAccessors().stream()
                    .anyMatch(accessor -> UserHeadersContext.KEY.equals(accessor.key()));
            if (!registered) {
                registry.registerThreadLocalAccessor(new UserHeadersContext.Accessor());
            }
        };
    }

    static final class SsHttpServiceGroupConfigurer implements RestClientHttpServiceGroupConfigurer {

        private final RemoteErrorHandler errorHandler;
        private final SsWebProperties.Client client;

        SsHttpServiceGroupConfigurer(RemoteErrorHandler errorHandler, SsWebProperties.Client client) {
            this.errorHandler = errorHandler;
            this.client = client;
        }

        @Override
        public void configureGroups(HttpServiceGroupConfigurer.Groups<RestClient.Builder> groups) {
            NoInstanceAsIoErrorInterceptor noInstance = new NoInstanceAsIoErrorInterceptor();
            groups.forEachClient((group, builder) -> {
                builder.requestInterceptor(noInstance).defaultStatusHandler(HttpStatusCode::isError, errorHandler);
                if (client.propagatesTo(group.name())) {
                    builder.requestInterceptor(new UserHeadersInterceptor());
                }
            });
        }

        @Override
        public int getOrder() {
            return ORDER;
        }
    }
}
