package men.sbb8.grpc.core;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import men.sbb8.grpc.core.error.BusinessCode;
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

    @Test
    void oversizedParamsAreBounded() {
        String huge = "x".repeat(10_000);
        ErrorDetail detail = ErrorDetails.toDetail(CommonCode.PARAMS_ERROR, Map.of("field", huge));
        String value = detail.getParamsMap().get("field");
        assertEquals(ErrorDetails.MAX_PARAM_LENGTH, value.length());
        assertTrue(value.endsWith("…"));

        Map<String, Object> many = new java.util.LinkedHashMap<>();
        for (int i = 0; i < 100; i++) {
            many.put("k" + i, i);
        }
        assertEquals(ErrorDetails.MAX_PARAMS, ErrorDetails.toDetail(CommonCode.PARAMS_ERROR, many).getParamsCount());
        // 整个详情放进 trailers 后远小于客户端默认的 8KB metadata 上限
        assertTrue(ErrorDetails.toDetail(CommonCode.PARAMS_ERROR, many).getSerializedSize() < 8192);
    }

    @Test
    void renderedMessageUsesBoundedParams() {
        BusinessCode template = new BusinessCode() {
            public io.grpc.Status status() { return Status.INVALID_ARGUMENT; }
            public String code() { return "T"; }
            public String msg() { return "bad {{v}}"; }
        };
        String message = new GrpcBusinessException(template, Map.of("v", "y".repeat(5000))).getMessage();
        assertEquals("bad ".length() + ErrorDetails.MAX_PARAM_LENGTH, message.length());
    }

    @Test
    void exceptionKeepsItsOwnCopyOfParams() {
        Map<String, Object> params = new java.util.HashMap<>();
        params.put("a", 1);
        GrpcBusinessException e = new GrpcBusinessException(CommonCode.PARAMS_ERROR, params);
        params.put("b", 2);
        assertEquals(Map.of("a", 1), e.getParams());
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class, () -> e.getParams().clear());
    }
}
