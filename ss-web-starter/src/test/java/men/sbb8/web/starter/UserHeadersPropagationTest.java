package men.sbb8.web.starter;

import io.micrometer.context.ContextSnapshotFactory;
import men.sbb8.web.starter.client.UserHeadersContext;
import men.sbb8.web.starter.client.UserHeadersInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 用户头透传：缺失的 X-Request-Ip 用 0.0.0.0 替代；@Async 等异步线程也能透传（请求结束后仍然有效）
 */
@SpringBootTest
class UserHeadersPropagationTest {

    @TestConfiguration
    @EnableAsync
    static class Config {
        @Bean
        AsyncWork asyncWork() {
            return new AsyncWork();
        }
    }

    static class AsyncWork {
        /** 在 @Async 线程里，经拦截器算出会发给下游的头 */
        @Async
        CompletableFuture<HttpHeaders> outgoingHeadersInsideAsync() throws Exception {
            return CompletableFuture.completedFuture(outgoing());
        }
    }

    @Autowired
    AsyncWork work;

    @AfterEach
    void clear() {
        RequestContextHolder.resetRequestAttributes();
    }

    /** 当前线程发出一个请求时，用户头拦截器会往请求上加的头 */
    static HttpHeaders outgoing() throws Exception {
        MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET, URI.create("http://downstream/x"));
        new UserHeadersInterceptor().intercept(request, new byte[0],
                (req, body) -> new MockClientHttpResponse(new byte[0], HttpStatus.OK));
        return request.getHeaders();
    }

    private static MockHttpServletRequest bindRequest(String... headers) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        for (int i = 0; i < headers.length; i += 2) {
            request.addHeader(headers[i], headers[i + 1]);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        return request;
    }

    // ---- X-Request-Ip 缺失 ----

    @Test
    void missingIpBecomesZeroAddress() throws Exception {
        bindRequest("X-User-Id", "42", "X-User-Name", "alice");
        HttpHeaders headers = outgoing();
        assertEquals("42", headers.getFirst("X-User-Id"));
        assertEquals("0.0.0.0", headers.getFirst("X-Request-Ip"));
        assertNull(headers.getFirst("X-Client"), "其它缺失的头不补");
    }

    @Test
    void blankIpBecomesZeroAddress() throws Exception {
        bindRequest("X-Request-Ip", "  ");
        assertEquals("0.0.0.0", outgoing().getFirst("X-Request-Ip"));
    }

    @Test
    void noRequestAtAllStillCarriesZeroAddress() throws Exception {
        HttpHeaders headers = outgoing();
        assertEquals("0.0.0.0", headers.getFirst("X-Request-Ip"));
        assertNull(headers.getFirst("X-User-Id"));
    }

    @Test
    void realIpFromGatewayIsKept() throws Exception {
        bindRequest("X-Request-Ip", "203.0.113.9", "X-User-Id", "7");
        assertEquals("203.0.113.9", outgoing().getFirst("X-Request-Ip"));
    }

    @Test
    void explicitlySetHeaderWins() throws Exception {
        bindRequest("X-User-Id", "42");
        MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET, URI.create("http://downstream/x"));
        request.getHeaders().set("X-User-Id", "explicit");
        request.getHeaders().set("X-Request-Ip", "10.0.0.1");
        new UserHeadersInterceptor().intercept(request, new byte[0],
                (req, body) -> new MockClientHttpResponse(new byte[0], HttpStatus.OK));
        assertEquals("explicit", request.getHeaders().getFirst("X-User-Id"));
        assertEquals("10.0.0.1", request.getHeaders().getFirst("X-Request-Ip"));
    }

    // ---- 异步线程 ----

    @Test
    void asyncMethodInheritsUserHeaders() throws Exception {
        bindRequest("X-User-Id", "42", "X-User-Name", "alice", "X-Request-Ip", "203.0.113.9", "X-Channel-Id", "3");
        CompletableFuture<HttpHeaders> future = work.outgoingHeadersInsideAsync();
        // 提交后请求就结束了：异步线程用的是提交时复制的值，不能再依赖请求对象
        RequestContextHolder.resetRequestAttributes();
        HttpHeaders headers = future.get(5, TimeUnit.SECONDS);
        assertEquals("42", headers.getFirst("X-User-Id"));
        assertEquals("alice", headers.getFirst("X-User-Name"));
        assertEquals("203.0.113.9", headers.getFirst("X-Request-Ip"));
        assertEquals("3", headers.getFirst("X-Channel-Id"));
    }

    @Test
    void asyncMethodWithoutIpGetsZeroAddress() throws Exception {
        bindRequest("X-User-Id", "42");
        HttpHeaders headers = work.outgoingHeadersInsideAsync().get(5, TimeUnit.SECONDS);
        assertEquals("42", headers.getFirst("X-User-Id"));
        assertEquals("0.0.0.0", headers.getFirst("X-Request-Ip"));
    }

    @Test
    void asyncWithoutAnyRequestHasNoUserButStillZeroIp() throws Exception {
        HttpHeaders headers = work.outgoingHeadersInsideAsync().get(5, TimeUnit.SECONDS);
        assertNull(headers.getFirst("X-User-Id"));
        assertEquals("0.0.0.0", headers.getFirst("X-Request-Ip"));
    }

    @Test
    void ownThreadPoolNeedsToWrapTasksWithContextSnapshot() throws Exception {
        bindRequest("X-User-Id", "42");
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            // 自建线程池不会自动传递：直接提交，拿不到用户头
            AtomicReference<HttpHeaders> plain = new AtomicReference<>();
            pool.submit(() -> {
                plain.set(outgoing());
                return null;
            }).get(5, TimeUnit.SECONDS);
            assertNull(plain.get().getFirst("X-User-Id"));

            // 用 ContextSnapshot 包一层就能传递
            AtomicReference<HttpHeaders> wrapped = new AtomicReference<>();
            Runnable task = ContextSnapshotFactory.builder().build().captureAll().wrap(() -> {
                try {
                    wrapped.set(outgoing());
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
            pool.submit(task).get(5, TimeUnit.SECONDS);
            assertEquals("42", wrapped.get().getFirst("X-User-Id"));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void contextIsCleanedUpAfterAsyncTask() throws Exception {
        bindRequest("X-User-Id", "42");
        work.outgoingHeadersInsideAsync().get(5, TimeUnit.SECONDS);
        RequestContextHolder.resetRequestAttributes();
        // 异步线程（和测试线程）执行完后不应残留上一次的用户
        assertEquals(Map.of(), UserHeadersContext.current());
    }
}
