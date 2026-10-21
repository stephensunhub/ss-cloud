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
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import tools.jackson.databind.json.JsonMapper;

import java.util.Objects;

/**
 * 把控制器返回值统一包装为 {@link Resp}。
 * Spring / springdoc 自带的接口不包装，方法或类标注 {@link RawResponse} 的不包装。
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
        Class<?> controller = returnType.getContainingClass();
        String pkg = controller.getPackageName();
        if (pkg.startsWith("org.springframework.") || pkg.startsWith("org.springdoc.")) {
            return false;
        }
        if (AnnotatedElementUtils.hasAnnotation(Objects.requireNonNull(returnType.getMethod()), RawResponse.class)
                || AnnotatedElementUtils.hasAnnotation(controller, RawResponse.class)) {
            return false;
        }
        Class<?> type = returnType.getParameterType();
        return !Resp.class.isAssignableFrom(type)
                && !ProblemDetail.class.isAssignableFrom(type)
                && !Resource.class.isAssignableFrom(type)
                && type != byte[].class;
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
        Resp<Object> wrapped = Resp.ok(body);
        if (body instanceof String) {
            // String 由 StringHttpMessageConverter 输出，需自行序列化并声明 JSON
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            return jsonMapper.writeValueAsString(wrapped);
        }
        return wrapped;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
