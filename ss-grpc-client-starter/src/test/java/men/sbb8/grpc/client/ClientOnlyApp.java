package men.sbb8.grpc.client;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 只引入 client starter 的应用：用来证明客户端不会带上服务端的东西
 */
@SpringBootApplication
class ClientOnlyApp {
}
