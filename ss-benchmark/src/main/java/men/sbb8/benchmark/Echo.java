package men.sbb8.benchmark;

import io.grpc.MethodDescriptor;
import io.grpc.ServerServiceDefinition;
import io.grpc.stub.ServerCalls;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * 空处理器：原样返回请求，不含任何业务逻辑。Spring 版和原生 grpc-java 版共用同一份服务定义，
 * 这样两者的差别只来自框架。直接用字节数组做编解码，避免 protobuf 本身的开销混进来。
 *
 * @author Stephen
 */
final class Echo {

    static final MethodDescriptor.Marshaller<byte[]> BYTES = new MethodDescriptor.Marshaller<>() {
        @Override
        public InputStream stream(byte[] value) {
            return new ByteArrayInputStream(value);
        }

        @Override
        public byte[] parse(InputStream stream) {
            try {
                return stream.readAllBytes();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    };

    static final MethodDescriptor<byte[], byte[]> METHOD = MethodDescriptor.<byte[], byte[]>newBuilder()
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("bench.Echo/Echo")
            .setRequestMarshaller(BYTES)
            .setResponseMarshaller(BYTES)
            .build();

    static ServerServiceDefinition service() {
        return ServerServiceDefinition.builder("bench.Echo")
                .addMethod(METHOD, ServerCalls.asyncUnaryCall((request, observer) -> {
                    observer.onNext(request);
                    observer.onCompleted();
                })).build();
    }

    private Echo() {
    }
}
