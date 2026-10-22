# ss-grpc-client-starter

gRPC **客户端**规范 starter。引入后获得：调用失败还原为带业务码的异常、一元调用默认超时，
并包含 `ss-grpc-core` 的错误模型、指标与链路追踪（见 `ss-grpc-core/README.md`）。不带服务端。

## 失败还原

用 `GrpcCalls.call(() -> stub.xxx(req))`（无返回值用 `GrpcCalls.run`）调用，失败时 `StatusRuntimeException`
会转成 `GrpcRemoteException`：`getStatus()` 是 gRPC 状态，`getCode()` / `getRemoteMsg()` 是下游返回的业务码和消息
（下游没有返回详情时为 `null`）。注意这不是自动生效的，需要业务代码主动用它包住 stub 调用。

## 客户端默认超时

一元调用没有显式 deadline 时使用 `ss.grpc.client.default-deadline`（默认 30s，0 关闭）。
流式调用不受影响；调用方自己设置的 deadline 优先。

## 服务发现（`discovery:///服务名`）

应用引入 Spring Cloud 的服务发现实现（如 `spring-cloud-starter-consul-discovery`，会带来 `spring-cloud-commons`）后，
gRPC 目标写作 `discovery:///order-service`，实例列表来自 `DiscoveryClient`，因此注册中心可以是 Consul、Nacos 等任何
Spring Cloud 支持的实现。没有 Spring Cloud 时整块不生效。scheme 与 Spring Cloud 社区提案
（[#1675](https://github.com/spring-cloud/spring-cloud-commons/issues/1675)）一致，官方出了正式实现后可以平滑迁移。

```properties
spring.grpc.client.channels.order.address=discovery:///order-service
```

- **请求路径不查注册中心。** 解析器把地址列表推给负载均衡策略（默认 `round_robin`），每个 RPC 在本地挑选连接，
  注册中心的压力只和「应用实例数 × 关注的服务数」有关，与 QPS、stream 数量无关。
- **刷新**：Spring Cloud 发布 `HeartbeatEvent` 时刷新（Consul 约每 3 秒一次）；gRPC 连接失败时自己触发刷新；
  另有兜底定时器 `ss.grpc.client.discovery.refresh-interval`（默认 10s），只在事件缺失时起作用。
  同一服务同一时刻只有一次查询在进行，多余的触发被合并。
- **列表没有变化不推送**；变化后新增实例建连，消失的实例由负载均衡策略关闭。
- **注册中心故障**：已有列表时保留不动，服务照常调用现有实例；从未成功查询过则报 `UNAVAILABLE`，调用快速失败。
- **查询成功但没有实例**：如实推送空列表，调用报 `UNAVAILABLE`。
- **端口**：优先取实例 metadata 的 `gRPC_port`，没有才用实例自身的端口。gRPC 服务通常没有 HTTP 端口，
  注册时要把 gRPC 端口写进 metadata，例如 Consul：`spring.cloud.consul.discovery.metadata.gRPC_port=9090`。
- **Consul 要求** `spring.cloud.consul.discovery.query-passing=true`，否则健康检查失败的实例也会返回。
- **Consul 要求 Jackson 3**：应用必须加依赖 `tools.jackson.core:jackson-databind`。Spring Cloud Consul 用 `RestClient`
  解析 Consul 的 JSON，类路径上没有 Jackson 时退回 `spring-cloud-starter-consul` 自带的 Gson；Gson 按字段名精确匹配，
  对不上 Consul 返回的 `"Service"`，`DiscoveryClient.getInstances` 每次都会 `NullPointerException`。
  这与 gRPC 本身无关（gRPC 走 protobuf），本 starter 也不依赖 Jackson；类路径上有 Consul 却没有 Jackson 时，
  应用启动会直接失败并给出这条提示。
- **纯 gRPC 应用（没有 web 容器）要关闭 Consul 的自动注册**：`spring.cloud.service-registry.auto-registration.enabled=false`，
  否则启动时 `NoClassDefFoundError: WebServerInitializedEvent`（Consul 的自动注册类引用了 Boot 的 web server 事件）。
  这只影响调用方；纯 gRPC 服务端如何用 Spring Cloud Consul 自动注册，目前还没有验证过。

### 限制

- **只能在 JVM 上用**：Spring Cloud Consul / Nacos 目前不支持原生镜像，应用构建原生镜像时不要引入。
- **变更感知有延迟**：约一个心跳周期（Consul 约 3 秒）。发布时服务端应先注销、等待排空再停止，客户端配合重试，
  否则这几秒内可能有请求打到已下线的实例。
- 一个后端地址只建一条连接。流量集中在少数调用方实例上时，单条 HTTP/2 连接可能成为瓶颈，此时需要压测确认。
- 已在本地 Consul 1.20.6 上验证过：多实例轮询、健康检查失败剔除、新增与注销实例、Consul 不可达时保留旧列表并恢复，
  所有场景没有请求失败；持续 13 万次调用期间 Consul 的请求数与空闲时相同。实例变化约 1.5 到 2 秒内生效（每个场景只测了一次）。
  **没有验证**：进程被强杀或网络分区（连接不会正常关闭）、TLS、多主机、压测，以及 Nacos。
