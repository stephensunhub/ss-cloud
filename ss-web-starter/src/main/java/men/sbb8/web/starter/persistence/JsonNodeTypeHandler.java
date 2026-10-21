package men.sbb8.web.starter.persistence;

import men.sbb8.web.starter.json.JacksonUtils;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * MyBatis：JsonNode 与数据库 JSON/文本列互转（需要 mybatis 依赖）。
 * 配置 {@code mybatis.type-handlers-package=men.sbb8.web.starter.persistence} 注册，或在映射里用 typeHandler 指定。
 *
 * @author Stephen
 */
@MappedTypes(JsonNode.class)
public class JsonNodeTypeHandler extends BaseTypeHandler<JsonNode> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, JsonNode parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, JacksonUtils.toJson(parameter));
    }

    @Override
    public @Nullable JsonNode getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return JacksonUtils.readTree(rs.getString(columnName));
    }

    @Override
    public @Nullable JsonNode getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return JacksonUtils.readTree(rs.getString(columnIndex));
    }

    @Override
    public @Nullable JsonNode getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return JacksonUtils.readTree(cs.getString(columnIndex));
    }
}
