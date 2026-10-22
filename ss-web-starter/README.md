# ss-web-starter

引入依赖即可获得：统一响应 `Resp`、全局异常处理、`UserInfo` 参数注入、OpenAPI 文档、Jackson / 时间格式约定、
HTTP 声明式客户端（`@HttpExchange`）、Redis JSON 序列化、监控与链路追踪的默认配置。配置前缀 `ss.web`，全部有默认值。

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
- 调用链会通过 HTTP 头（W3C traceparent），包括 `@HttpExchange` 声明式客户端的调用 自动延续到下游，下游的 span 与上游在同一个 trace 里
- 日志里自动带 traceId / spanId；`Resp.traceId` 会返回当前 traceId，前端报错时带上它即可定位
- 注意是头部采样：未被采样的请求也会有 traceId，但在 Jaeger 里查不到。需要"出错必留"请在 Collector 里做尾部采样

## 指标（Prometheus）

与 `ss-grpc-*` 的风格一致，Prometheus 对所有服务可以用同一份抓取配置：

- 抓取地址：非 prod 在业务端口 `/actuator/prometheus`；**prod 为 `:9091/metrics`**（与 gRPC 服务的抓取地址相同）
- 所有指标带 `application` 标签（取自 `spring.application.name`）
- `http.server.requests`、`http.client.requests` 已开启直方图（范围 1ms–10s），可跨实例聚合 p95 / p99
- 要换端口：`management.server.port`；非 prod 想用同样的路径，可设 `management.endpoints.web.base-path=/` 和
  `management.endpoints.web.path-mapping.prometheus=metrics`

## 原生镜像（GraalVM）

已用 GraalVM 25（Oracle GraalVM 25.0.4）实际编译并运行验证，需要 GraalVM 25 及以上（Boot 4 的要求）。
验证覆盖：统一响应、异常处理、参数校验、`UserInfo`、`@HttpExchange` 客户端、`JacksonUtils`、Redis 序列化、
OpenAPI、Prometheus 指标、链路追踪，结果与 JVM 模式一致。示例应用编译约 1 分 10 秒，启动 0.09 秒（JVM 模式约 1.7 秒）。

另已在 prod 配置的原生镜像上验证：独立管理端口 `:9091/metrics`、`/health/liveness|readiness`、接口文档关闭、
OTLP 导出（含服务名和请求路径）、真实 Redis 读写与 `@Cacheable`（值为带 `@class` 的 JSON）、Swagger UI（非 prod）。

starter 自己需要的提示（默认配置文件、`aot.factories`）已内置。**应用代码**需要注意，这是 GraalVM 的限制，JVM 模式不需要：

- 只通过 `JacksonUtils.fromJson(json, Xxx.class)` 反序列化、没有控制器引用的类型，要自己注册反射提示，
  如 `@RegisterReflectionForBinding(Xxx.class)`；漏注册时启动正常，调用时报 "Record components not available for record class"
- 放进 Redis 的类型同样需要反射提示，并且仍受类型白名单限制（`men.sbb8.*`）
- 原生模式下 `AOT` 在编译期固定了 Bean 和条件，不要依赖运行期改变 `@Conditional` 的属性

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
- 同理，各类 `*.enabled` 开关要在编译期确定。已实测的例子：`springdoc.*.enabled`、独立管理端口，
  以及示例里自己写的 `@ConditionalOnProperty` 开关，运行期设置都不生效。`ss.web.response-wrap.enabled`
  同样是 `@ConditionalOnProperty`，按同一原理应当如此（未单独实测）。

## 生产环境默认值

激活 `prod` / `production` 时：关闭 Swagger UI 和 `/v3/api-docs`、actuator 监控端点改用独立端口 `9091`（指标 `:9091/metrics`，健康 `:9091/health/liveness`、`/health/readiness`）、
链路追踪采样率 `0.1`、控制台日志使用 ECS 结构化 JSON。均可在应用配置里覆盖。
