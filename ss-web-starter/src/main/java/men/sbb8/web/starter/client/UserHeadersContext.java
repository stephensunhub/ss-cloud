package men.sbb8.web.starter.client;

import io.micrometer.context.ThreadLocalAccessor;
import jakarta.servlet.http.HttpServletRequest;
import men.sbb8.web.starter.resolver.UserInfoArgumentResolver;
import org.jspecify.annotations.Nullable;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 当前线程要透传给内部服务的用户头（X-User-Id 等）。
 * <ul>
 *     <li>请求线程：直接取当前 Servlet 请求里的头；</li>
 *     <li>异步线程：请求结束后 {@code HttpServletRequest} 就不能再用了，所以通过 Micrometer Context Propagation
 *     （{@link Accessor}）在提交任务时把<b>头的值</b>复制一份，在执行任务的线程里恢复。
 *     Boot 的 {@code @Async} 线程池（{@code spring.task.execution.propagate-context=true}，starter 默认开启）、
 *     {@code ContextSnapshot.wrap(...)} 包过的任务都会带上；自建线程池、{@code CompletableFuture} 默认池、
 *     {@code new Thread} 不会自动带，需要用 {@code ContextSnapshotFactory.builder().build().captureAll().wrap(任务)} 包一层。</li>
 * </ul>
 *
 * @author Stephen
 */
public final class UserHeadersContext {

    /** Context Propagation 里的键 */
    public static final String KEY = "ss.web.user-headers";

    /** 请求里没有 X-Request-Ip 时，透传给下游的占位值 */
    public static final String UNKNOWN_IP = "0.0.0.0";

    /** 会透传的头，顺序固定 */
    static final List<String> HEADERS = List.of(
            UserInfoArgumentResolver.HEADER_CHANNEL_ID,
            UserInfoArgumentResolver.HEADER_USER_ID,
            UserInfoArgumentResolver.HEADER_USER_NAME,
            UserInfoArgumentResolver.HEADER_CLIENT,
            UserInfoArgumentResolver.HEADER_LANGUAGE,
            UserInfoArgumentResolver.HEADER_REQUEST_IP);

    /** 异步线程里由 Context Propagation 恢复出来的值 */
    private static final ThreadLocal<Map<String, String>> PROPAGATED = new ThreadLocal<>();

    private UserHeadersContext() {
    }

    /**
     * 当前线程的用户头（只含存在的头）。优先用被传递进来的值（异步线程），其次取当前 Servlet 请求；都没有则为空
     */
    public static Map<String, String> current() {
        Map<String, String> propagated = PROPAGATED.get();
        if (propagated != null) {
            return propagated;
        }
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return read(attributes.getRequest());
        }
        return Map.of();
    }

    private static Map<String, String> read(HttpServletRequest request) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String name : HEADERS) {
            String value = request.getHeader(name);
            if (value != null) {
                values.put(name, value);
            }
        }
        return Map.copyOf(values);
    }

    /**
     * 注册到 {@code ContextRegistry} 的访问器：提交异步任务时 {@link #getValue()} 在提交线程里复制头的值，
     * 执行任务前 {@link #setValue(Map)} 在执行线程里恢复，执行后清理
     */
    public static final class Accessor implements ThreadLocalAccessor<Map<String, String>> {

        @Override
        public Object key() {
            return KEY;
        }

        @Override
        public @Nullable Map<String, String> getValue() {
            Map<String, String> current = current();
            return current.isEmpty() ? null : current;
        }

        @Override
        public void setValue(Map<String, String> value) {
            PROPAGATED.set(value);
        }

        @Override
        public void setValue() {
            PROPAGATED.remove();
        }
    }
}
