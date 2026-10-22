package men.sbb8.web.starter.json;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.ValueDeserializerModifier;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

import java.time.LocalDateTime;

/**
 * 项目统一的 Jackson 约定。同时用于容器里的 JsonMapper 和 {@link JacksonUtils} 的兜底 mapper，保证两者一致。
 *
 * <p>时间格式规范：
 * <ul>
 *     <li>时间点用 {@code Instant} / {@code OffsetDateTime}，输出 ISO-8601 带时区（{@code 2026-01-02T03:04:05Z}）</li>
 *     <li>{@code LocalDateTime} 输出 ISO-8601，读取兼容 ISO 与空格分隔两种写法，字段上的 {@code @JsonFormat} 仍然有效</li>
 *     <li>数值时间戳不使用，新代码避免 {@code java.util.Date}</li>
 * </ul>
 *
 * @author Stephen
 */
public class SsJacksonCustomizer implements JsonMapperBuilderCustomizer {

    @Override
    public void customize(JsonMapper.Builder builder) {
        builder.addModule(new SimpleModule("ss-time").setDeserializerModifier(new ValueDeserializerModifier() {
            @Override
            public ValueDeserializer<?> modifyDeserializer(DeserializationConfig config,
                                                           BeanDescription.Supplier beanDesc,
                                                           ValueDeserializer<?> deserializer) {
                return beanDesc.getBeanClass() == LocalDateTime.class
                        ? new LenientLocalDateTimeDeserializer(deserializer)
                        : deserializer;
            }
        }));
    }
}
