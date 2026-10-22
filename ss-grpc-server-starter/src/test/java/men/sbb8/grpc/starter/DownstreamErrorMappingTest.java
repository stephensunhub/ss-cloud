package men.sbb8.grpc.starter;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import men.sbb8.grpc.core.error.ErrorDetails;
import men.sbb8.grpc.starter.test.GreeterGrpc;
import men.sbb8.grpc.starter.test.HelloRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.grpc.test.autoconfigure.AutoConfigureTestGrpcTransport;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.grpc.client.ImportGrpcClients;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 下游不可用 / 内部错误对外的状态与不泄露（日志级别见 HandlerLogLevelTest）
 */
@SpringBootTest
@AutoConfigureTestGrpcTransport
@ImportGrpcClients(types = GreeterGrpc.GreeterBlockingStub.class)
class DownstreamErrorMappingTest {

    @Autowired
    GreeterGrpc.GreeterBlockingStub stub;

    private StatusRuntimeException call(String name) {
        return assertThrows(StatusRuntimeException.class, () -> stub.hello(HelloRequest.newBuilder().setName(name).build()));
    }

    @Test
    void unavailableDownstreamIsHiddenBehindAGenericError() {
        StatusRuntimeException e = call("downstream-unavailable");
        assertEquals(Status.Code.UNAVAILABLE, e.getStatus().getCode());
        assertEquals("DOWNSTREAM_ERROR", ErrorDetails.find(e).orElseThrow().getCode());
    }

    @Test
    void rawUnavailableStatusDoesNotLeakInternalAddresses() {
        StatusRuntimeException e = call("raw-unavailable");
        assertEquals(Status.Code.UNAVAILABLE, e.getStatus().getCode());
        assertFalse(String.valueOf(e.getStatus().getDescription()).contains("10.0.0.9"), "不应泄露内部地址");
    }

    @Test
    void downstreamInternalErrorIsHidden() {
        StatusRuntimeException e = call("downstream-internal");
        assertEquals(Status.Code.UNAVAILABLE, e.getStatus().getCode());
        assertEquals("DOWNSTREAM_ERROR", ErrorDetails.find(e).orElseThrow().getCode());
    }
}
