package men.sbb8.web.starter.json;

import org.jspecify.annotations.Nullable;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * JSON 静态门面，给无法注入 Bean 的地方使用（静态方法、枚举、转换器等）；
 * 能注入的地方直接注入 {@link JsonMapper}。
 *
 * <p>启动后使用容器里统一配置的 mapper（由 {@code SsJacksonAutoConfiguration} 设置，容器关闭时还原）。
 * 这是进程级的全局状态：同一个 JVM 里有多个容器时，用最后启动且仍在运行的那个。
 * 容器就绪前或没有 Spring 的单元测试里使用兜底 mapper，它应用了相同的 {@link SsJacksonCustomizer}，
 * 但不含 spring.jackson.* 配置。
 *
 * <p>Jackson 3 的异常都是 unchecked，解析失败抛 {@link tools.jackson.core.JacksonException}。
 * 入参为 null、空白字符串或空字节数组时 {@code fromJson}、{@code readTree} 返回 null；
 * 输入流为 null 时返回 null，流里没有内容则按解析失败处理（不预读流，无法提前判断）。
 *
 * @author Stephen
 */
public final class JacksonUtils {

    private static final JsonMapper FALLBACK = buildFallback();

    /**
     * 正在运行的容器的 mapper，按启动先后排列；正常的服务只有一个，测试或父子容器里可能有多个
     */
    private static final Deque<JsonMapper> BOUND = new ArrayDeque<>();

    /**
     * 读取很频繁、写入只在容器启停时，所以读不加锁，用 volatile
     */
    private static volatile JsonMapper mapper = FALLBACK;

    private JacksonUtils() {
    }

    private static JsonMapper buildFallback() {
        JsonMapper.Builder builder = JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        new SsJacksonCustomizer().customize(builder);
        return builder.build();
    }

    /**
     * 由自动配置在容器启动时调用，换成统一配置的 mapper，业务代码不要调用
     */
    public static synchronized void init(JsonMapper jsonMapper) {
        BOUND.add(jsonMapper);
        mapper = jsonMapper;
    }

    /**
     * 由自动配置在容器关闭时调用，业务代码不要调用：回到仍在运行的、最后启动的那个容器的 mapper，没有则用兜底 mapper，
     * 不继续引用已关闭容器的对象
     */
    public static synchronized void release(JsonMapper jsonMapper) {
        BOUND.remove(jsonMapper);
        mapper = BOUND.isEmpty() ? FALLBACK : BOUND.getLast();
    }

    public static JsonMapper mapper() {
        return mapper;
    }

    // ─── 序列化 ──────────────────────────────────────

    public static String toJson(@Nullable Object value) {
        return mapper.writeValueAsString(value);
    }

    public static byte[] toJsonBytes(@Nullable Object value) {
        return mapper.writeValueAsBytes(value);
    }

    // ─── 反序列化 ────────────────────────────────────

    public static <T> @Nullable T fromJson(@Nullable String json, Class<T> type) {
        return isBlank(json) ? null : mapper.readValue(json, type);
    }

    public static <T> @Nullable T fromJson(@Nullable String json, TypeReference<T> type) {
        return isBlank(json) ? null : mapper.readValue(json, type);
    }

    public static <T> @Nullable T fromJson(@Nullable String json, Type type) {
        return isBlank(json) ? null : mapper.readValue(json, javaType(type));
    }

    public static <T> @Nullable T fromJson(byte @Nullable [] json, Class<T> type) {
        return json == null || json.length == 0 ? null : mapper.readValue(json, type);
    }

    public static <T> @Nullable T fromJson(byte @Nullable [] json, TypeReference<T> type) {
        return json == null || json.length == 0 ? null : mapper.readValue(json, type);
    }

    public static <T> @Nullable T fromJson(@Nullable InputStream json, Class<T> type) {
        return json == null ? null : mapper.readValue(json, type);
    }

    public static <T> @Nullable T fromJson(@Nullable InputStream json, TypeReference<T> type) {
        return json == null ? null : mapper.readValue(json, type);
    }

    public static <T> @Nullable List<T> fromJsonList(@Nullable String json, Class<T> elementType) {
        return isBlank(json) ? null
                : mapper.readValue(json, mapper.getTypeFactory().constructCollectionType(List.class, elementType));
    }

    public static @Nullable JsonNode readTree(@Nullable String json) {
        return isBlank(json) ? null : mapper.readTree(json);
    }

    // ─── 类型转换（不经过 JSON 字符串）──────────────────

    public static <T> @Nullable T convert(@Nullable Object from, Class<T> toType) {
        return from == null ? null : mapper.convertValue(from, toType);
    }

    public static <T> @Nullable T convert(@Nullable Object from, TypeReference<T> toType) {
        return from == null ? null : mapper.convertValue(from, toType);
    }

    private static JavaType javaType(Type type) {
        return mapper.getTypeFactory().constructType(type);
    }

    private static boolean isBlank(@Nullable String json) {
        return json == null || json.isBlank();
    }
}
