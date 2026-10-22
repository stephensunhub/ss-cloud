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

        private String title;

        private String description;

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
}
