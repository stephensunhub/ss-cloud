package men.sbb8.web.starter.error;


import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * 通用状态码
 *
 * @author Stephen
 */
@Getter
@AllArgsConstructor
public enum CommonCode implements BusinessCode {


    /**
     * 通用状态码禁止添加具有业务含义的枚举
     */
    SUCCESS("成功"),

    PARAMS_LOSS("缺失参数"),
    PARAMS_ERROR("参数错误"),
    PARAMS_TRANSFER_ERROR("参数类型错误"),

    UNAUTHORIZED("未登录或身份无效", HttpStatus.UNAUTHORIZED),

    SYSTEM_ERROR("系统异常", HttpStatus.INTERNAL_SERVER_ERROR),
    ;

    private final String msg;

    /**
     * 默认对应的 HTTP 状态码；没写的为 200
     */
    private final HttpStatusCode httpStatus;

    CommonCode(String msg) {
        this(msg, HttpStatus.OK);
    }

}
