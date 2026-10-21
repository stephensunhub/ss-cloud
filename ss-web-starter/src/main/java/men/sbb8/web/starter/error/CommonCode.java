package men.sbb8.web.starter.error;


import lombok.AllArgsConstructor;
import lombok.Getter;

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

    SYSTEM_ERROR("系统异常"),
    ;

    private final String msg;

}
