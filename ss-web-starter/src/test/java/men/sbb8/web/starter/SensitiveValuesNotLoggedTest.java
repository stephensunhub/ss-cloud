package men.sbb8.web.starter;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import men.sbb8.web.starter.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用户传入的原始值（可能是密码、证件号等）不能出现在 WARN 及以上的日志里：参数类型错误、绑定校验失败、请求体无法解析，
 * 日志只写字段名和原因；完整信息只在 DEBUG 日志里。
 */
@SpringBootTest
@AutoConfigureMockMvc
class SensitiveValuesNotLoggedTest {

    @Autowired
    MockMvc mvc;

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @BeforeEach
    void attach() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detach() {
        logger.detachAppender(appender);
        appender.stop();
    }

    private void assertNothingAtWarnOrAboveContains(String secret) {
        var warnings = appender.list.stream().filter(event -> event.getLevel().isGreaterOrEqual(Level.WARN)).toList();
        assertThat(warnings).as("应有一条 WARN 日志").isNotEmpty();
        for (ILoggingEvent event : warnings) {
            assertThat(event.getFormattedMessage()).doesNotContain(secret);
            assertThat(event.getThrowableProxy()).as("WARN 日志不带异常（异常消息里有原始值）").isNull();
        }
    }

    @Test
    void typeMismatchDoesNotLogTheRawValue() throws Exception {
        mvc.perform(get("/param").param("id", "secret-token-abc")).andExpect(status().isBadRequest());
        assertNothingAtWarnOrAboveContains("secret-token-abc");
    }

    @Test
    void bindingFailureDoesNotLogTheRejectedValue() throws Exception {
        mvc.perform(get("/page").param("size", "7654321")).andExpect(status().isBadRequest());
        assertNothingAtWarnOrAboveContains("7654321");
    }

    @Test
    void unreadableBodyDoesNotLogTheBodyContent() throws Exception {
        mvc.perform(post("/valid").contentType(MediaType.APPLICATION_JSON).content("{\"name\": secret-body-xyz"))
                .andExpect(status().isBadRequest());
        assertNothingAtWarnOrAboveContains("secret-body-xyz");
    }
}
