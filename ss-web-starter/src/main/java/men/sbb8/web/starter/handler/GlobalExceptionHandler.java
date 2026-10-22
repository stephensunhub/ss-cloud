package men.sbb8.web.starter.handler;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import men.sbb8.web.starter.dto.resp.Resp;
import men.sbb8.web.starter.error.BusinessException;
import men.sbb8.web.starter.error.CommonCode;
import men.sbb8.web.starter.error.RemoteCallException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import men.sbb8.web.starter.config.SsWebProperties;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 全局异常处理，统一输出 {@link Resp}。
 * 业务异常使用其自带 HTTP 状态码（默认 200），参数错误 400，未知异常 500 且不向前端暴露异常详情。
 * 错误格式由 {@code ss.web.error-format} 决定：默认 {@code resp}，错误与成功共用 {@code Resp} 信封，
 * 客户端只需一种解析方式；{@code problem-detail} 输出 RFC 9457 的 {@code application/problem+json}。
 * 优先级最低：应用自己的 {@code @RestControllerAdvice} 能先于它处理异常，从而覆盖这里的约定。
 * 应用的 advice 没有显式 {@code @Order} 时，顺序值与这里并列最低，靠 Bean 注册先后区分：Spring Boot 保证自动配置的 Bean
 * 在应用自己的 Bean 之后注册，所以应用的先生效（有测试覆盖）。应用显式设置了 {@code @Order} 的，数值越小越先生效。
 *
 * @author Stephen
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private final SsWebProperties.ErrorFormat errorFormat;

    public GlobalExceptionHandler() {
        this(SsWebProperties.ErrorFormat.RESP);
    }

    public GlobalExceptionHandler(SsWebProperties.ErrorFormat errorFormat) {
        this.errorFormat = errorFormat;
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<?> handleBusiness(BusinessException e) {
        log.warn("业务异常: code={}, msg={}", e.getCode(), e.getMsg());
        if (e.getCause() != null) {
            log.error("业务异常的原始原因", e.getCause());
        }
        return error(e.getHttpStatus(), e.getCode(), e.getMsg());
    }

    /**
     * 下游返回 4xx 且带业务 Resp 时透传其状态码、code、msg；其余统一 502，不暴露下游细节：
     * 5xx、非 Resp 响应，以及框架自己生成的 404/405/415 等（code 是状态名，如 NOT_FOUND，说明是我方调用路径或方式错了，不是业务结果）
     */
    @ExceptionHandler(RemoteCallException.class)
    public ResponseEntity<?> handleRemoteCall(RemoteCallException e) {
        if (e.getStatus().is4xxClientError() && e.getCode() != null && !isFrameworkError(e)) {
            log.warn("下游业务错误: {} code={}, msg={}", e.getMessage(), e.getCode(), e.getRemoteMsg());
            return error(e.getStatus(), e.getCode(), e.getRemoteMsg());
        }
        log.error("下游调用失败: {}, body={}", e.getMessage(), StringUtils.truncate(e.getBody(), 500));
        return error(HttpStatus.BAD_GATEWAY, HttpStatus.BAD_GATEWAY.name(), "下游服务异常");
    }

    /**
     * 下游根本没有给出 HTTP 响应：地址解析失败、连接被拒、连接中断、超时，客户端抛出 {@link ResourceAccessException}。
     * 超时 504，其余 502。故障（下游宕机或抖动）会成批出现，所以只打一行 WARN，不带堆栈。
     */
    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<?> handleResourceAccess(ResourceAccessException e) {
        boolean timeout = isTimeout(e);
        log.warn("下游{}: {}", timeout ? "超时" : "不可达", e.getMessage());
        HttpStatus status = timeout ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.BAD_GATEWAY;
        return error(status, status.name(), timeout ? "下游服务超时" : "下游服务异常");
    }

    private static boolean isTimeout(Throwable throwable) {
        Throwable current = throwable;
        for (int depth = 0; current != null && depth < 10; depth++, current = current.getCause()) {
            if (current instanceof SocketTimeoutException || current instanceof HttpTimeoutException) {
                return true;
            }
        }
        return false;
    }

    private static boolean isFrameworkError(RemoteCallException e) {
        HttpStatus status = HttpStatus.resolve(e.getStatus().value());
        return status != null && status.name().equals(e.getCode());
    }

    /**
     * @RequestBody / @ModelAttribute 校验与绑定失败。
     * WARN 日志只写字段名和校验消息，不写被拒绝的值：那是用户的原始输入，可能包含密码、证件号等；完整信息在 DEBUG 日志里
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<?> handleBind(BindException e) {
        log.warn("参数绑定/校验异常: {}", describe(e.getBindingResult()));
        log.debug("参数绑定/校验异常的完整信息", e);
        ObjectError error = e.getBindingResult().getAllErrors().stream().findFirst().orElse(null);
        if (error instanceof FieldError fieldError) {
            if ("typeMismatch".equalsIgnoreCase(fieldError.getCode())) {
                return badRequest(CommonCode.PARAMS_TRANSFER_ERROR, "参数格式(" + fieldError.getField() + ")错误");
            }
            return badRequest(CommonCode.PARAMS_ERROR, fieldError.getDefaultMessage());
        }
        return badRequest(CommonCode.PARAMS_ERROR, error == null ? null : error.getDefaultMessage());
    }

    private static String describe(BindingResult result) {
        return result.getAllErrors().stream()
                .map(error -> (error instanceof FieldError field ? field.getField() : error.getObjectName())
                        + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
    }

    /**
     * 方法参数上的校验（@Validated 级联到参数、@Min 等）
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<?> handleMethodValidation(HandlerMethodValidationException e) {
        log.warn("方法参数校验异常: {}", e.getMessage());
        String msg = e.getAllErrors().stream()
                .map(error -> error.getDefaultMessage())
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        return badRequest(CommonCode.PARAMS_ERROR, msg);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<?> handleConstraintViolation(ConstraintViolationException e) {
        log.warn("约束校验异常: {}", e.getMessage());
        String msg = e.getConstraintViolations().stream()
                .map(v -> v.getMessage())
                .findFirst()
                .orElse(null);
        return badRequest(CommonCode.PARAMS_ERROR, msg);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<?> handleMissingParameter(MissingServletRequestParameterException e) {
        log.warn("缺少请求参数: {}", e.getParameterName());
        return badRequest(CommonCode.PARAMS_LOSS, "请求参数 " + e.getParameterName() + " 不能为空");
    }

    @ExceptionHandler(TypeMismatchException.class)
    public ResponseEntity<?> handleTypeMismatch(TypeMismatchException e) {
        // 异常消息里带有用户传入的原始值，WARN 日志里不写，完整信息在 DEBUG 日志里
        log.warn("参数类型转换异常: 参数 {} 需要 {}", e.getPropertyName(),
                e.getRequiredType() == null ? "?" : e.getRequiredType().getSimpleName());
        log.debug("参数类型转换异常的完整信息", e);
        Class<?> required = e.getRequiredType();
        String msg = "参数(" + e.getPropertyName() + ")类型错误" + (required == null ? "" : ",必须(" + required.getSimpleName() + ")");
        return badRequest(CommonCode.PARAMS_TRANSFER_ERROR, msg);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> handleNotReadable(HttpMessageNotReadableException e) {
        // 异常消息可能带有请求体里的片段，WARN 日志里只写原因的类型，完整信息在 DEBUG 日志里
        log.warn("请求体无法解析: {}", NestedExceptionUtils.getMostSpecificCause(e).getClass().getSimpleName());
        log.debug("请求体无法解析的完整信息", e);
        return badRequest(CommonCode.PARAMS_ERROR, "请求体格式错误");
    }

    /**
     * 兜底。Spring MVC 自带的 404/405/415 等异常实现了 {@link ErrorResponse}，沿用其状态码；其余按 500 处理
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleException(Exception e) {
        if (e instanceof ErrorResponse errorResponse) {
            HttpStatusCode status = errorResponse.getStatusCode();
            log.warn("请求异常: status={}, {}", status, e.getMessage());
            HttpStatus resolved = HttpStatus.resolve(status.value());
            String code = resolved == null ? CommonCode.SYSTEM_ERROR.name() : resolved.name();
            return error(status, code, errorResponse.getBody().getDetail());
        }
        log.error("未知异常", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, CommonCode.SYSTEM_ERROR.name(), CommonCode.SYSTEM_ERROR.getMsg());
    }

    private ResponseEntity<?> badRequest(CommonCode code, String msg) {
        return error(HttpStatus.BAD_REQUEST, code.name(), msg == null ? code.getMsg() : msg);
    }

    /**
     * 按配置的错误格式输出：{@code RESP} 为 {@link Resp} 信封；{@code PROBLEM_DETAIL} 为 RFC 9457
     * ({@code application/problem+json})，业务码和 traceId 作为扩展成员 {@code code}、{@code traceId} 带出；
     * 业务异常的 2xx 状态码（Resp 约定下的默认值）在此格式下改为 422。
     */
    private ResponseEntity<?> error(HttpStatusCode status, String code, String msg) {
        if (errorFormat == SsWebProperties.ErrorFormat.PROBLEM_DETAIL) {
            if (status.is2xxSuccessful()) {
                // Resp 约定里业务失败可以是 HTTP 200；Problem Details 描述的是错误，不能是 2xx
                status = HttpStatus.UNPROCESSABLE_CONTENT;
            }
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, msg);
            HttpStatus resolved = HttpStatus.resolve(status.value());
            if (resolved != null) {
                problem.setTitle(resolved.getReasonPhrase());
            }
            problem.setProperty("code", code);
            String traceId = MDC.get("traceId");
            if (traceId != null) {
                problem.setProperty("traceId", traceId);
            }
            return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
        }
        return ResponseEntity.status(status).body(Resp.fail(code, msg));
    }
}
