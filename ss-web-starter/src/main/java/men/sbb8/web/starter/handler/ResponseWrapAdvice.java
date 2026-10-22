package men.sbb8.web.starter.handler;

import men.sbb8.web.starter.dto.resp.RawResponse;
import men.sbb8.web.starter.dto.resp.Resp;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Objects;

/**
 * 把控制器返回值统一包装为 {@link Resp}。
 * Spring / springdoc 自带的接口不包装，方法或类标注 {@link RawResponse} 的不包装，
 * 非 JSON 响应（显式 produces 为 text/plain 的 String、XML、图片等）也不包装。
 *
 * @author Stephen
 */
@RestControllerAdvice
public class ResponseWrapAdvice implements ResponseBodyAdvice<Object>, Ordered {

    private final JsonMapper jsonMapper;

    public ResponseWrapAdvice(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public boolean supports(MethodParameter returnType,
                            @NonNull Class<? extends HttpMessageConverter<?>> converterType) {
        return shouldWrap(returnType.getContainingClass(),
                Objects.requireNonNull(returnType.getMethod()), returnType.getParameterType());
    }

    /**
     * 某个控制器方法的返回值是否会被包装为 {@link Resp}。运行期的包装与 OpenAPI 文档共用这一条规则，保证两者一致
     */
    public static boolean shouldWrap(Class<?> controller, Method method, Class<?> returnType) {
        String pkg = controller.getPackageName();
        if (pkg.startsWith("org.springframework.") || pkg.startsWith("org.springdoc.")) {
            return false;
        }
        if (AnnotatedElementUtils.hasAnnotation(method, RawResponse.class)
                || AnnotatedElementUtils.hasAnnotation(controller, RawResponse.class)) {
            return false;
        }
        return !Resp.class.isAssignableFrom(returnType)
                && !ProblemDetail.class.isAssignableFrom(returnType)
                && !Resource.class.isAssignableFrom(returnType)
                && returnType != byte[].class;
    }

    @Override
    public @Nullable Object beforeBodyWrite(@Nullable Object body,
                                            MethodParameter returnType,
                                            MediaType selectedContentType,
                                            Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                            ServerHttpRequest request,
                                            ServerHttpResponse response) {
        if (body instanceof Resp<?> || body instanceof ProblemDetail || body instanceof Resource) {
            return body;
        }
        if (body instanceof String) {
            // 显式声明 produces 且都不是 JSON（text/plain 等）的接口，保持纯文本
            if (!producesJson(request)) {
                return body;
            }
            Resp<Object> wrapped = Resp.ok(body);
            if (StringHttpMessageConverter.class.isAssignableFrom(selectedConverterType)) {
                // String 由 StringHttpMessageConverter 原样输出，需自行序列化并声明 JSON
                response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                return jsonMapper.writeValueAsString(wrapped);
            }
            return wrapped;
        }
        // 只包装 JSON 响应，其他媒体类型（XML、图片、事件流等）保持原样
        return isJson(selectedContentType) ? Resp.ok(body) : body;
    }

    private static boolean producesJson(ServerHttpRequest request) {
        if (request instanceof ServletServerHttpRequest servletRequest
                && servletRequest.getServletRequest()
                .getAttribute(HandlerMapping.PRODUCIBLE_MEDIA_TYPES_ATTRIBUTE) instanceof Collection<?> producible
                && !producible.isEmpty()) {
            return producible.stream().anyMatch(type -> type instanceof MediaType mediaType && isJson(mediaType));
        }
        return true;
    }

    private static boolean isJson(MediaType type) {
        return "application".equalsIgnoreCase(type.getType())
                && ("json".equalsIgnoreCase(type.getSubtype()) || type.getSubtype().toLowerCase().endsWith("+json"));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
