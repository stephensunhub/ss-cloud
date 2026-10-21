package men.sbb8.web.starter;

import men.sbb8.web.starter.persistence.JsonNodeConverter;
import men.sbb8.web.starter.persistence.JsonNodeTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PersistenceConvertersTest {

    private static final JsonNode NODE = JsonMapper.builder().build().readTree("{\"a\":1,\"b\":[true,null]}");

    @Test
    void jpaConverterRoundTrip() {
        JsonNodeConverter converter = new JsonNodeConverter();
        String db = converter.convertToDatabaseColumn(NODE);
        assertEquals("{\"a\":1,\"b\":[true,null]}", db);
        assertEquals(NODE, converter.convertToEntityAttribute(db));
        assertNull(converter.convertToDatabaseColumn(null));
        assertNull(converter.convertToEntityAttribute(null));
        assertNull(converter.convertToEntityAttribute(" "));
    }

    @Test
    void mybatisTypeHandlerRoundTrip() throws Exception {
        JsonNodeTypeHandler handler = new JsonNodeTypeHandler();

        PreparedStatement ps = mock(PreparedStatement.class);
        handler.setNonNullParameter(ps, 1, NODE, JdbcType.VARCHAR);
        verify(ps).setString(1, "{\"a\":1,\"b\":[true,null]}");

        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("c")).thenReturn("{\"a\":1,\"b\":[true,null]}");
        when(rs.getString(2)).thenReturn(null);
        assertEquals(NODE, handler.getNullableResult(rs, "c"));
        assertNull(handler.getNullableResult(rs, 2));
    }
}
