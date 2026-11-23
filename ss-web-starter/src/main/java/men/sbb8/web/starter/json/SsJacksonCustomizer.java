package men.sbb8.web.starter.json;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.deser.ValueDeserializerModifier;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

import java.math.BigDecimal;
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
 * <p>金额（{@code BigDecimal}）规范：
 * <ul>
 *     <li>输出十进制<b>字符串</b>（{@code "398.00"}），避免 JavaScript 等客户端按双精度解析丢精度；用 {@code toPlainString()}，
 *     不会出现科学计数法，<b>小数位原样保留</b>（{@code new BigDecimal("398.00")} 输出 {@code "398.00"}，不是 {@code 398}）</li>
 *     <li>小数位由业务代码在 DTO 里决定（{@code setScale}），这里不统一补零、不舍入</li>
 *     <li>读取不改，Jackson 默认同时接受字符串和数字</li>
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
        builder.addModule(new SimpleModule("ss-decimal").addSerializer(BigDecimal.class, new PlainStringSerializer()));
    }

    /** 金额写成字符串，不用科学计数法，小数位不变 */
    private static final class PlainStringSerializer extends ValueSerializer<BigDecimal> {

        @Override
        public void serialize(BigDecimal value, JsonGenerator generator, SerializationContext context) {
            generator.writeString(value.toPlainString());
        }
    }
}
