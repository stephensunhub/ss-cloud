package men.sbb8.web.starter.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * web starter 配置，前缀 {@code ss.web}，全部有默认值，引入依赖即可使用
 *
 * @author Stephen
 */
@Getter
@ConfigurationProperties(prefix = "ss.web")
public class SsWebProperties {

    /**
     * 错误响应的格式：resp（默认，沿用 Resp 信封）或 problem-detail（RFC 9457）。
     * 成功响应由控制器自己决定怎么返回（用 Resp.ok(...) 包装，或直接返回对象），starter 不会替它包装
     */
    @Setter
    private ErrorFormat errorFormat = ErrorFormat.RESP;

    /**
     * OpenAPI 文档信息，title 缺省取 spring.application.name
     */
    private final OpenApi openApi = new OpenApi();

    /**
     * 网关透传的用户上下文（UserInfo）
     */
    private final UserInfo userInfo = new UserInfo();

    /**
     * HTTP Service Client（@HttpExchange 声明式客户端）
     */
    private final Client client = new Client();

    @Setter
    @Getter
    public static class OpenApi {

        /**
         * 接口文档标题，缺省取 spring.application.name
         */
        private String title;

        /**
         * 接口文档描述，缺省为 "<标题> 接口文档"
         */
        private String description;

        /**
         * 接口文档版本号
         */
        private String version = "v1";

    }

    @Setter
    @Getter
    public static class UserInfo {

        /**
         * 为 true 时，控制器声明 UserInfo 参数而请求缺少 X-User-Id 头，返回 401
         */
        private boolean required = false;

    }

    @Setter
    @Getter
    public static class Client {

        /**
         * 透传分组名，取这个值表示所有 {@code @HttpExchange} 客户端分组都透传
         */
        public static final String ALL = "all";

        /**
         * 需要透传当前用户请求头（X-User-Id 等）的客户端分组名；默认 {@code all}，所有分组都透传。
         * 会把用户标识发给该分组的下游，所以调用第三方接口的分组不要透传：此时列出需要透传的内部服务分组，不要用 {@code all}。
         * {@code all} 是保留值，不要用作分组名
         */
        private List<String> propagateUserHeaders = List.of(ALL);

        public boolean propagatesTo(String group) {
            return propagateUserHeaders.stream()
                    .anyMatch(name -> ALL.equalsIgnoreCase(name.trim()) || name.trim().equals(group));
        }

    }

    public enum ErrorFormat {

        /**
         * 与成功响应共用的 Resp 信封：{code, msg, traceId, data}
         */
        RESP,

        /**
         * RFC 9457 Problem Details：application/problem+json，业务码在扩展成员 code
         */
        PROBLEM_DETAIL
    }
}
