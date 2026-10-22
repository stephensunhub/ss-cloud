package men.sbb8.grpc.core.error;

import io.grpc.Metadata;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.ProtoUtils;
import men.sbb8.grpc.core.proto.ErrorDetail;

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

    public static final int MAX_PARAMS = 20;

    public static final int MAX_PARAM_LENGTH = 256;

    private static final Pattern PARAM = Pattern.compile("\\{\\{(\\w+)}}");

    private ErrorDetails() {
    }

    public static ErrorDetail toDetail(BusinessCode code, Map<String, ?> params) {
        Map<String, String> bounded = bound(params);
        return ErrorDetail.newBuilder()
                .setCode(code.code())
                .setMsg(render(code.msg(), bounded))
                .putAllParams(bounded)
                .build();
    }

    /**
     * 错误详情随 trailers 返回，而客户端默认只接受 8KB 的 metadata，超出会让调用方收到 RESOURCE_EXHAUSTED。
     * 所以参数转成字符串后做限制：最多 {@value #MAX_PARAMS} 个，每个值最多 {@value #MAX_PARAM_LENGTH} 个字符（超出截断，以 … 结尾）。
     */
    public static Map<String, String> bound(Map<String, ?> params) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : params.entrySet()) {
            if (result.size() >= MAX_PARAMS) {
                break;
            }
            String value = String.valueOf(entry.getValue());
            result.put(entry.getKey(), value.length() <= MAX_PARAM_LENGTH
                    ? value : value.substring(0, MAX_PARAM_LENGTH - 1) + '…');
        }
        return result;
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
