package men.sbb8.web.starter.error;


import org.springframework.http.HttpStatus;

/**
 * 业务异常
 *
 * @author Stephen
 */
public class BusinessException extends RuntimeException {

    public HttpStatus httpStatus;
    public String code;
    public String msg;


    public BusinessException(Exception e) {
        super(e);
    }


    public BusinessException(HttpStatus httpStatus, BusinessCode code, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.code = code.name();
        this.msg = message;
    }

    public BusinessException(BusinessCode code, String message) {
        super(message);
        this.code = code.name();
        this.msg = message;
        this.httpStatus = HttpStatus.OK;
    }

    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
        this.msg = message;
        this.httpStatus = HttpStatus.OK;
    }

    public BusinessException(String msg) {
        super(msg);
        this.code = CommonCode.SYSTEM_ERROR.name();
        this.msg = msg;
        this.httpStatus = HttpStatus.OK;
    }

    public BusinessException(BusinessCode code) {
        super(code.getMsg());
        this.code = code.name();
        this.msg = code.getMsg();
        this.httpStatus = HttpStatus.OK;
    }

    public BusinessException(BusinessCode code, HttpStatus aHttpStatus) {
        super(code.getMsg());
        this.code = code.name();
        this.msg = code.getMsg();
        this.httpStatus = aHttpStatus;
    }

    public BusinessException() {
        super(CommonCode.SYSTEM_ERROR.getMsg());
        this.code = CommonCode.SYSTEM_ERROR.name();
        this.msg = CommonCode.SYSTEM_ERROR.getMsg();
        this.httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
    }
}