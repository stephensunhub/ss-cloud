package men.sbb8.grpc.server;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import men.sbb8.grpc.client.GrpcCalls;
import men.sbb8.grpc.core.error.ErrorDetails;
import men.sbb8.grpc.core.error.GrpcRemoteException;
import men.sbb8.grpc.core.proto.ErrorDetail;
import men.sbb8.grpc.server.test.GreeterGrpc;
import men.sbb8.grpc.server.test.HelloRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.grpc.test.autoconfigure.AutoConfigureTestGrpcTransport;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.grpc.client.ImportGrpcClients;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@AutoConfigureTestGrpcTransport
@ImportGrpcClients(types = GreeterGrpc.GreeterBlockingStub.class)
class GrpcStarterTest {

    @Autowired
    GreeterGrpc.GreeterBlockingStub stub;

    @Autowired
    Environment env;

    private StatusRuntimeException fail(String name) {
        return assertThrows(StatusRuntimeException.class,
                () -> stub.hello(HelloRequest.newBuilder().setName(name).build()));
    }

    @Test
    void successPassesThrough() {
        assertEquals("hi tom", stub.hello(HelloRequest.newBuilder().setName("tom").build()).getMessage());
    }

    @Test
    void businessExceptionBecomesStatusWithRenderedDetail() {
        StatusRuntimeException e = fail("business");
        assertEquals(Status.Code.ALREADY_EXISTS, e.getStatus().getCode());
        ErrorDetail detail = ErrorDetails.find(e).orElseThrow();
        assertEquals("NAME_TAKEN", detail.getCode());
        assertEquals("名称 bob 已被占用", detail.getMsg());
        assertEquals("bob", detail.getParamsMap().get("name"));
        assertEquals("名称 bob 已被占用", e.getStatus().getDescription());
    }

    @Test
    void commonCodeUsesItsOwnStatus() {
        StatusRuntimeException e = fail("common");
        assertEquals(Status.Code.INVALID_ARGUMENT, e.getStatus().getCode());
        assertEquals("PARAMS_ERROR", ErrorDetails.find(e).orElseThrow().getCode());
    }

    @Test
    void unknownExceptionDoesNotLeakDetails() {
        StatusRuntimeException e = fail("boom");
        assertEquals(Status.Code.INTERNAL, e.getStatus().getCode());
        assertEquals("SYSTEM_ERROR", ErrorDetails.find(e).orElseThrow().getCode());
        assertFalse(String.valueOf(e.getStatus().getDescription()).contains("secret"));
    }

    @Test
    void explicitStatusIsKept() {
        StatusRuntimeException e = fail("status");
        assertEquals(Status.Code.NOT_FOUND, e.getStatus().getCode());
        assertEquals("raw", e.getStatus().getDescription());
    }

    @Test
    void downstreamCallerErrorsPassThrough() {
        StatusRuntimeException e = fail("downstream-business");
        assertEquals(Status.Code.ALREADY_EXISTS, e.getStatus().getCode());
        assertEquals("名称 alice 已被占用", ErrorDetails.find(e).orElseThrow().getMsg());
    }

    @Test
    void downstreamInternalErrorsAreHidden() {
        for (String name : new String[]{"downstream-internal", "downstream-plain"}) {
            StatusRuntimeException e = fail(name);
            assertEquals(Status.Code.UNAVAILABLE, e.getStatus().getCode());
            assertEquals("DOWNSTREAM_ERROR", ErrorDetails.find(e).orElseThrow().getCode());
        }
    }

    @Test
    void businessExceptionWrappedInAnotherExceptionIsStillRecognised() {
        StatusRuntimeException e = fail("wrapped");
        assertEquals(Status.Code.ALREADY_EXISTS, e.getStatus().getCode());
        assertEquals("名称 wrapped 已被占用", ErrorDetails.find(e).orElseThrow().getMsg());
    }

    @Test
    void onErrorWithBusinessExceptionIsConverted() {
        StatusRuntimeException e = fail("onerror");
        assertEquals(Status.Code.INVALID_ARGUMENT, e.getStatus().getCode());
        assertEquals("PARAMS_ERROR", ErrorDetails.find(e).orElseThrow().getCode());
    }

    @Test
    void rawServerSideStatusesDoNotLeakDescriptions() {
        StatusRuntimeException internal = fail("leak-internal");
        assertEquals(Status.Code.INTERNAL, internal.getStatus().getCode());
        assertEquals("SYSTEM_ERROR", ErrorDetails.find(internal).orElseThrow().getCode());
        assertFalse(String.valueOf(internal.getStatus().getDescription()).contains("password"));

        StatusRuntimeException unavailable = fail("wrapped-status");
        assertEquals(Status.Code.UNAVAILABLE, unavailable.getStatus().getCode());
        assertEquals("DOWNSTREAM_ERROR", ErrorDetails.find(unavailable).orElseThrow().getCode());
        assertFalse(String.valueOf(unavailable.getStatus().getDescription()).contains("jdbc"));
    }

    @Test
    void clientSideTranslation() {
        GrpcRemoteException e = assertThrows(GrpcRemoteException.class,
                () -> GrpcCalls.call(() -> stub.hello(HelloRequest.newBuilder().setName("business").build())));
        assertEquals(Status.Code.ALREADY_EXISTS, e.getStatus().getCode());
        assertEquals("NAME_TAKEN", e.getCode());
        assertEquals("名称 bob 已被占用", e.getRemoteMsg());

        GrpcRemoteException plain = assertThrows(GrpcRemoteException.class,
                () -> GrpcCalls.run(() -> {
                    throw Status.DEADLINE_EXCEEDED.asRuntimeException();
                }));
        assertNull(plain.getCode());
        assertEquals(Status.Code.DEADLINE_EXCEEDED, plain.getStatus().getCode());
    }

    @Test
    void renderTemplate() {
        assertEquals("a-1-", ErrorDetails.render("a-{{x}}-{{missing}}", Map.of("x", 1)));
        assertEquals("no params", ErrorDetails.render("no params", Map.of()));
    }

    @Test
    void defaultsApplied() {
        assertEquals("9001", env.getProperty("spring.grpc.server.port"));
        assertEquals("1.0", env.getProperty("management.tracing.sampling.probability"));
    }
}
