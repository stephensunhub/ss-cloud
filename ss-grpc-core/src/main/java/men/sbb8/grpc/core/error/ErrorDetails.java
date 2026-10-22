package men.sbb8.grpc.core.error;

import io.grpc.Metadata;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.ProtoUtils;
import men.sbb8.grpc.core.proto.ErrorDetail;
import org.slf4j.MDC;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 业务错误与 gRPC 状态之间的转换：错误详情 {@link ErrorDetail} 放在 trailers 里随状态一起返回。
 *
 * @author Stephen
 */
public final class ErrorDetails {

    public static final Metadata.Key<ErrorDetail> ERROR_DETAIL_KEY =
            ProtoUtils.keyForProto(ErrorDetail.getDefaultInstance());

    /**
     * 参数个数上限，多余的丢弃
     */
    public static final int MAX_PARAMS = 20;

    /**
     * 单个参数值的上限，按 UTF-8 字节计，超出截断并以 … 结尾
     */
    public static final int MAX_PARAM_BYTES = 256;

    /**
     * 全部参数值合计的上限，按 UTF-8 字节计，用完后的参数丢弃。
     * 错误详情在 trailers 里（二进制 metadata 经 base64，约膨胀三分之一），同样的消息还会进 grpc-message 头
     * （非 ASCII 字节做百分号编码，膨胀 3 倍），而客户端默认只接受 8KB 的 metadata，所以总量要压得比较小。
     * 数值由 MetadataSizeLimitTest 在真实网络上按最坏情况（中文、全部参数都出现在消息里）校准
     */
    public static final int MAX_TOTAL_PARAM_BYTES = 1024;

    private static final String ELLIPSIS = "…";

    private static final int ELLIPSIS_BYTES = 3;

    private static final Pattern PARAM = Pattern.compile("\\{\\{(\\w+)}}");

    private ErrorDetails() {
    }

    public static ErrorDetail toDetail(BusinessCode code, Map<String, ?> params) {
        Map<String, String> bounded = bound(params);
        ErrorDetail.Builder detail = ErrorDetail.newBuilder()
                .setCode(code.code())
                .setMsg(render(code.msg(), bounded))
                .putAllParams(bounded);
        // Micrometer Tracing 桥接会把 traceId 写入 MDC，避免对追踪实现的硬依赖
        String traceId = MDC.get("traceId");
        if (traceId != null) {
            detail.setTraceId(traceId);
        }
        return detail.build();
    }

    /**
     * 限制参数的数量和大小：最多 {@value #MAX_PARAMS} 个；每个值转成字符串后最多 {@value #MAX_PARAM_BYTES} 个 UTF-8 字节，
     * 全部合计最多 {@value #MAX_TOTAL_PARAM_BYTES} 字节。超出的值按字符（码点）截断，以 … 结尾，不会截断在字符中间；
     * 总量用完后的参数丢弃。原因见 {@link #MAX_TOTAL_PARAM_BYTES}。
     */
    public static Map<String, String> bound(Map<String, ?> params) {
        Map<String, String> result = new LinkedHashMap<>();
        int remaining = MAX_TOTAL_PARAM_BYTES;
        for (Map.Entry<String, ?> entry : params.entrySet()) {
            if (result.size() >= MAX_PARAMS || remaining < ELLIPSIS_BYTES + 1) {
                break;
            }
            String value = truncateUtf8(String.valueOf(entry.getValue()), Math.min(MAX_PARAM_BYTES, remaining));
            result.put(entry.getKey(), value);
            remaining -= utf8Length(value);
        }
        return result;
    }

    /**
     * 截到不超过 maxBytes 个 UTF-8 字节；被截断时以 … 结尾（结果同样不超过 maxBytes）
     */
    static String truncateUtf8(String value, int maxBytes) {
        if (utf8Length(value) <= maxBytes) {
            return value;
        }
        int budget = maxBytes - ELLIPSIS_BYTES;
        int bytes = 0;
        int end = 0;
        while (end < value.length()) {
            int codePoint = value.codePointAt(end);
            int length = utf8Length(codePoint);
            if (bytes + length > budget) {
                break;
            }
            bytes += length;
            end += Character.charCount(codePoint);
        }
        return value.substring(0, end) + ELLIPSIS;
    }

    private static int utf8Length(String value) {
        int bytes = 0;
        for (int i = 0; i < value.length(); i += Character.charCount(value.codePointAt(i))) {
            bytes += utf8Length(value.codePointAt(i));
        }
        return bytes;
    }

    private static int utf8Length(int codePoint) {
        if (codePoint < 0x80) {
            return 1;
        }
        if (codePoint < 0x800) {
            return 2;
        }
        return codePoint < 0x10000 ? 3 : 4;
    }

    public static StatusException toStatusException(BusinessCode code, Map<String, ?> params) {
        ErrorDetail detail = toDetail(code, params);
        return code.status().withDescription(detail.getMsg()).asException(trailers(detail));
    }

    public static StatusRuntimeException toStatusRuntimeException(BusinessCode code, Map<String, ?> params) {
        ErrorDetail detail = toDetail(code, params);
        return code.status().withDescription(detail.getMsg()).asRuntimeException(trailers(detail));
    }

    public static Optional<ErrorDetail> find(Metadata trailers) {
        return Optional.ofNullable(trailers).map(metadata -> metadata.get(ERROR_DETAIL_KEY));
    }

    public static Optional<ErrorDetail> find(StatusRuntimeException e) {
        return find(e.getTrailers());
    }

    public static Optional<ErrorDetail> find(StatusException e) {
        return find(e.getTrailers());
    }

    public static Metadata trailers(ErrorDetail detail) {
        Metadata metadata = new Metadata();
        metadata.put(ERROR_DETAIL_KEY, detail);
        return metadata;
    }

    /**
     * 把消息模板里的 {@code {{name}}} 替换为参数值，缺失的参数替换为空串
     */
    public static String render(String template, Map<String, ?> params) {
        Matcher matcher = PARAM.matcher(template);
        StringBuilder sb = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            sb.append(template, last, matcher.start());
            Object value = params.get(matcher.group(1));
            sb.append(value == null ? "" : value);
            last = matcher.end();
        }
        return sb.append(template, last, template.length()).toString();
    }
}
