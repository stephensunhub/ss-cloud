package men.sbb8.web.starter;

import men.sbb8.web.starter.error.BusinessCode;
import men.sbb8.web.starter.error.BusinessException;
import men.sbb8.web.starter.error.CommonCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * BusinessException 的 HTTP 状态码：显式指定的优先，否则取业务码自带的，业务码没声明的为 200
 */
class BusinessCodeHttpStatusTest {

    /** 没有覆盖 getHttpStatus 的业务码，沿用接口默认的 200 */
    enum Plain implements BusinessCode {
        CLOSED;

        @Override
        public String getMsg() {
            return "closed";
        }
    }

    enum WithStatus implements BusinessCode {
        NOT_FOUND;

        @Override
        public String getMsg() {
            return "not found";
        }

        @Override
        public HttpStatusCode getHttpStatus() {
            return HttpStatus.NOT_FOUND;
        }
    }

    @Test
    void codeWithoutStatusDefaultsTo200() {
        assertEquals(HttpStatus.OK, new BusinessException(Plain.CLOSED).getHttpStatus());
        assertEquals(HttpStatus.OK, new BusinessException(Plain.CLOSED, "custom").getHttpStatus());
    }

    @Test
    void codeStatusIsUsedWhenNotGivenExplicitly() {
        assertEquals(HttpStatus.NOT_FOUND, new BusinessException(WithStatus.NOT_FOUND).getHttpStatus());
        assertEquals(HttpStatus.NOT_FOUND, new BusinessException(WithStatus.NOT_FOUND, "custom").getHttpStatus());
    }

    @Test
    void explicitStatusOverridesTheCodeStatus() {
        assertEquals(HttpStatus.CONFLICT, new BusinessException(WithStatus.NOT_FOUND, HttpStatus.CONFLICT).getHttpStatus());
        assertEquals(HttpStatus.CONFLICT,
                new BusinessException(HttpStatus.CONFLICT, WithStatus.NOT_FOUND, "x").getHttpStatus());
    }

    @Test
    void stringCodeIsAlways200() {
        assertEquals(HttpStatus.OK, new BusinessException("SOME_CODE", "msg").getHttpStatus());
    }

    @Test
    void commonCodeStatuses() {
        assertEquals(HttpStatus.UNAUTHORIZED, new BusinessException(CommonCode.UNAUTHORIZED).getHttpStatus());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, new BusinessException(CommonCode.SYSTEM_ERROR).getHttpStatus());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, new BusinessException().getHttpStatus());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, new BusinessException(new RuntimeException()).getHttpStatus());
        // PARAMS_* 暂不改，保持 200
        assertEquals(HttpStatus.OK, new BusinessException(CommonCode.PARAMS_ERROR).getHttpStatus());
        assertEquals(HttpStatus.OK, new BusinessException(CommonCode.PARAMS_LOSS).getHttpStatus());
    }
}
