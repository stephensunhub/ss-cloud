package men.sbb8.web.starter.dto.resp;

import com.hkx.exchange.common.error.CommonCode;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/**
 * 响应体
 */
@Getter
@Schema(description = "统一响应体")
public class Resp<T> {

    @Schema(title = "响应体")
    private T data;

    @Schema(title = "响应码")
    private String code;

    @Schema(title = "响应消息")
    private String msg;

    @Schema(title = "分布式日志ID")
    private String traceId;


    public Resp() {
    }

    private Resp(T data, String code, String msg) {
        this.data = data;
        this.code = code;
        this.msg = msg;
        SpanContext ctx = Span.current().getSpanContext();
        this.traceId = ctx.isValid() ? ctx.getTraceId() : null;
    }

    /**
     * 成功
     *
     * @param t   返回内容
     * @param <T> 返回类型
     * @return 返回结构体
     */
    public static <T> Resp<T> ok(T t) {
        return new Resp<>(t, CommonCode.SUCCESS.name(), CommonCode.SUCCESS.getMsg());
    }

    public static Resp<Void> fail(String code) {
        return new Resp<>(null, code, "");
    }

    public static Resp<Void> fail(String code, String msg) {
        return new Resp<>(null, code, msg);
    }

    public static <T> Resp<T> fail(T t, String rawCode, String msg) {
        return new Resp<>(t, rawCode, msg);
    }

    public boolean isSuccess() {
        return CommonCode.SUCCESS.name().equalsIgnoreCase(this.code);
    }

    public boolean isFail() {
        return !isSuccess();
    }

}
