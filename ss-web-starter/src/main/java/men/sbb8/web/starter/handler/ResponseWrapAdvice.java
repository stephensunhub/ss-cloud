package men.sbb8.web.starter.handler;

import men.sbb8.web.starter.dto.resp.Resp;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 把控制器的成功返回值统一包成 {@link Resp}。
 *
 * <p>只对配置的包生效（{@code ss.web.wrap-response.base-packages}，含子包），没配置就不包装任何接口。
 * 包名在请求时读取，不是条件 Bean，所以原生镜像里设置同样生效。
 *
 * <p>下面这些返回值原样返回，不包装：
 * <ul>
 *     <li>已经是 {@link Resp}；错误响应（{@link ProblemDetail}、{@link ErrorResponse}）</li>
 *     <li>{@link String}</li>
 *     <li>文件和流：{@code byte[]}、{@link Resource}、{@link StreamingResponseBody}、{@link ResponseBodyEmitter}（含 SSE）</li>
 *     <li>响应类型不是 JSON 的（比如 XML、纯文本）</li>
 * </ul>
 * 返回 {@link Resource} 且有文件名、控制器没自己设置 Content-Disposition 时，补上 {@code attachment}（文件名按 UTF-8 编码）；
 * 不改 Content-Type，由消息转换器按文件类型决定。
 *
 * @author Stephen
 */
@RestControllerAdvice
public class ResponseWrapAdvice implements ResponseBodyAdvice<Object> {

    private final List<String> basePackages;

    public ResponseWrapAdvice(List<String> basePackages) {
        this.basePackages = basePackages.stream().map(String::trim).filter(p -> !p.isEmpty()).toList();
    }

    @Override
    public boolean supports(@NonNull MethodParameter returnType,
                            @NonNull Class<? extends HttpMessageConverter<?>> converterType) {
        if (basePackages.isEmpty()) {
            return false;
        }
        String packageName = returnType.getContainingClass().getPackageName();
        return basePackages.stream().anyMatch(base -> packageName.equals(base) || packageName.startsWith(base + "."));
    }

    @Override
    public @Nullable Object beforeBodyWrite(@Nullable Object body,
                                            @NonNull MethodParameter returnType,
                                            @NonNull MediaType selectedContentType,
                                            @NonNull Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                            @NonNull ServerHttpRequest request,
                                            @NonNull ServerHttpResponse response) {
        if (body instanceof Resource resource) {
            addAttachmentName(resource, response.getHeaders());
            return body;
        }
        if (body instanceof Resp || body instanceof String || body instanceof byte[]
                || body instanceof ProblemDetail || body instanceof ErrorResponse
                || body instanceof StreamingResponseBody || body instanceof ResponseBodyEmitter
                || !isJson(selectedContentType)) {
            return body;
        }
        return Resp.ok(body);
    }

    private static boolean isJson(MediaType type) {
        return MediaType.APPLICATION_JSON.isCompatibleWith(type) || type.getSubtype().endsWith("+json");
    }

    private static void addAttachmentName(Resource resource, HttpHeaders headers) {
        String filename = resource.getFilename();
        if (filename != null && !headers.containsHeader(HttpHeaders.CONTENT_DISPOSITION)) {
            headers.setContentDisposition(ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build());
        }
    }
}
