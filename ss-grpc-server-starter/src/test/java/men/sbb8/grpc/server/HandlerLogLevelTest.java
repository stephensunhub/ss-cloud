package men.sbb8.grpc.server;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.grpc.Status;
import men.sbb8.grpc.core.error.CommonCode;
import men.sbb8.grpc.core.error.ErrorDetails;
import men.sbb8.grpc.core.error.GrpcRemoteException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 日志级别：下游暂时不可用只打一行 WARN、不带堆栈，避免下游宕机时刷屏；真正的内部错误是 ERROR 并带堆栈。
 * 直接检查日志事件，与控制台输出格式（文本 / ECS JSON）无关。
 */
class HandlerLogLevelTest {

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Logger businessLogger = (Logger) LoggerFactory.getLogger(BusinessExceptionHandler.class);
    private final Logger fallbackLogger = (Logger) LoggerFactory.getLogger(FallbackExceptionHandler.class);

    @BeforeEach
    void attach() {
        appender.start();
        businessLogger.addAppender(appender);
        fallbackLogger.addAppender(appender);
    }

    @AfterEach
    void detach() {
        businessLogger.detachAppender(appender);
        fallbackLogger.detachAppender(appender);
        appender.stop();
    }

    private ILoggingEvent onlyEvent() {
        List<ILoggingEvent> events = appender.list;
        assertEquals(1, events.size(), events.toString());
        return events.get(0);
    }

    private static GrpcRemoteException remote(Status status) {
        return new GrpcRemoteException(status.asRuntimeException());
    }

    @Test
    void transientRemoteFailureIsOneWarnWithoutStackTrace() {
        for (Status.Code code : new Status.Code[]{Status.Code.UNAVAILABLE, Status.Code.DEADLINE_EXCEEDED}) {
            appender.list.clear();
            new BusinessExceptionHandler().handleException(remote(code.toStatus()));
            ILoggingEvent event = onlyEvent();
            assertEquals(Level.WARN, event.getLevel(), code.toString());
            assertNull(event.getThrowableProxy(), code + " 不应带堆栈");
        }
    }

    @Test
    void otherRemoteFailuresAreErrorsWithStackTrace() {
        new BusinessExceptionHandler().handleException(remote(Status.INTERNAL));
        ILoggingEvent event = onlyEvent();
        assertEquals(Level.ERROR, event.getLevel());
        assertNotNull(event.getThrowableProxy());
    }

    @Test
    void hiddenTransientStatusIsOneWarnWithoutStackTrace() {
        new FallbackExceptionHandler().handleException(Status.UNAVAILABLE.withDescription("10.0.0.9").asRuntimeException());
        ILoggingEvent event = onlyEvent();
        assertEquals(Level.WARN, event.getLevel());
        assertNull(event.getThrowableProxy());
    }

    @Test
    void hiddenInternalStatusAndUnknownExceptionsAreErrorsWithStackTrace() {
        new FallbackExceptionHandler().handleException(Status.INTERNAL.withDescription("db password").asRuntimeException());
        ILoggingEvent hidden = onlyEvent();
        assertEquals(Level.ERROR, hidden.getLevel());
        assertNotNull(hidden.getThrowableProxy());

        appender.list.clear();
        new FallbackExceptionHandler().handleException(new IllegalStateException("boom"));
        ILoggingEvent unknown = onlyEvent();
        assertEquals(Level.ERROR, unknown.getLevel());
        assertNotNull(unknown.getThrowableProxy());
    }

    @Test
    void callerErrorsAreNotLoggedAsErrors() {
        // 带 ErrorDetail 的调用方错误透传，只是 WARN
        GrpcRemoteException callerError = new GrpcRemoteException(
                ErrorDetails.toStatusRuntimeException(CommonCode.PARAMS_ERROR, Map.of()));
        new BusinessExceptionHandler().handleException(callerError);
        assertEquals(Level.WARN, onlyEvent().getLevel());
    }
}
