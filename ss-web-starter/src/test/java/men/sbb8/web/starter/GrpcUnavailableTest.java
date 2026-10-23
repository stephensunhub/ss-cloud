package men.sbb8.web.starter;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import men.sbb8.web.starter.config.SsWebProperties;
import men.sbb8.web.starter.dto.resp.Resp;
import men.sbb8.web.starter.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 下游 gRPC 服务不可达（UNAVAILABLE）返回 502，超过 deadline（DEADLINE_EXCEEDED）返回 504，和 HTTP 下游一致；
 * 只打一行 WARN，不带堆栈；其它 gRPC 状态不受影响，仍是未知异常 500。
 */
class GrpcUnavailableTest {

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

    private static StatusRuntimeException status(Status status) {
        return new StatusRuntimeException(status);
    }

    @Test
    void unavailableIs502() {
        ResponseEntity<?> response = new GlobalExceptionHandler().handleException(status(Status.UNAVAILABLE.withDescription("io exception")));
        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertEquals("BAD_GATEWAY", body(response).getCode());
        assertEquals("下游服务异常", body(response).getMsg());
    }

    @Test
    void wrappedUnavailableIs502() {
        // ss-grpc-client-starter 的 GrpcRemoteException 把 StatusRuntimeException 作为 cause 包起来
        RuntimeException wrapped = new RuntimeException("调用下游失败", new RuntimeException("中间层", status(Status.UNAVAILABLE)));
        ResponseEntity<?> response = new GlobalExceptionHandler().handleException(wrapped);
        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
    }

    @Test
    void deadlineExceededIs504() {
        ResponseEntity<?> response = new GlobalExceptionHandler()
                .handleException(status(Status.DEADLINE_EXCEEDED.withDescription("deadline exceeded after 4.9s")));
        assertEquals(HttpStatus.GATEWAY_TIMEOUT, response.getStatusCode());
        assertEquals("GATEWAY_TIMEOUT", body(response).getCode());
        assertEquals("下游服务超时", body(response).getMsg());
    }

    @Test
    void wrappedDeadlineExceededIs504() {
        RuntimeException wrapped = new RuntimeException("调用下游失败", status(Status.DEADLINE_EXCEEDED));
        assertEquals(HttpStatus.GATEWAY_TIMEOUT, new GlobalExceptionHandler().handleException(wrapped).getStatusCode());
    }

    @Test
    void otherGrpcStatusesStayInternalServerError() {
        for (Status other : new Status[]{Status.INTERNAL, Status.NOT_FOUND, Status.UNKNOWN, Status.PERMISSION_DENIED}) {
            ResponseEntity<?> response = new GlobalExceptionHandler().handleException(status(other));
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode(), other.toString());
            assertEquals("SYSTEM_ERROR", body(response).getCode());
        }
    }

    @Test
    void doesNotLeakDownstreamDetails() {
        ResponseEntity<?> response = new GlobalExceptionHandler()
                .handleException(status(Status.UNAVAILABLE.withDescription("connect to 10.0.3.7:9001 failed")));
        assertEquals("下游服务异常", body(response).getMsg());
    }

    @Test
    void oneWarnLineWithoutStackTrace() {
        for (Status failure : new Status[]{Status.UNAVAILABLE, Status.DEADLINE_EXCEEDED}) {
            appender.list.clear();
            new GlobalExceptionHandler().handleException(status(failure));
            assertEquals(1, appender.list.size(), failure.toString());
            ILoggingEvent event = appender.list.get(0);
            assertEquals(Level.WARN, event.getLevel());
            assertNull(event.getThrowableProxy(), "下游宕机 / 抖动时会成批出现，不应每个都带堆栈");
        }
    }

    @Test
    void problemDetailFormatToo() {
        ResponseEntity<?> response = new GlobalExceptionHandler(SsWebProperties.ErrorFormat.PROBLEM_DETAIL)
                .handleException(status(Status.UNAVAILABLE));
        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        ProblemDetail problem = assertInstanceOf(ProblemDetail.class, response.getBody());
        assertEquals("BAD_GATEWAY", problem.getProperties().get("code"));

        ResponseEntity<?> timeout = new GlobalExceptionHandler(SsWebProperties.ErrorFormat.PROBLEM_DETAIL)
                .handleException(status(Status.DEADLINE_EXCEEDED));
        assertEquals(HttpStatus.GATEWAY_TIMEOUT, timeout.getStatusCode());
        assertEquals("GATEWAY_TIMEOUT", assertInstanceOf(ProblemDetail.class, timeout.getBody()).getProperties().get("code"));
    }
}
