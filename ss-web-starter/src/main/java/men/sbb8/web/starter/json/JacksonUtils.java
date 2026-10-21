package men.sbb8.web.starter.json;

import org.jspecify.annotations.Nullable;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.List;

/**
 * JSON 静态门面，给无法注入 Bean 的地方使用（静态方法、枚举、转换器等）；
 * 能注入的地方直接注入 {@link JsonMapper}。
 *
 * <p>启动后使用容器里统一配置的 mapper（由 {@code SsJacksonAutoConfiguration} 设置）。
 * 容器就绪前或没有 Spring 的单元测试里使用兜底 mapper，它应用了相同的 {@link SsJacksonCustomizer}，
 * 但不含 spring.jackson.* 配置。
 *
 * <p>Jackson 3 的异常都是 unchecked，解析失败抛 {@link tools.jackson.core.JacksonException}。
 * 入参为 null 或空白字符串时 {@code fromJson}、{@code readTree} 返回 null。
 *
 * @author Stephen
 */
public final class JacksonUtils {

    private static final JsonMapper FALLBACK = buildFallback();

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
    public static void init(JsonMapper jsonMapper) {
        mapper = jsonMapper;
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

    public static <T> T fromJson(byte[] json, Class<T> type) {
        return mapper.readValue(json, type);
    }

    public static <T> T fromJson(byte[] json, TypeReference<T> type) {
        return mapper.readValue(json, type);
    }

    public static <T> T fromJson(InputStream json, Class<T> type) {
        return mapper.readValue(json, type);
    }

    public static <T> T fromJson(InputStream json, TypeReference<T> type) {
        return mapper.readValue(json, type);
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
