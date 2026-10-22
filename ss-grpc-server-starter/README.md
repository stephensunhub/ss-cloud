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

## 连接最长存活时间与滚动发布（k8s headless Service）

默认 `spring.grpc.server.keepalive.connection.max-age: 10m`（Boot 默认是无限）。到期后服务端优雅地关闭连接
（GOAWAY，`spring.grpc.server.keepalive.connection.grace-period` 默认 30s 内让进行中的请求完成），客户端随之**重新解析地址并重连**。

**为什么必须设**：k8s 用 headless Service 做服务发现时，gRPC 客户端的 DNS 解析只在连接断开时才重新查询。
不设的话，新扩容的 pod 要等到有连接因故障断开才会被客户端发现，一直没有流量，也无法把负载重新摊平。
`ConnectionRecyclingTest` 在真实网络上验证了这一点：设了 max-age，客户端的解析器被要求重新解析，连接恢复后调用正常；
不设就不会重新解析。

怎么取舍：

- 新 pod 被发现的延迟约等于 max-age：10 分钟是折中，扩缩容很频繁的服务可以调小（连接重建的代价很小）
- **长连接的流式调用会在 `max-age + grace-period` 后被取消**（默认约 10 分半）。长时间订阅类的服务请把 max-age 调大
  （如 `1h`），并让客户端在流被取消后重新订阅；想"关闭"不能设成 0，gRPC 不接受（`max connection age must be positive`）
- 调用方依旧要配 `dns:///` 目标和 `round_robin`，见 `ss-grpc-client-starter/README.md`

滚动发布时不丢请求，还需要 k8s 这一侧配合（k8s 的一般做法，**没有在真实集群里验证过**）：

- 就绪状态在停机开始时立即翻转（见上文"健康检查"），k8s 马上停止派发新流量
- pod 从 Service 的地址里摘除并传播到客户端的 DNS 缓存需要几秒（CoreDNS 默认缓存约 5 秒），期间客户端仍可能把请求发过来，
  所以在 `preStop` 里先等待几秒再让进程收到 SIGTERM：`lifecycle.preStop.exec.command: ["sleep", "10"]`
  （镜像里要有 `sleep`；较新的 k8s 版本支持不依赖镜像命令的 `preStop.sleep`）
- `terminationGracePeriodSeconds` 要大于 preStop 的等待时间加上优雅停机时间（`spring.lifecycle.timeout-per-shutdown-phase`，
  默认 30s），否则进程会被强制杀死

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

异常拦截器只处理它内侧的拦截器和服务方法抛出的异常；应用自己加了顺序小于 1 的拦截器（比它更靠外）时，那个拦截器抛出的异常不会经过这里的处理器。

## 生产环境默认值

激活 `prod` / `production` 时：关闭 gRPC 反射服务（会暴露所有接口定义）；
另见 `ss-grpc-core` 的追踪采样率与结构化日志默认值。
