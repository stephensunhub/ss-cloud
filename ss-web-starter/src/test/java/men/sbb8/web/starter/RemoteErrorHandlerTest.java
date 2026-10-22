package men.sbb8.web.starter;

import men.sbb8.web.starter.client.RemoteErrorHandler;
import men.sbb8.web.starter.error.RemoteCallException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.nio.charset.Charset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoteErrorHandlerTest {

    private final RemoteErrorHandler handler = new RemoteErrorHandler(JsonMapper.builder().build());
    private final MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET, URI.create("http://x/y"));

    @Test
    void bodyIsCapped() {
        MockClientHttpResponse response = new MockClientHttpResponse("a".repeat(1_000_000).getBytes(), HttpStatus.BAD_GATEWAY);
        RemoteCallException e = assertThrows(RemoteCallException.class, () -> handler.handle(request, response));
        assertEquals(64 * 1024, e.getBody().length());
    }

    @Test
    void usesDeclaredCharset() {
        Charset gbk = Charset.forName("GBK");
        MockClientHttpResponse response = new MockClientHttpResponse(
                "{\"code\":\"X\",\"msg\":\"参数错误\"}".getBytes(gbk), HttpStatus.BAD_REQUEST);
        response.getHeaders().setContentType(new MediaType("application", "json", gbk));
        RemoteCallException e = assertThrows(RemoteCallException.class, () -> handler.handle(request, response));
        assertEquals("X", e.getCode());
        assertEquals("参数错误", e.getRemoteMsg());
    }

    @Test
    void nonJsonBodyKeepsOnlyRawText() {
        MockClientHttpResponse response = new MockClientHttpResponse("<html>bad gateway</html>".getBytes(), HttpStatus.BAD_GATEWAY);
        RemoteCallException e = assertThrows(RemoteCallException.class, () -> handler.handle(request, response));
        assertEquals(null, e.getCode());
        assertTrue(e.getBody().contains("bad gateway"));
    }
}
