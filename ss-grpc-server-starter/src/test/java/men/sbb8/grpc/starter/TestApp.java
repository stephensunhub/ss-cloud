package men.sbb8.grpc.starter;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import men.sbb8.grpc.client.GrpcCalls;
import men.sbb8.grpc.core.error.BusinessCode;
import men.sbb8.grpc.core.error.CommonCode;
import men.sbb8.grpc.core.error.ErrorDetails;
import men.sbb8.grpc.core.error.GrpcBusinessException;
import men.sbb8.grpc.starter.test.GreeterGrpc;
import men.sbb8.grpc.starter.test.HelloReply;
import men.sbb8.grpc.starter.test.HelloRequest;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Service;

import java.util.Map;

@SpringBootApplication
class TestApp {

    enum DemoCode implements BusinessCode {
        NAME_TAKEN(Status.ALREADY_EXISTS, "名称 {{name}} 已被占用");

        private final Status status;
        private final String msg;

        DemoCode(Status status, String msg) {
            this.status = status;
            this.msg = msg;
        }

        @Override
        public Status status() {
            return status;
        }

        @Override
        public String code() {
            return name();
        }

        @Override
        public String msg() {
            return msg;
        }
    }

    @Service
    static class GreeterService extends GreeterGrpc.GreeterImplBase {

        private final io.micrometer.tracing.Tracer tracer;

        GreeterService(io.micrometer.tracing.Tracer tracer) {
            this.tracer = tracer;
        }

        @Override
        public void hello(HelloRequest request, StreamObserver<HelloReply> responseObserver) {
            switch (request.getName()) {
                case "business" -> throw new GrpcBusinessException(DemoCode.NAME_TAKEN, Map.of("name", "bob"));
                case "common" -> throw new GrpcBusinessException(CommonCode.PARAMS_ERROR);
                case "wrapped" -> throw new java.util.concurrent.CompletionException(
                        new GrpcBusinessException(DemoCode.NAME_TAKEN, Map.of("name", "wrapped")));
                case "wrapped-status" -> throw new java.util.concurrent.CompletionException(
                        Status.UNAVAILABLE.withDescription("jdbc:mysql://10.0.0.5").asRuntimeException());
                case "onerror" -> {
                    responseObserver.onError(new GrpcBusinessException(CommonCode.PARAMS_ERROR));
                    return;
                }
                case "leak-internal" -> throw Status.INTERNAL.withDescription("db password=xxx").asRuntimeException();
                case "slow" -> {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    responseObserver.onNext(HelloReply.newBuilder().setMessage("slow done").build());
                    responseObserver.onCompleted();
                }
                case "trace" -> {
                    // 服务端当前 span 所在的 trace，用来验证调用链是否从客户端延续过来
                    responseObserver.onNext(HelloReply.newBuilder()
                            .setMessage(tracer.currentSpan().context().traceId()).build());
                    responseObserver.onCompleted();
                }
                case "boom" -> throw new IllegalStateException("secret internal detail");
                case "status" -> throw Status.NOT_FOUND.withDescription("raw").asRuntimeException();
                case "downstream-business" -> throw remote(ErrorDetails.toStatusRuntimeException(
                        DemoCode.NAME_TAKEN, Map.of("name", "alice")));
                case "downstream-internal" -> throw remote(ErrorDetails.toStatusRuntimeException(
                        CommonCode.SYSTEM_ERROR, Map.of()));
                case "downstream-plain" -> throw remote(Status.INVALID_ARGUMENT.asRuntimeException());
                default -> {
                    responseObserver.onNext(HelloReply.newBuilder().setMessage("hi " + request.getName()).build());
                    responseObserver.onCompleted();
                }
            }
        }

        private static RuntimeException remote(StatusRuntimeException failure) {
            return GrpcCalls.call(() -> {
                throw failure;
            });
        }
    }
}
