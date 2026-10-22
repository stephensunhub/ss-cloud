package men.sbb8.web.starter.json;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.std.DelegatingDeserializer;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 包装 Jackson 自带的 {@code LocalDateTime} 反序列化器：先交给它处理（字段上的 {@code @JsonFormat}、
 * 全局时间配置等都照常生效），仅当它拒绝时，再兼容日期与时间之间用空格分隔的写法
 * （{@code 2026-01-02 03:04:05}）。序列化不受影响，仍是 ISO-8601。
 *
 * @author Stephen
 */
public class LenientLocalDateTimeDeserializer extends DelegatingDeserializer {

    /**
     * 日期部分固定 10 位（yyyy-MM-dd），其后的第 11 位是分隔符
     */
    private static final int SEPARATOR_INDEX = 10;

    public LenientLocalDateTimeDeserializer(ValueDeserializer<?> delegate) {
        super(delegate);
    }

    @Override
    protected ValueDeserializer<?> newDelegatingInstance(ValueDeserializer<?> newDelegatee) {
        return new LenientLocalDateTimeDeserializer(newDelegatee);
    }

    @Override
    public Object deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
        try {
            return super.deserialize(p, ctxt);
        } catch (JacksonException original) {
            LocalDateTime lenient = parseSpaceSeparated(p);
            if (lenient == null) {
                throw original;
            }
            return lenient;
        }
    }

    private static LocalDateTime parseSpaceSeparated(JsonParser p) {
        if (!p.hasToken(JsonToken.VALUE_STRING)) {
            return null;
        }
        String text = p.getString().trim();
        if (text.length() <= SEPARATOR_INDEX || text.charAt(SEPARATOR_INDEX) != ' ') {
            return null;
        }
        try {
            return LocalDateTime.parse(text.substring(0, SEPARATOR_INDEX) + 'T' + text.substring(SEPARATOR_INDEX + 1),
                    DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
