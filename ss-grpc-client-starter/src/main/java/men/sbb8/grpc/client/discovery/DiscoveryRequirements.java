package men.sbb8.grpc.client.discovery;

import org.springframework.util.ClassUtils;

/**
 * 服务发现的前置条件检查，在启动时就报出明确的原因，而不是等到第一次查询时抛 NullPointerException。
 * <p>
 * Spring Cloud Consul 用 {@code RestClient} 解析 Consul 的 JSON，类路径上没有 Jackson 3 时退回 Gson；
 * Gson 按字段名精确匹配，对不上 Consul 返回的 {@code "Service"}，{@code getInstances} 每次都会 NullPointerException。
 * Gson 由 {@code spring-cloud-starter-consul} 带入，Jackson 不会。
 *
 * @author Stephen
 */
final class DiscoveryRequirements {

    static final String CONSUL_DISCOVERY_CLIENT = "org.springframework.cloud.consul.discovery.ConsulDiscoveryClient";
    static final String JACKSON_MAPPER = "tools.jackson.databind.json.JsonMapper";

    private DiscoveryRequirements() {
    }

    static void verify(ClassLoader classLoader) {
        verify(CONSUL_DISCOVERY_CLIENT, JACKSON_MAPPER, classLoader);
    }

    static void verify(String consulClass, String jacksonClass, ClassLoader classLoader) {
        if (ClassUtils.isPresent(consulClass, classLoader) && !ClassUtils.isPresent(jacksonClass, classLoader)) {
            throw new IllegalStateException("使用 Spring Cloud Consul 做服务发现需要 Jackson 3，但类路径上没有："
                    + "Consul 的客户端会退回 Gson，无法解析 Consul 的响应，每次查询都会 NullPointerException。"
                    + "请加依赖 tools.jackson.core:jackson-databind");
        }
    }
}
