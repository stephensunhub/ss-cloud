package men.sbb8.grpc.core;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import men.sbb8.grpc.core.error.CommonCode;
import men.sbb8.grpc.core.error.ErrorDetails;
import men.sbb8.grpc.core.error.GrpcBusinessException;
import men.sbb8.grpc.core.error.GrpcRemoteException;
import men.sbb8.grpc.core.proto.ErrorDetail;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ErrorDetailsTest {

    @Test
    void renderTemplate() {
        assertEquals("a-1-", ErrorDetails.render("a-{{x}}-{{missing}}", Map.of("x", 1)));
        assertEquals("no params", ErrorDetails.render("no params", Map.of()));
    }

    @Test
    void statusCarriesDetailInTrailers() {
        StatusRuntimeException e = ErrorDetails.toStatusRuntimeException(CommonCode.PARAMS_ERROR, Map.of("field", "name"));
        assertEquals(Status.Code.INVALID_ARGUMENT, e.getStatus().getCode());
        ErrorDetail detail = ErrorDetails.find(e).orElseThrow();
        assertEquals("PARAMS_ERROR", detail.getCode());
        assertEquals("参数错误", detail.getMsg());
        assertEquals("name", detail.getParamsMap().get("field"));
        assertEquals("参数错误", e.getStatus().getDescription());
    }

    @Test
    void businessExceptionRendersMessage() {
        GrpcBusinessException e = new GrpcBusinessException(CommonCode.SYSTEM_ERROR);
        assertEquals("系统异常", e.getMessage());
        assertEquals(CommonCode.SYSTEM_ERROR, e.getCode());
    }

    @Test
    void remoteExceptionExposesCodeAndMessageWhenDetailPresent() {
        GrpcRemoteException withDetail = new GrpcRemoteException(
                ErrorDetails.toStatusRuntimeException(CommonCode.PERMISSION_DENIED, Map.of()));
        assertEquals("PERMISSION_DENIED", withDetail.getCode());
        assertEquals("无权限", withDetail.getRemoteMsg());
        assertEquals(Status.Code.PERMISSION_DENIED, withDetail.getStatus().getCode());

        GrpcRemoteException plain = new GrpcRemoteException(Status.UNAVAILABLE.asRuntimeException());
        assertNull(plain.getCode());
        assertNull(plain.getRemoteMsg());
        assertTrue(plain.getMessage().contains("UNAVAILABLE"));
    }
}
