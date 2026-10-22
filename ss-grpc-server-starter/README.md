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


存活 / 就绪状态一变化（启动完成、优雅停机开始等），会**立即**同步到 gRPC 健康服务，不等定时同步。
所以启动完成后马上可探测，停机时就绪状态翻转后 k8s 马上停止派发流量。

## 服务端异常转换

- `GrpcBusinessException(业务码, 参数)` 转成该业务码对应的 gRPC 状态，`ErrorDetail` 放在 trailers 里；
  被 `CompletionException` 等包装时沿 cause 链查找
- 未知异常统一为 `INTERNAL` + `SYSTEM_ERROR`，不暴露细节
- 直接抛出的 `StatusRuntimeException`：带 `ErrorDetail` 或属于调用方错误类（参数、不存在、权限等）的原样返回，
  其余丢弃描述换成通用错误
- 下游暂时不可用（`UNAVAILABLE`、`DEADLINE_EXCEEDED`）只打一行 WARN、不带堆栈，避免下游宕机时日志刷屏；其他被隐藏的内部错误仍是 ERROR 并带堆栈
- 处理器的先后：应用自己的（`@GrpcAdvice` 等）→ 业务异常处理 → 兜底，前面的先处理
- 应用写 `@GrpcAdvice` 时，**类和方法都必须是 `public`**，否则 Spring gRPC 反射调用失败，会被悄悄吞成通用的 `INTERNAL`

## 失败调用的可观测性

异常拦截器放在观测拦截器的紧邻内侧（顺序 1）。Boot 默认把它放在最外层，会导致服务端指标把失败调用都记成 `UNKNOWN`
（客户端看到的却是真实状态），异常处理时打的日志也没有追踪号。现在服务端指标里是真实的 gRPC 状态
（`grpc.server` 的 `grpc.status_code` 标签），异常日志带追踪号。

顺序更靠后的拦截器（如 Spring Security 的认证拦截器）仍在它的内侧，它们抛出的异常照样会被这里的处理器接住（按顺序推断，未用 Spring Security 实测）。
如果应用自己加了顺序小于 1 的拦截器，它抛出的异常不会经过这里的处理器。

## 生产环境默认值

激活 `prod` / `production` 时：关闭 gRPC 反射服务（会暴露所有接口定义）；
另见 `ss-grpc-core` 的追踪采样率与结构化日志默认值。
