package men.sbb8.web.starter;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import men.sbb8.web.starter.dto.resp.Resp;
import men.sbb8.web.starter.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * 下游根本没有给出 HTTP 响应（连不上、解析失败、超时）：超时 504，其余 502；只打一行 WARN，不带堆栈。
 */
class DownstreamUnavailableTest {

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

    private static Resp<?> body(ResponseEntity<?> response) {
        return assertInstanceOf(Resp.class, response.getBody());
    }

    @Test
    void connectionProblemsAre502() {
        for (Throwable cause : new Throwable[]{new ConnectException("refused"), new UnknownHostException("wallet"),
                new java.io.IOException("reset")}) {
            ResponseEntity<?> response = new GlobalExceptionHandler()
                    .handleResourceAccess(new ResourceAccessException("I/O error", (java.io.IOException) cause));
            assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode(), cause.toString());
            assertEquals("BAD_GATEWAY", body(response).getCode());
            assertEquals("下游服务异常", body(response).getMsg());
        }
    }

    @Test
    void timeoutsAre504EvenWhenNested() {
        for (java.io.IOException cause : new java.io.IOException[]{new HttpTimeoutException("request timed out"),
                new SocketTimeoutException("Read timed out"),
                new java.io.IOException("wrapped", new SocketTimeoutException("inner"))}) {
            ResponseEntity<?> response = new GlobalExceptionHandler()
                    .handleResourceAccess(new ResourceAccessException("I/O error", cause));
            assertEquals(HttpStatus.GATEWAY_TIMEOUT, response.getStatusCode(), cause.toString());
            assertEquals("GATEWAY_TIMEOUT", body(response).getCode());
            assertEquals("下游服务超时", body(response).getMsg());
        }
    }

    @Test
    void oneWarnLineWithoutStackTrace() {
        new GlobalExceptionHandler().handleResourceAccess(new ResourceAccessException("I/O error", new ConnectException("refused")));
        assertEquals(1, appender.list.size());
        ILoggingEvent event = appender.list.get(0);
        assertEquals(Level.WARN, event.getLevel());
        assertNull(event.getThrowableProxy(), "下游宕机时会成批出现，不应每个都带堆栈");
    }

    @Test
    void problemDetailFormatToo() {
        ResponseEntity<?> response = new GlobalExceptionHandler(men.sbb8.web.starter.config.SsWebProperties.ErrorFormat.PROBLEM_DETAIL)
                .handleResourceAccess(new ResourceAccessException("I/O error", new HttpTimeoutException("t")));
        assertEquals(HttpStatus.GATEWAY_TIMEOUT, response.getStatusCode());
        ProblemDetail problem = assertInstanceOf(ProblemDetail.class, response.getBody());
        assertEquals("GATEWAY_TIMEOUT", problem.getProperties().get("code"));
    }
}
