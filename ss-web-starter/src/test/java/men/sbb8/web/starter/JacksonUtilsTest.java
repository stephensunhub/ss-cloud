package men.sbb8.web.starter;

import men.sbb8.web.starter.json.JacksonUtils;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 不启动 Spring，验证兜底 mapper 的行为
 */
class JacksonUtilsTest {

    record Item(String name, LocalDateTime at, Instant instant) {
    }

    private static final LocalDateTime AT = LocalDateTime.of(2026, 1, 2, 3, 4, 5);

    @Test
    void writesIso8601() {
        String json = JacksonUtils.toJson(new Item("a", AT, Instant.parse("2026-01-02T03:04:05Z")));
        assertEquals("{\"name\":\"a\",\"at\":\"2026-01-02T03:04:05\",\"instant\":\"2026-01-02T03:04:05Z\"}", json);
    }

    @Test
    void readsBothLocalDateTimeFormats() {
        assertEquals(AT, JacksonUtils.fromJson("\"2026-01-02T03:04:05\"", LocalDateTime.class));
        assertEquals(AT, JacksonUtils.fromJson("\"2026-01-02 03:04:05\"", LocalDateTime.class));
        assertEquals(AT.withNano(123_000_000), JacksonUtils.fromJson("\"2026-01-02 03:04:05.123\"", LocalDateTime.class));
    }

    @Test
    void badLocalDateTimeFailsWithJacksonException() {
        assertThrows(JacksonException.class, () -> JacksonUtils.fromJson("\"2026/01/02 03:04:05\"", LocalDateTime.class));
        assertThrows(JacksonException.class, () -> JacksonUtils.fromJson("123456", LocalDateTime.class));
    }

    @Test
    void ignoresUnknownProperties() {
        Item item = JacksonUtils.fromJson("{\"name\":\"a\",\"extra\":1}", Item.class);
        assertEquals("a", item.name());
    }

    @Test
    void nullAndBlankReturnNull() {
        assertNull(JacksonUtils.fromJson((String) null, Item.class));
        assertNull(JacksonUtils.fromJson("  ", Item.class));
        assertNull(JacksonUtils.fromJson((byte[]) null, Item.class));
        assertNull(JacksonUtils.fromJson(new byte[0], Item.class));
        assertNull(JacksonUtils.fromJson((java.io.InputStream) null, Item.class));
        assertNull(JacksonUtils.fromJsonList("", Item.class));
        assertNull(JacksonUtils.readTree(null));
        assertNull(JacksonUtils.convert(null, Item.class));
    }

    @Test
    void genericTypesAreNotErased() {
        List<Item> items = JacksonUtils.fromJson("[{\"name\":\"a\"}]", new TypeReference<List<Item>>() {
        });
        assertInstanceOf(Item.class, items.getFirst());

        var in = new ByteArrayInputStream("[{\"name\":\"b\"}]".getBytes(StandardCharsets.UTF_8));
        List<Item> fromStream = JacksonUtils.fromJson(in, new TypeReference<List<Item>>() {
        });
        assertEquals("b", fromStream.getFirst().name());

        assertEquals("c", JacksonUtils.fromJsonList("[{\"name\":\"c\"}]", Item.class).getFirst().name());
    }

    @Test
    void convertWithoutJsonString() {
        Item item = JacksonUtils.convert(Map.of("name", "m", "at", "2026-01-02 03:04:05"), Item.class);
        assertEquals(AT, item.at());
    }
}
