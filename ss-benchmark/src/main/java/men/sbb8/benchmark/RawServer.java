package men.sbb8.benchmark;

import io.grpc.Server;
import io.grpc.netty.NettyServerBuilder;

/**
 * 对照组：没有 Spring、没有任何观测的原生 grpc-java 服务端。端口用 {@code -Dbench.port} 修改
 *
 * @author Stephen
 */
public final class RawServer {

    private RawServer() {
    }

    public static void main(String[] args) throws Exception {
        Server server = NettyServerBuilder.forPort(Integer.getInteger("bench.port", 19101))
                .addService(Echo.service()).build().start();
        System.out.println("RawServer started on " + server.getPort());
        server.awaitTermination();
    }
}
