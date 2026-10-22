package men.sbb8.web.starter.config;

import men.sbb8.web.starter.client.NoInstanceAsIoErrorInterceptor;
import men.sbb8.web.starter.client.RemoteErrorHandler;
import men.sbb8.web.starter.client.UserHeadersInterceptor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.DeferringLoadBalancerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * HTTP Service Client（@HttpExchange）的统一约定：
 * <ul>
 *     <li>所有 @HttpExchange 客户端的非 2xx 响应统一转成 RemoteCallException（不影响应用自己创建的 RestClient）</li>
 *     <li>ss.web.client.propagate-user-headers 指定的分组透传当前用户请求头</li>
 *     <li>超时等通用参数用 spring.http.clients.* 配置（默认值见 ss-web-defaults.yaml）</li>
 * </ul>
 * 使用方式：在配置类上 {@code @ImportHttpServices(group = "wallet", types = WalletClient.class)}，
 * 并配置 {@code spring.http.serviceclient.wallet.base-url}
 *
 * @author Stephen
 */
@AutoConfiguration
@ConditionalOnClass(RestClient.class)
@EnableConfigurationProperties(SsWebProperties.class)
public class SsHttpClientAutoConfiguration {

    @Bean
    public RestClientHttpServiceGroupConfigurer ssHttpServiceGroupConfigurer(JsonMapper jsonMapper,
                                                                            SsWebProperties properties) {
        RemoteErrorHandler errorHandler = new RemoteErrorHandler(jsonMapper);
        List<String> userHeaderGroups = properties.getClient().getPropagateUserHeaders();
        return groupConfigurer -> {
            groupConfigurer.forEachClient((group, builder) ->
                    builder.defaultStatusHandler(HttpStatusCode::isError, errorHandler));
            if (!userHeaderGroups.isEmpty()) {
                groupConfigurer.filterByName(userHeaderGroups.toArray(String[]::new))
                        .forEachClient((group, builder) -> builder.requestInterceptor(new UserHeadersInterceptor()));
            }
        };
    }

    /**
     * ss.web.client.load-balanced-groups：给这些 {@code @HttpExchange} 分组挂上 Spring Cloud 的负载均衡拦截器，
     * base-url 里写服务名即可。Spring Cloud 没有提供现成的 HTTP 服务分组集成，所以在这里补上。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(DeferringLoadBalancerInterceptor.class)
    static class LoadBalancedGroupsConfiguration {

        @Bean
        RestClientHttpServiceGroupConfigurer ssLoadBalancedGroupConfigurer(
                SsWebProperties properties, ObjectProvider<DeferringLoadBalancerInterceptor> interceptor) {
            List<String> groups = properties.getClient().getLoadBalancedGroups();
            return groupConfigurer -> {
                if (groups.isEmpty()) {
                    return;
                }
                DeferringLoadBalancerInterceptor loadBalancer = interceptor.getIfAvailable();
                if (loadBalancer == null) {
                    throw new IllegalStateException("ss.web.client.load-balanced-groups 配置了 " + groups
                            + "，但没有负载均衡拦截器：请引入 spring-cloud-starter-loadbalancer，并配置服务发现（如 Consul）");
                }
                NoInstanceAsIoErrorInterceptor wrapped = new NoInstanceAsIoErrorInterceptor(loadBalancer);
                groupConfigurer.filterByName(groups.toArray(String[]::new))
                        .forEachClient((group, builder) -> builder.requestInterceptor(wrapped));
            };
        }
    }

    /**
     * 配置了 load-balanced-groups 却没有引入 Spring Cloud：启动时就失败，而不是悄悄地把服务名当主机名去解析
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnMissingClass("org.springframework.cloud.client.loadbalancer.DeferringLoadBalancerInterceptor")
    static class LoadBalancedGroupsWithoutSpringCloud {

        @Bean
        InitializingBean ssLoadBalancedGroupsCheck(SsWebProperties properties) {
            return () -> {
                List<String> groups = properties.getClient().getLoadBalancedGroups();
                if (!groups.isEmpty()) {
                    throw new IllegalStateException("ss.web.client.load-balanced-groups 配置了 " + groups
                            + "，但类路径上没有 Spring Cloud：请引入 spring-cloud-starter-loadbalancer，并配置服务发现（如 Consul）");
                }
            };
        }
    }
}
