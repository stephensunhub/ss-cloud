# ss-grpc-core

gRPC **服务端、客户端共用**的部分，不含 gRPC 服务端 / 客户端 starter，也没有 web 容器。
一般不直接引入，而是通过 `ss-grpc-server-starter` / `ss-grpc-client-starter` 传递引入。

包含：错误模型（`ErrorDetail` proto、`BusinessCode`、`GrpcBusinessException`、`GrpcRemoteException`）、
Prometheus 抓取端口、链路追踪与监控指标的默认配置。

## 原生镜像（GraalVM）

已用 GraalVM 25（Oracle GraalVM 25.0.4）实际编译并运行验证（服务端 + 客户端，含 protobuf `ErrorDetail`、
异常转换、健康检查、Prometheus 指标），结果与 JVM 模式一致。示例应用编译约 46 秒，启动 0.03 秒（JVM 模式约 0.8 秒）。

另已验证：只引入 `ss-grpc-client-starter` 的原生镜像（启动 0.014 秒）调用原生服务端，链路传递和 OTLP 导出正常
（同一个 traceId 同时出现在客户端和服务端导出的数据里）。

prod 档案编译出的镜像里，gRPC 反射服务已关闭（用 `grpcurl` 实测：`list` 返回 "server does not support the reflection API"，
而普通编译的镜像能列出所有服务；健康检查等其他功能不受影响）。要关闭反射，必须在**编译时**激活 prod，见下文。

starter 已内置所需提示：默认配置文件，以及 grpc-java 通过反射使用的 `LongAdder`（Boot / Spring gRPC 没有注册它，
缺了会在 gRPC 服务端 / 客户端启动时失败）。应用自己的 protobuf 消息和 gRPC 服务由 Spring gRPC 的 AOT 处理注册。


### 编译原生镜像的要求（已实测）

Spring AOT 在**编译期**就固定了 Bean 集合：凡是由 `@ConditionalOnProperty` 等属性条件决定是否创建的 Bean，
运行期再改属性**不会**增删它。只改值的属性（端口、URL、采样率等）不受影响。

- **编译时就要激活目标环境的 profile**，否则 prod 的默认值不会进入镜像（接口文档仍然暴露、独立管理端口不存在）：
  ```bash
  SPRING_PROFILES_ACTIVE=prod mvn -Pnative -DskipTests native:compile
  # 或 -Dspring-boot.aot.jvmArguments=-Dspring.profiles.active=prod
  ```
  注意：`-Dspring-boot.run.profiles`、`-Dspring-boot.aot.profiles`、`-Dspring.profiles.active` 直接写在 mvn 命令行上**都不会**传给 AOT 进程（已实测）。
  也就是说镜像是按环境编译的，prod 镜像用 prod 编译。
- **OTLP 导出器需要编译期就存在导出地址**，否则导出器不会被包含，运行期配了地址也不会导出（且没有任何报错）。
  做法：编译期放一个占位地址（如 `application-prod.properties` 里的
  `management.opentelemetry.tracing.export.otlp.endpoint=http://localhost:4318/v1/traces`），运行期再用环境变量或参数覆盖成真实地址。
- 同理，各类 `*.enabled` 开关要在编译期确定。已实测的例子：`springdoc.*.enabled`、独立管理端口，以及示例里自己写的
  `@ConditionalOnProperty` 开关，运行期设置都不生效。`ss.grpc.metrics.prometheus.enabled`
  同样如此，**已实测**（原生镜像里运行期设 `enabled=false`，9091 仍在监听）：要关闭抓取端口，请在编译期设置。

## 指标（Prometheus 抓取）

没有 web 容器，所以 starter 用 JDK 自带的 `HttpServer` 在独立端口提供 `/metrics`（默认 `:9091/metrics`），
由两个守护线程处理，不占用 gRPC 和业务线程。内容包括 JVM 指标和 Boot 内置的 gRPC 观测指标
（`grpc_server_seconds`、`grpc_client_seconds` 等，耗时已配置固定的 11 个桶（5ms–10s，可跨实例聚合分位数），所有指标带 `application` 标签）。

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ss.grpc.metrics.prometheus.enabled` | `true` | 设为 `false` 不监听任何 HTTP 端口 |
| `ss.grpc.metrics.prometheus.port` | `9091` | 0–65535，超出范围启动失败；端口被占用时也会失败并给出提示；`0` 为随机端口 |
| `ss.grpc.metrics.prometheus.path` | `/metrics` | 抓取路径，必须以 `/` 开头，否则启动失败 |

**与 `ss-web-starter` 在 prod 下的抓取地址一致**（都是 `:9091/metrics`，指标都带 `application` 标签、耗时桶一致），
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
  错误详情 `ErrorDetail`（code / msg / params）放在 trailers 里。参数会发给调用方，不要放敏感值；为不超出客户端默认 8KB 的 metadata 上限，参数最多 20 个、每个值最多 256 个字符（超出截断，以 `…` 结尾）
- 未知异常统一为 `INTERNAL` + `SYSTEM_ERROR`，不暴露细节；直接抛出的 `StatusRuntimeException`，
  调用方错误类（参数、不存在、权限等）原样返回，其余丢弃描述换成通用错误
- 客户端（`ss-grpc-client-starter`）用 `GrpcCalls.call(() -> stub.xxx(req))` 把失败还原为 `GrpcRemoteException`（带业务码）

