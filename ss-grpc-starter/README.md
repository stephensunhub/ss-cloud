# ss-grpc-starter

引入依赖即可获得：统一错误模型、服务端全局异常转换、客户端错误还原、客户端默认超时、健康检查默认配置。

## 健康检查（k8s gRPC 探针）

本 starter **不带 web 容器**，没有 HTTP 健康端点。健康检查使用 gRPC 原生的 `grpc.health.v1`，
状态由 actuator 的健康贡献者汇总，约每 5 秒同步一次（`spring.grpc.server.health.schedule.period`）。

```yaml
livenessProbe:
  grpc:
    port: 9001
    service: liveness
readinessProbe:
  grpc:
    port: 9001
    service: readiness
```

- `liveness`：只反映进程自身状态（`livenessState`），不含外部依赖，避免依赖故障引发整批重启
- `readiness`：是否接收流量（`readinessState`），优雅停机时会先变为未就绪
- 服务名留空表示整体健康（所有健康贡献者聚合）
- 要把数据库等依赖纳入就绪：`spring.grpc.server.health.service.readiness.include` 追加贡献者名
  （如 `db`、`redis`）；名称必须存在，启动时会校验

注意：k8s 的 gRPC 探针要求 k8s 1.24+，端口只能写数字，且不支持 TLS，探针端口须是明文端口。

## 指标（Prometheus 抓取）

没有 web 容器，所以 starter 用 JDK 自带的 `HttpServer` 在独立端口提供 `/metrics`（默认 `:9091/metrics`），
由两个守护线程处理，不占用 gRPC 和业务线程。内容包括 JVM 指标和 Boot 内置的 gRPC 观测指标
（`grpc_server_seconds`、`grpc_client_seconds` 等，已开启直方图，所有指标带 `application` 标签）。

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ss.grpc.metrics.prometheus.enabled` | `true` | 设为 `false` 不监听任何 HTTP 端口 |
| `ss.grpc.metrics.prometheus.port` | `9091` | 端口被占用时启动会失败并给出提示；`0` 为随机端口 |
| `ss.grpc.metrics.prometheus.path` | `/metrics` | 抓取路径 |

端口只是 Pod 内的端口；用 ServiceMonitor / PodMonitor 或 Pod 注解让 Prometheus 抓取。
链路追踪（OTLP）与此无关，需要时另行引入导出器。

## 错误模型

- 服务端抛 `GrpcBusinessException(业务码, 参数)`，转成该业务码对应的 gRPC 状态，
  错误详情 `ErrorDetail`（code / msg / params）放在 trailers 里。参数会原样发给调用方，不要放敏感值
- 未知异常统一为 `INTERNAL` + `SYSTEM_ERROR`，不暴露细节；直接抛出的 `StatusRuntimeException`，
  调用方错误类（参数、不存在、权限等）原样返回，其余丢弃描述换成通用错误
- 客户端用 `GrpcCalls.call(() -> stub.xxx(req))` 把失败还原为 `GrpcRemoteException`（带业务码）

## 客户端默认超时

一元调用没有显式 deadline 时使用 `ss.grpc.client.default-deadline`（默认 30s，0 关闭）。
流式调用不受影响；调用方自己设置的 deadline 优先。
