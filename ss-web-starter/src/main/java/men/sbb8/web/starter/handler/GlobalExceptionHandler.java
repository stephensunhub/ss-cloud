package men.sbb8.web.starter.handler;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import men.sbb8.web.starter.dto.resp.Resp;
import men.sbb8.web.starter.error.BusinessException;
import men.sbb8.web.starter.error.CommonCode;
import men.sbb8.web.starter.error.RemoteCallException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.Objects;

/**
 * 全局异常处理，统一输出 {@link Resp}。
 * 业务异常使用其自带 HTTP 状态码（默认 200），参数错误 400，未知异常 500 且不向前端暴露异常详情。
 * 优先级最低：应用自己的 {@code @RestControllerAdvice} 能先于它处理异常，从而覆盖这里的约定。
 *
 * @author Stephen
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Resp<Void>> handleBusiness(BusinessException e) {
        log.warn("业务异常: code={}, msg={}", e.getCode(), e.getMsg());
        if (e.getCause() != null) {
            log.error("业务异常的原始原因", e.getCause());
        }
        return ResponseEntity.status(e.getHttpStatus()).body(Resp.fail(e.getCode(), e.getMsg()));
    }

    /**
     * 下游返回 4xx 且带业务 Resp 时透传其状态码、code、msg；其余统一 502，不暴露下游细节：
     * 5xx、非 Resp 响应，以及框架自己生成的 404/405/415 等（code 是状态名，如 NOT_FOUND，说明是我方调用路径或方式错了，不是业务结果）
     */
    @ExceptionHandler(RemoteCallException.class)
    public ResponseEntity<Resp<Void>> handleRemoteCall(RemoteCallException e) {
        if (e.getStatus().is4xxClientError() && e.getCode() != null && !isFrameworkError(e)) {
            log.warn("下游业务错误: {} code={}, msg={}", e.getMessage(), e.getCode(), e.getRemoteMsg());
            return ResponseEntity.status(e.getStatus()).body(Resp.fail(e.getCode(), e.getRemoteMsg()));
        }
        log.error("下游调用失败: {}, body={}", e.getMessage(), StringUtils.truncate(e.getBody(), 500));
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Resp.fail(HttpStatus.BAD_GATEWAY.name(), "下游服务异常"));
    }

    private static boolean isFrameworkError(RemoteCallException e) {
        HttpStatus status = HttpStatus.resolve(e.getStatus().value());
        return status != null && status.name().equals(e.getCode());
    }

    /**
     * @RequestBody / @ModelAttribute 校验与绑定失败
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<Resp<Void>> handleBind(BindException e) {
        log.warn("参数绑定/校验异常: {}", e.getMessage());
        ObjectError error = e.getBindingResult().getAllErrors().stream().findFirst().orElse(null);
        if (error instanceof FieldError fieldError) {
            if ("typeMismatch".equalsIgnoreCase(fieldError.getCode())) {
                return badRequest(CommonCode.PARAMS_TRANSFER_ERROR, "参数格式(" + fieldError.getField() + ")错误");
            }
            return badRequest(CommonCode.PARAMS_ERROR, fieldError.getDefaultMessage());
        }
        return badRequest(CommonCode.PARAMS_ERROR, error == null ? null : error.getDefaultMessage());
    }

    /**
     * 方法参数上的校验（@Validated 级联到参数、@Min 等）
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<Resp<Void>> handleMethodValidation(HandlerMethodValidationException e) {
        log.warn("方法参数校验异常: {}", e.getMessage());
        String msg = e.getAllErrors().stream()
                .map(error -> error.getDefaultMessage())
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        return badRequest(CommonCode.PARAMS_ERROR, msg);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Resp<Void>> handleConstraintViolation(ConstraintViolationException e) {
        log.warn("约束校验异常: {}", e.getMessage());
        String msg = e.getConstraintViolations().stream()
                .map(v -> v.getMessage())
                .findFirst()
                .orElse(null);
        return badRequest(CommonCode.PARAMS_ERROR, msg);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Resp<Void>> handleMissingParameter(MissingServletRequestParameterException e) {
        log.warn("缺少请求参数: {}", e.getParameterName());
        return badRequest(CommonCode.PARAMS_LOSS, "请求参数 " + e.getParameterName() + " 不能为空");
    }

    @ExceptionHandler(TypeMismatchException.class)
    public ResponseEntity<Resp<Void>> handleTypeMismatch(TypeMismatchException e) {
        log.warn("参数类型转换异常: {}", e.getMessage());
        Class<?> required = e.getRequiredType();
        String msg = "参数(" + e.getPropertyName() + ")类型错误" + (required == null ? "" : ",必须(" + required.getSimpleName() + ")");
        return badRequest(CommonCode.PARAMS_TRANSFER_ERROR, msg);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Resp<Void>> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体无法解析: {}", e.getMessage());
        return badRequest(CommonCode.PARAMS_ERROR, "请求体格式错误");
    }

    /**
     * 兜底。Spring MVC 自带的 404/405/415 等异常实现了 {@link ErrorResponse}，沿用其状态码；其余按 500 处理
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Resp<Void>> handleException(Exception e) {
        if (e instanceof ErrorResponse errorResponse) {
            HttpStatusCode status = errorResponse.getStatusCode();
            log.warn("请求异常: status={}, {}", status, e.getMessage());
            HttpStatus resolved = HttpStatus.resolve(status.value());
            String code = resolved == null ? CommonCode.SYSTEM_ERROR.name() : resolved.name();
            return ResponseEntity.status(status).body(Resp.fail(code, errorResponse.getBody().getDetail()));
        }
        log.error("未知异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Resp.fail(CommonCode.SYSTEM_ERROR.name(), CommonCode.SYSTEM_ERROR.getMsg()));
    }

    private static ResponseEntity<Resp<Void>> badRequest(CommonCode code, String msg) {
        return ResponseEntity.badRequest().body(Resp.fail(code.name(), msg == null ? code.getMsg() : msg));
    }
}
