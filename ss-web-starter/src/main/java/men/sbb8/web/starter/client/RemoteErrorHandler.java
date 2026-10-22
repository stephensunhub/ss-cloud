package men.sbb8.web.starter.client;

import org.springframework.http.HttpRequest;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;
import men.sbb8.web.starter.error.RemoteCallException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 把下游的非 2xx 响应转成 {@link RemoteCallException}，响应体是 {@code Resp} 结构时提取 code 和 msg
 *
 * @author Stephen
 */
public class RemoteErrorHandler implements RestClient.ResponseSpec.ErrorHandler {

    private static final int MAX_BODY_BYTES = 64 * 1024;

    private final JsonMapper jsonMapper;

    public RemoteErrorHandler(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void handle(HttpRequest request, ClientHttpResponse response) throws IOException {
        String body = readBody(response);
        String code = null;
        String msg = null;
        try {
            JsonNode node = jsonMapper.readTree(body);
            if (node.isObject() && node.path("code").isString()) {
                code = node.path("code").asString();
                msg = node.path("msg").asString(null);
            }
        } catch (RuntimeException ignored) {
            // 不是 JSON（网关错误页等），只保留原始响应体
        }
        String message = "调用下游失败: " + request.getMethod() + " " + request.getURI() + " -> " + response.getStatusCode();
        throw new RemoteCallException(message, response.getStatusCode(), code, msg, body);
    }

    /**
     * 最多读取 {@link #MAX_BODY_BYTES} 字节，按响应声明的字符集解码，缺省 UTF-8；防止异常响应撑爆内存
     */
    private static String readBody(ClientHttpResponse response) throws IOException {
        byte[] bytes = response.getBody().readNBytes(MAX_BODY_BYTES);
        MediaType contentType = response.getHeaders().getContentType();
        Charset charset = contentType != null && contentType.getCharset() != null
                ? contentType.getCharset() : StandardCharsets.UTF_8;
        return new String(bytes, charset);
    }
}
