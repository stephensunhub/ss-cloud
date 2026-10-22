package men.sbb8.grpc.starter.metrics;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * 在独立端口上提供 Prometheus 抓取（默认 {@code :9091/metrics}）。
 * gRPC 服务不带 web 容器，所以用 JDK 自带的 {@link HttpServer}：没有 Tomcat / Spring MVC，也不占用 gRPC 的线程；
 * 抓取由单独的两个守护线程处理，不影响业务线程。
 *
 * @author Stephen
 */
@Slf4j
public class PrometheusScrapeServer implements SmartLifecycle {

    private static final String CONTENT_TYPE = "text/plain; version=0.0.4; charset=utf-8";

    private final Supplier<PrometheusMeterRegistry> registrySupplier;
    private volatile PrometheusMeterRegistry registry;
    private final int configuredPort;
    private final String path;

    private volatile HttpServer server;
    private ExecutorService executor;

    /**
     * @param registrySupplier 启动时才取注册表，取不到（没有 Prometheus 注册表）就不启动抓取服务
     */
    public PrometheusScrapeServer(Supplier<PrometheusMeterRegistry> registrySupplier, int port, String path) {
        this.registrySupplier = registrySupplier;
        this.configuredPort = port;
        this.path = path;
    }

    @Override
    public void start() {
        registry = registrySupplier.get();
        if (registry == null) {
            log.info("没有 PrometheusMeterRegistry，不启动 Prometheus 抓取服务");
            return;
        }
        AtomicInteger counter = new AtomicInteger();
        try {
            HttpServer httpServer = HttpServer.create(new InetSocketAddress(configuredPort), 0);
            httpServer.createContext("/", this::handle);
            executor = Executors.newFixedThreadPool(2, runnable -> {
                Thread thread = new Thread(runnable, "ss-metrics-" + counter.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            });
            httpServer.setExecutor(executor);
            httpServer.start();
            server = httpServer;
        } catch (IOException e) {
            throw new IllegalStateException("无法在端口 " + configuredPort + " 启动 Prometheus 抓取服务，"
                    + "可通过 ss.grpc.metrics.prometheus.port 修改端口，或 ss.grpc.metrics.prometheus.enabled=false 关闭", e);
        }
        log.info("Prometheus 抓取服务已启动: :{}{}", getPort(), path);
    }

    @Override
    public void stop() {
        HttpServer httpServer = server;
        if (httpServer != null) {
            httpServer.stop(0);
            executor.shutdown();
            server = null;
        }
    }

    @Override
    public boolean isRunning() {
        return server != null;
    }

    /**
     * 实际监听的端口（配置为 0 时是系统分配的随机端口）
     */
    public int getPort() {
        HttpServer httpServer = server;
        return httpServer == null ? configuredPort : httpServer.getAddress().getPort();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!path.equals(exchange.getRequestURI().getPath())) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", "GET");
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            byte[] body;
            try {
                body = registry.scrape().getBytes(StandardCharsets.UTF_8);
            } catch (RuntimeException e) {
                log.error("生成 Prometheus 指标失败", e);
                exchange.sendResponseHeaders(500, -1);
                return;
            }
            exchange.getResponseHeaders().set("Content-Type", CONTENT_TYPE);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        }
    }
}
