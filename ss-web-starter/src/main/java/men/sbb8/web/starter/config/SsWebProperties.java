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
     * 选 problem-detail 时，成功响应是否仍包装由 response-wrap.enabled 单独控制，
     * 想要完全符合标准的 REST 风格，把它设为 false
     */
    @Setter
    private ErrorFormat errorFormat = ErrorFormat.RESP;

    /**
     * 统一响应包装
     */
    private final ResponseWrap responseWrap = new ResponseWrap();

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
    public static class ResponseWrap {

        /**
         * 是否把控制器返回值包装为 Resp，方法或类上标注 @RawResponse 可单独跳过
         */
        private boolean enabled = true;

    }

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
         * 需要透传当前用户请求头（X-User-Id 等）的客户端分组名，仅用于调用内部服务。
         * 默认不透传，避免把用户标识发给第三方接口
         */
        private List<String> propagateUserHeaders = List.of();

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
