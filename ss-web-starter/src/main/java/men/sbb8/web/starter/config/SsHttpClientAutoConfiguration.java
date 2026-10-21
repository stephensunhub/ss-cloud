package men.sbb8.web.starter.config;

import men.sbb8.web.starter.client.RemoteErrorHandler;
import men.sbb8.web.starter.client.UserHeadersInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * HTTP Service Client（@HttpExchange）的统一约定：
 * <ul>
 *     <li>所有 RestClient 的非 2xx 响应统一转成 RemoteCallException</li>
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
    public RestClientCustomizer ssRestClientCustomizer(JsonMapper jsonMapper) {
        return builder -> builder.defaultStatusHandler(HttpStatusCode::isError, new RemoteErrorHandler(jsonMapper));
    }

    @Bean
    public RestClientHttpServiceGroupConfigurer ssUserHeadersGroupConfigurer(SsWebProperties properties) {
        List<String> groups = properties.getClient().getPropagateUserHeaders();
        return groupConfigurer -> {
            if (!groups.isEmpty()) {
                groupConfigurer.filterByName(groups.toArray(String[]::new))
                        .forEachClient((group, builder) -> builder.requestInterceptor(new UserHeadersInterceptor()));
            }
        };
    }
}
