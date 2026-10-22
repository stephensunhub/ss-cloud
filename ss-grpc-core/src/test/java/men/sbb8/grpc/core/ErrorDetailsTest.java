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

    private static int bytes(String value) {
        return value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    }

    private static String param(String value) {
        return ErrorDetails.toDetail(CommonCode.PARAMS_ERROR, Map.of("field", value)).getParamsMap().get("field");
    }

    @Test
    void asciiParamIsTruncatedToTheByteLimit() {
        String value = param("x".repeat(10_000));
        assertEquals(ErrorDetails.MAX_PARAM_BYTES, bytes(value));
        assertTrue(value.endsWith("…"));
    }

    @Test
    void chineseParamIsBoundedByBytesNotCharacters() {
        // 每个汉字 3 个字节：按字符数算会放进 3 倍的字节
        String value = param("汉".repeat(1_000));
        assertTrue(bytes(value) <= ErrorDetails.MAX_PARAM_BYTES, "实际 " + bytes(value) + " 字节");
        assertTrue(value.endsWith("…"));
        assertTrue(value.chars().noneMatch(c -> c == '\uFFFD'));
    }

    /**
     * 编码成 UTF-8 再解码能还原：说明没有残缺的代理对（残缺的会被编码成 ?）
     */
    private static boolean isWellFormed(String value) {
        return new String(value.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                java.nio.charset.StandardCharsets.UTF_8).equals(value);
    }

    @Test
    void truncationNeverSplitsASurrogatePair() {
        // emoji 占 2 个 char、4 个字节；前面垫 0~3 个 ASCII 字符，让截断点落在 emoji 内部的各种位置
        for (int padding = 0; padding < 4; padding++) {
            String value = param("a".repeat(padding) + "😀".repeat(200));
            assertTrue(isWellFormed(value), "垫 " + padding + " 个字符");
            assertTrue(bytes(value) <= ErrorDetails.MAX_PARAM_BYTES);
            assertTrue(value.endsWith("…"));
        }
    }

    @Test
    void shortParamIsKeptAsIs() {
        assertEquals("abc", param("abc"));
        assertEquals("汉字", param("汉字"));
    }

    @Test
    void paramCountIsBounded() {
        Map<String, Object> many = new java.util.LinkedHashMap<>();
        for (int i = 0; i < 100; i++) {
            many.put("k" + i, i);
        }
        assertEquals(ErrorDetails.MAX_PARAMS, ErrorDetails.toDetail(CommonCode.PARAMS_ERROR, many).getParamsCount());
    }

    @Test
    void totalParamSizeIsBounded() {
        Map<String, Object> big = new java.util.LinkedHashMap<>();
        for (int i = 0; i < 20; i++) {
            big.put("k" + i, "y".repeat(1_000));
        }
        Map<String, String> bounded = ErrorDetails.bound(big);
        int total = bounded.values().stream().mapToInt(ErrorDetailsTest::bytes).sum();
        assertTrue(total <= ErrorDetails.MAX_TOTAL_PARAM_BYTES, "合计 " + total + " 字节");
        assertTrue(bounded.size() < ErrorDetails.MAX_PARAMS, "总量用完后的参数应被丢弃");
        assertTrue(bounded.size() >= 1);
    }

    @Test
    void renderedMessageUsesBoundedParams() {
        BusinessCode template = new BusinessCode() {
            public io.grpc.Status status() { return Status.INVALID_ARGUMENT; }
            public String code() { return "T"; }
            public String msg() { return "bad {{v}}"; }
        };
        String message = new GrpcBusinessException(template, Map.of("v", "y".repeat(5000))).getMessage();
        assertEquals("bad " + "y".repeat(ErrorDetails.MAX_PARAM_BYTES - 3) + "…", message);
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
