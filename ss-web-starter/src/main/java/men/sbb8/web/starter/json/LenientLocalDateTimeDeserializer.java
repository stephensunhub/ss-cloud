package men.sbb8.web.starter.json;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * LocalDateTime 反序列化：标准 ISO-8601（{@code 2026-01-02T03:04:05}）之外，
 * 兼容日期与时间之间用空格分隔（{@code 2026-01-02 03:04:05}）的写法。
 * 序列化仍使用 Jackson 默认的 ISO-8601，不受影响。
 *
 * @author Stephen
 */
public class LenientLocalDateTimeDeserializer extends StdDeserializer<LocalDateTime> {

    /**
     * 日期部分固定 10 位（yyyy-MM-dd），其后的第 11 位是分隔符
     */
    private static final int SEPARATOR_INDEX = 10;

    public LenientLocalDateTimeDeserializer() {
        super(LocalDateTime.class);
    }

    @Override
    public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) {
        if (!p.hasToken(JsonToken.VALUE_STRING)) {
            return (LocalDateTime) ctxt.handleUnexpectedToken(LocalDateTime.class, p);
        }
        String text = p.getString().trim();
        if (text.isEmpty()) {
            return null;
        }
        if (text.length() > SEPARATOR_INDEX && text.charAt(SEPARATOR_INDEX) == ' ') {
            text = text.substring(0, SEPARATOR_INDEX) + 'T' + text.substring(SEPARATOR_INDEX + 1);
        }
        try {
            return LocalDateTime.parse(text, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException e) {
            return (LocalDateTime) ctxt.handleWeirdStringValue(LocalDateTime.class, p.getString(),
                    "期望格式 yyyy-MM-ddTHH:mm:ss 或 yyyy-MM-dd HH:mm:ss");
        }
    }
}
