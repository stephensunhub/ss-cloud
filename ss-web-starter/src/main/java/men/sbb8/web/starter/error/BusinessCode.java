package men.sbb8.web.starter.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * 业务代码。
 * {@link #getHttpStatus()} 是该业务码默认对应的 HTTP 状态码，默认 200（业务失败靠 code 表达）；
 * {@link BusinessException} 抛出时用它，也可以在抛出时显式指定覆盖。
 *
 * @author Stephen
 */
public interface BusinessCode {


    String name();

    String getMsg();

    default HttpStatusCode getHttpStatus() {
        return HttpStatus.OK;
    }

}
