package men.sbb8.web.starter;

import men.sbb8.web.starter.json.JacksonUtils;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 不启动 Spring，验证金额（BigDecimal）和 JSON 字符串的互转
 *
 * @author Stephen
 */
class BigDecimalJsonTest {

    record Money(BigDecimal amount) {
    }

    @Test
    void writesStringKeepingScale() {
        assertEquals("{\"amount\":\"398.00\"}", JacksonUtils.toJson(new Money(new BigDecimal("398.00"))));
        assertEquals("{\"amount\":\"398\"}", JacksonUtils.toJson(new Money(new BigDecimal("398"))));
    }

    @Test
    void neverUsesScientificNotation() {
        assertEquals("{\"amount\":\"100000000000\"}", JacksonUtils.toJson(new Money(new BigDecimal("1E+11"))));
        assertEquals("{\"amount\":\"0.00000001\"}", JacksonUtils.toJson(new Money(new BigDecimal("1E-8"))));
    }

    @Test
    void keepsPrecisionBeyondDouble() {
        assertEquals("{\"amount\":\"12345678901234567890.123456789012345678\"}",
                JacksonUtils.toJson(new Money(new BigDecimal("12345678901234567890.123456789012345678"))));
    }

    @Test
    void readsStringAndNumber() {
        assertEquals(new BigDecimal("398.00"), JacksonUtils.fromJson("{\"amount\":\"398.00\"}", Money.class).amount());
        assertEquals(new BigDecimal("398.5"), JacksonUtils.fromJson("{\"amount\":398.5}", Money.class).amount());
    }

    @Test
    void roundTripKeepsScale() {
        Money back = JacksonUtils.fromJson(JacksonUtils.toJson(new Money(new BigDecimal("1.2300"))), Money.class);
        assertEquals("1.2300", back.amount().toPlainString());
    }
}
