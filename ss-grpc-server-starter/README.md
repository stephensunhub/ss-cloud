# ss-grpc-server-starter

gRPC **服务端**规范 starter。引入后获得：全局异常转换、健康检查（存活 / 就绪）默认配置，
并包含 `ss-grpc-core` 的错误模型、指标与链路追踪（见 `ss-grpc-core/README.md`）。不带 web 容器。

只做客户端的服务引入 `ss-grpc-client-starter` 即可，不需要它。

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


## 服务端异常转换

- `GrpcBusinessException(业务码, 参数)` 转成该业务码对应的 gRPC 状态，`ErrorDetail` 放在 trailers 里；
  被 `CompletionException` 等包装时沿 cause 链查找
- 未知异常统一为 `INTERNAL` + `SYSTEM_ERROR`，不暴露细节
- 直接抛出的 `StatusRuntimeException`：带 `ErrorDetail` 或属于调用方错误类（参数、不存在、权限等）的原样返回，
  其余丢弃描述换成通用错误
- 应用自己的 `@GrpcAdvice` 优先于 starter 的处理器

## 生产环境默认值

激活 `prod` / `production` 时：关闭 gRPC 反射服务（会暴露所有接口定义）；
另见 `ss-grpc-core` 的追踪采样率与结构化日志默认值。
