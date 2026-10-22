package men.sbb8.web.starter.handler;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.web.method.HandlerMethod;

/**
 * 让 OpenAPI 文档与运行期一致：会被 {@link ResponseWrapAdvice} 包装的接口，成功响应的 schema
 * 描述为 {@code {code, msg, traceId, data: <原返回类型>}}，而不是裸的返回类型。
 *
 * @author Stephen
 */
public class ResponseWrapOperationCustomizer implements OperationCustomizer {

    private static final String JSON = org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        if (operation.getResponses() == null
                || !ResponseWrapAdvice.shouldWrap(handlerMethod.getBeanType(), handlerMethod.getMethod(),
                handlerMethod.getReturnType().getParameterType())) {
            return operation;
        }
        operation.getResponses().forEach((status, response) -> {
            if (status.startsWith("2")) {
                wrap(response);
            }
        });
        return operation;
    }

    private static void wrap(ApiResponse response) {
        Content wrapped = new Content();
        if (response.getContent() != null) {
            response.getContent().values().forEach(media -> wrapped.addMediaType(JSON, envelope(media.getSchema())));
        }
        if (wrapped.isEmpty()) {
            // 返回 void 的接口，响应体仍是不带 data 的 Resp
            wrapped.addMediaType(JSON, envelope(null));
        }
        response.setContent(wrapped);
    }

    private static MediaType envelope(Schema<?> data) {
        ObjectSchema envelope = new ObjectSchema();
        envelope.description("统一响应体");
        envelope.addProperty("code", new StringSchema().description("响应码"));
        envelope.addProperty("msg", new StringSchema().description("响应消息"));
        envelope.addProperty("traceId", new StringSchema().description("分布式日志ID"));
        if (data != null) {
            envelope.addProperty("data", data);
        }
        return new MediaType().schema(envelope);
    }
}
