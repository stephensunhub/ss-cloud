package men.sbb8.web.starter.dto.resp;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import men.sbb8.web.starter.error.BusinessException;
import men.sbb8.web.starter.error.CommonCode;
import men.sbb8.web.starter.utils.RequestTime;
import org.slf4j.MDC;

/**
 * 响应体
 *
 * @author Stephen
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

    @Schema(title = "服务收到请求的时间，毫秒时间戳")
    private Long requestTime;

    @Schema(title = "服务生成响应的时间，毫秒时间戳")
    private Long responseTime;


    public Resp() {
    }

    private Resp(T data, String code, String msg) {
        this.data = data;
        this.code = code;
        this.msg = msg;
        // Micrometer Tracing / OpenTelemetry 桥接会把 traceId 写入 MDC，避免对 OTel 的硬依赖
        this.traceId = MDC.get("traceId");
        // 请求到达时间由 RequestTimeFilter 记在请求属性里；不在请求线程里构造时两个时间相同
        long now = System.currentTimeMillis();
        this.responseTime = now;
        this.requestTime = RequestTime.currentOr(now);
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

    @JsonIgnore
    public boolean isSuccess() {
        return CommonCode.SUCCESS.name().equalsIgnoreCase(this.code);
    }

    /**
     * 取出 data，响应不是成功码时抛 {@link BusinessException}。
     * 用于 HTTP Service Client 接口返回 {@code Resp<T>} 之后的解包
     */
    public T unwrap() {
        if (isFail()) {
            throw new BusinessException(code, msg);
        }
        return data;
    }

    @JsonIgnore
    public boolean isFail() {
        return !isSuccess();
    }

}
