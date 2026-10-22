package men.sbb8.grpc.starter.error;

import io.grpc.Metadata;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.ProtoUtils;
import men.sbb8.grpc.starter.proto.ErrorDetail;

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

    private static final Pattern PARAM = Pattern.compile("\\{\\{(\\w+)}}");

    private ErrorDetails() {
    }

    public static ErrorDetail toDetail(BusinessCode code, Map<String, ?> params) {
        Map<String, String> stringParams = new LinkedHashMap<>();
        params.forEach((key, value) -> stringParams.put(key, String.valueOf(value)));
        return ErrorDetail.newBuilder()
                .setCode(code.code())
                .setMsg(render(code.msg(), params))
                .putAllParam(stringParams)
                .build();
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
