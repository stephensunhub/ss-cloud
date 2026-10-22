# ss-grpc-core

gRPC **服务端、客户端共用**的部分，不含 gRPC 服务端 / 客户端 starter，也没有 web 容器。
一般不直接引入，而是通过 `ss-grpc-server-starter` / `ss-grpc-client-starter` 传递引入。

包含：错误模型（`ErrorDetail` proto、`BusinessCode`、`GrpcBusinessException`、`GrpcRemoteException`）、
Prometheus 抓取端口、链路追踪与监控指标的默认配置。

## 指标（Prometheus 抓取）

没有 web 容器，所以 starter 用 JDK 自带的 `HttpServer` 在独立端口提供 `/metrics`（默认 `:9091/metrics`），
由两个守护线程处理，不占用 gRPC 和业务线程。内容包括 JVM 指标和 Boot 内置的 gRPC 观测指标
（`grpc_server_seconds`、`grpc_client_seconds` 等，已开启直方图，所有指标带 `application` 标签）。

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ss.grpc.metrics.prometheus.enabled` | `true` | 设为 `false` 不监听任何 HTTP 端口 |
| `ss.grpc.metrics.prometheus.port` | `9091` | 端口被占用时启动会失败并给出提示；`0` 为随机端口 |
| `ss.grpc.metrics.prometheus.path` | `/metrics` | 抓取路径 |

**与 `ss-web-starter` 在 prod 下的抓取地址一致**（都是 `:9091/metrics`，指标都带 `application` 标签、耗时都有直方图），
Prometheus 对所有服务可以用同一份抓取配置。

端口只是 Pod 内的端口；用 ServiceMonitor / PodMonitor 或 Pod 注解让 Prometheus 抓取。
链路追踪（OTLP）与此无关，需要时另行引入导出器。


## 链路追踪（OTLP → Jaeger / Tempo / OTel Collector）

已内置 Micrometer Tracing 的 OpenTelemetry 桥接和 OTLP/HTTP 导出器（用 JDK 自带的 HTTP 客户端发送，不依赖 OkHttp）。
**没有配置导出地址时不会创建导出器，也不会有连接错误**；配置地址后自动开始导出：

```yaml
management:
  opentelemetry:
    tracing:
      export:
        otlp:
          endpoint: http://jaeger:4318/v1/traces   # OTLP/HTTP 端口；Collector、Tempo 同理
```

也可以不改代码，用环境变量（Boot 会映射 OpenTelemetry 标准变量）：
`OTEL_EXPORTER_OTLP_ENDPOINT`、`OTEL_EXPORTER_OTLP_HEADERS` 等。服务名取自 `spring.application.name`。

- 采样率：默认 `1.0`（开发、测试全量），激活 `prod` / `production` 时默认 `0.1`，用 `management.tracing.sampling.probability` 调整
- 调用链会通过 gRPC metadata（W3C traceparent） 自动延续到下游，下游的 span 与上游在同一个 trace 里
- 日志里自动带 traceId / spanId；gRPC 错误详情里不带 traceId，排查时用日志里的 traceId
- 注意是头部采样：未被采样的请求也会有 traceId，但在 Jaeger 里查不到。需要"出错必留"请在 Collector 里做尾部采样


## 错误模型

- 服务端（`ss-grpc-server-starter`）抛 `GrpcBusinessException(业务码, 参数)`，转成该业务码对应的 gRPC 状态，
  错误详情 `ErrorDetail`（code / msg / params）放在 trailers 里。参数会原样发给调用方，不要放敏感值
- 未知异常统一为 `INTERNAL` + `SYSTEM_ERROR`，不暴露细节；直接抛出的 `StatusRuntimeException`，
  调用方错误类（参数、不存在、权限等）原样返回，其余丢弃描述换成通用错误
- 客户端（`ss-grpc-client-starter`）用 `GrpcCalls.call(() -> stub.xxx(req))` 把失败还原为 `GrpcRemoteException`（带业务码）

