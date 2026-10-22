package men.sbb8.web.starter.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * 业务异常，由 {@code GlobalExceptionHandler} 转成 {@code Resp}。
 * HTTP 状态码默认 200，业务失败通过 code 表达；需要时显式指定。
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
        this.httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
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
        this(HttpStatus.OK, code, message);
    }

    public BusinessException(String code, String message) {
        super(message);
        this.httpStatus = HttpStatus.OK;
        this.code = code;
        this.msg = message;
    }

    public BusinessException(String msg) {
        this(CommonCode.SYSTEM_ERROR.name(), msg);
    }

    public BusinessException(BusinessCode code) {
        this(HttpStatus.OK, code, code.getMsg());
    }

    public BusinessException(BusinessCode code, HttpStatusCode httpStatus) {
        this(httpStatus, code, code.getMsg());
    }

    public BusinessException() {
        this(HttpStatus.INTERNAL_SERVER_ERROR, CommonCode.SYSTEM_ERROR, CommonCode.SYSTEM_ERROR.getMsg());
    }
}
