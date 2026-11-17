package men.sbb8.web.starter.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * 业务异常，由 {@code GlobalExceptionHandler} 转成 {@code Resp}。
 * HTTP 状态码取业务码的 {@link BusinessCode#getHttpStatus()}（默认 200，业务失败通过 code 表达），需要时在构造时显式指定覆盖；
 * 没有枚举的 {@code (String code, String message)} 固定 200。
 * 业务码优先用 {@link BusinessCode}（枚举）；没有对应枚举时用 {@code (String code, String message)}。
 * 没有"只传消息"的构造：不带业务码的失败是系统异常（500），用无参构造或包装原因的构造。
 *
 * @author Stephen
 */
@Getter
public class BusinessException extends RuntimeException {

    private final HttpStatusCode httpStatus;
    private final String code;
    private final String msg;

    /**
     * 包装非预期的异常：按系统异常处理（500），不向调用方暴露原因的内容
     */
    public BusinessException(Throwable cause) {
        super(CommonCode.SYSTEM_ERROR.getMsg(), cause);
        this.httpStatus = CommonCode.SYSTEM_ERROR.getHttpStatus();
        this.code = CommonCode.SYSTEM_ERROR.name();
        this.msg = CommonCode.SYSTEM_ERROR.getMsg();
    }

    public BusinessException(HttpStatusCode httpStatus, BusinessCode code, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.code = code.name();
        this.msg = message;
    }

    public BusinessException(BusinessCode code, String message) {
        this(code.getHttpStatus(), code, message);
    }

    public BusinessException(String code, String message) {
        super(message);
        this.httpStatus = HttpStatus.OK;
        this.code = code;
        this.msg = message;
    }

    public BusinessException(BusinessCode code) {
        this(code.getHttpStatus(), code, code.getMsg());
    }

    public BusinessException(BusinessCode code, HttpStatusCode httpStatus) {
        this(httpStatus, code, code.getMsg());
    }

    public BusinessException() {
        this(CommonCode.SYSTEM_ERROR);
    }
}
