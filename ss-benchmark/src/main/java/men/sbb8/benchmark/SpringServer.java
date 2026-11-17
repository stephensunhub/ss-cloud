package men.sbb8.benchmark;

import io.grpc.BindableService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * 使用 ss-grpc-server-starter 的服务端。观测、采样率等开关用 {@code -D} 系统属性切换，
 * 例如 {@code -Dmanagement.tracing.sampling.probability=0.1}、{@code -Dmanagement.tracing.enabled=false}
 *
 * @author Stephen
 */
@SpringBootApplication
public class SpringServer {

    @Bean
    BindableService echoService() {
        return Echo::service;
    }

    public static void main(String[] args) {
        SpringApplication.run(SpringServer.class, args);
        System.out.println("SpringServer started");
    }
}
