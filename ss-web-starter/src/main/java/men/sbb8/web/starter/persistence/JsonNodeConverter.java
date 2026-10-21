package men.sbb8.web.starter.persistence;

import jakarta.persistence.AttributeConverter;
import men.sbb8.web.starter.json.JacksonUtils;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * JPA：JsonNode 与数据库 JSON/文本列互转。实体字段上显式使用：
 * {@code @Convert(converter = JsonNodeConverter.class)}（需要 jakarta.persistence-api）
 *
 * @author Stephen
 */
public class JsonNodeConverter implements AttributeConverter<JsonNode, String> {

    @Override
    public @Nullable String convertToDatabaseColumn(@Nullable JsonNode attribute) {
        return attribute == null ? null : JacksonUtils.toJson(attribute);
    }

    @Override
    public @Nullable JsonNode convertToEntityAttribute(@Nullable String dbData) {
        return JacksonUtils.readTree(dbData);
    }
}
