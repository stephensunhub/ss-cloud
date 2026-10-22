# ss-web-starter

引入依赖即可获得：统一响应 `Resp`、全局异常处理、`UserInfo` 参数注入、OpenAPI 文档、Jackson / 时间格式约定、
HTTP 声明式客户端（`@HttpExchange`）、Redis JSON 序列化、监控与链路追踪的默认配置。配置前缀 `ss.web`，全部有默认值。

## 快速开始

在 `ss-cloud` 作为父 POM 的项目里引入即可，版本由父 POM 管理，不需要任何配置：

```xml
<dependency>
    <groupId>men.sbb8</groupId>
    <artifactId>ss-web-starter</artifactId>
</dependency>
```

要求 JDK 25、Spring Boot 4.1.1。默认配置放在 `META-INF/ss-web-defaults.yaml`，以**最低优先级**加载，
应用的 `application.yml`、环境变量、命令行都可以覆盖。

## 统一响应与异常

控制器返回任意对象，自动包装成 `Resp`：`{"code":"SUCCESS","msg":"成功","data":…,"traceId":"…"}`。

- 不想包装：方法或类上标 `@RawResponse`；返回 `Resp`、`ProblemDetail`、`Resource`、`byte[]`，
  或显式声明 `produces` 为非 JSON 的接口（如 `text/plain`）也不会被包装；全局关闭用 `ss.web.response-wrap.enabled=false`
- 抛业务异常，业务码是实现了 `BusinessCode` 的枚举：

  ```java
  @Getter @AllArgsConstructor
  enum OrderCode implements BusinessCode {
      ORDER_NOT_FOUND("订单不存在");
      private final String msg;
  }

  throw new BusinessException(OrderCode.ORDER_NOT_FOUND);                       // HTTP 200 + 业务码
  throw new BusinessException(OrderCode.ORDER_NOT_FOUND, HttpStatus.NOT_FOUND);  // 指定 HTTP 状态码
  ```

| 异常 | HTTP 状态 | `code` |
|---|---|---|
| `BusinessException` | 自带，常用构造器默认 200（无参和包装其他异常的构造器为 500 + `SYSTEM_ERROR`） | 业务码 |
| 参数绑定 / 校验失败、缺少参数、类型不匹配、请求体无法解析 | 400 | `PARAMS_ERROR` / `PARAMS_LOSS` / `PARAMS_TRANSFER_ERROR` |
| Spring MVC 自己的 404 / 405 / 415 等 | 沿用其状态码 | 状态名，如 `NOT_FOUND` |
| 下游调用失败（`RemoteCallException`） | 下游业务错误（4xx 且带业务码）透传；其余 502 | 透传的业务码 / `BAD_GATEWAY` |
| 其他未知异常 | 500，不暴露细节 | `SYSTEM_ERROR` |

- 应用自己的 `@RestControllerAdvice` 优先于 starter 的处理（starter 的优先级最低），可以覆盖其中任何一项
- 错误格式可切换：`ss.web.error-format=problem-detail` 输出 RFC 9457（`application/problem+json`），
  业务码和 `traceId` 作为扩展成员 `code`、`traceId`；这个格式下业务异常的 2xx 状态码改为 422。
  成功响应是否仍包装 `Resp`，由 `response-wrap.enabled` 单独控制，想要纯标准 REST 风格就把它设为 `false`

## 用户上下文 `UserInfo`

控制器方法声明 `UserInfo` 参数即可注入，来自网关透传的请求头：
`X-Channel-Id`、`X-User-Id`、`X-User-Name`、`X-Client`、`X-User-Language`、`X-Request-Ip`。

- **安全前提**：这些头不做签名校验，直接被信任。服务只能经网关访问，网关必须丢弃客户端自带的同名头再写入真实值
- `ss.web.user-info.required=true`：声明了 `UserInfo` 而请求缺少 `X-User-Id` 时返回 401
- `WebUtils.getCurrentUserId()`：缺失或不是数字时抛 401；`WebUtils.getRemoteIp()` 返回连接地址，
  不解析可被伪造的 `X-Forwarded-For`，位于代理之后时由 `server.forward-headers-strategy=native`（默认）还原真实地址

## 分页

请求继承 `PageReq`（`page` ≥ 1，`size` 为 1–200，默认 1 / 20），控制器参数上加 `@Valid`；响应用 `PageResp`。

## HTTP 声明式客户端

用 Spring 的 `@HttpExchange`（不用 Feign），分组注册、按组配置地址：

```java
interface WalletClient {
    @GetExchange("/wallets/{id}")
    Resp<Wallet> get(@PathVariable long id);
}

@Configuration
@ImportHttpServices(group = "wallet", types = WalletClient.class)
class ClientConfig {
}
```

```yaml
spring.http.serviceclient.wallet.base-url: http://wallet:8080
ss.web.client.propagate-user-headers: wallet    # 把当前请求的 X-User-* 头透传给这些分组，只用于内部服务
```

- 非 2xx 响应统一转成 `RemoteCallException`（带 `status`、下游的 `code` / `remoteMsg`、原始响应体，最多读 64KB）；
  返回 `Resp<T>` 的方法用 `resp.unwrap()` 取数据，失败时抛 `BusinessException`
- 默认超时：连接 3 秒、读取 30 秒，用 `spring.http.clients.connect-timeout` / `read-timeout` 调整
- 这些处理只作用于 `@HttpExchange` 客户端，不影响应用自己创建的 `RestClient`
- 用户头只在请求线程内才能透传；链路追踪的上下文会自动延续到下游

## JSON 与时间格式

- `JacksonUtils`：给无法注入 Bean 的地方使用的静态门面（`toJson`、`fromJson`、`readTree`、`convert` 等），
  容器启动后使用统一配置的 `JsonMapper`；能注入的地方直接注入 `JsonMapper`
- 时间点用 `Instant` / `OffsetDateTime`，输出 ISO-8601 带时区；`LocalDateTime` 输出 ISO，
  读取兼容 `2026-01-02T03:04:05` 和 `2026-01-02 03:04:05`，字段上的 `@JsonFormat` 照常生效
- 默认：时区 UTC、值为 `null` 的字段不输出、JSON 里多余的属性忽略、`@RequestParam` 的时间类型按 ISO 绑定
- `JsonNode` 存数据库：JPA 用 `@Convert(converter = JsonNodeConverter.class)`；
  MyBatis 配 `mybatis.type-handlers-package=men.sbb8.web.starter.persistence`

## Redis

应用引入 `spring-boot-starter-data-redis` 后生效：

- `redisTemplate`：key 是字符串，value 是带 `@class` 类型信息的 JSON，读回来是原类型而不是 `LinkedHashMap`
- 为防止反序列化任意类，有类型白名单：`men.sbb8.` 包、`java.time`、集合、`Map`、数字、`UUID`、`Date`。
  要缓存其他包下的类型，自己提供 `redisTemplate`（或 `ssRedisJsonSerializer`）覆盖
- 同时引入 `spring-boot-starter-cache` 时，`@Cacheable` 的缓存值同样是 JSON，`spring.cache.redis.*` 照常生效

## 接口文档（OpenAPI）

内置 springdoc：`/v3/api-docs` 和 Swagger UI。文档里的响应按 `Resp` 描述（不是裸的返回类型），
`UserInfo` 参数不会出现；**prod 环境默认关闭**。

## 配置项一览

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ss.web.response-wrap.enabled` | `true` | 是否把返回值包装成 `Resp` |
| `ss.web.error-format` | `resp` | 错误响应格式：`resp` 或 `problem-detail` |
| `ss.web.user-info.required` | `false` | 缺少 `X-User-Id` 时是否返回 401 |
| `ss.web.client.propagate-user-headers` | 空 | 需要透传用户头的客户端分组名 |
| `ss.web.open-api.title` | `spring.application.name` | 接口文档标题 |
| `ss.web.open-api.description` | `<标题> 接口文档` | 接口文档描述 |
| `ss.web.open-api.version` | `v1` | 接口文档版本 |

另外 starter 默认设置了这些 Boot 配置（都可以覆盖）：启用虚拟线程（`spring.threads.virtual.enabled`）、
`server.forward-headers-strategy=native`、停机超时 30 秒、Jackson 与时间格式（见上）、
actuator 只暴露 `health,info,prometheus`、链路追踪与指标（见下）。

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
