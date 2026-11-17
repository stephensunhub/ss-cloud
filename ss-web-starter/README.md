# ss-web-starter

引入依赖即可获得：统一信封 `Resp` 与全局异常处理（成功响应由 controller 自己返回）、`UserInfo` 参数注入、OpenAPI 文档、Jackson / 时间格式约定、
HTTP 声明式客户端（`@HttpExchange`）、Redis JSON 序列化、监控与链路追踪的默认配置。配置前缀 `ss.web`，全部有默认值。

## 快速开始

在 `ss-cloud` 作为父 POM 的项目里引入即可，版本由父 POM 管理，不需要任何配置：

```xml
<dependency>
    <groupId>men.sbb8</groupId>
    <artifactId>ss-web-starter</artifactId>
</dependency>
```

要求 JDK 25、Spring Boot 4.1.2。默认配置放在 `META-INF/ss-web-defaults.yaml`，以**最低优先级**加载，
应用的 `application.yml`、环境变量、命令行都可以覆盖。

## 统一响应与异常

成功响应由控制器自己决定怎么返回，starter **不会替它包装**；统一的信封 `Resp`（`{"code":"SUCCESS","msg":"成功","data":…,"traceId":"…","requestTime":1700000000000,"responseTime":1700000000000}`，两个时间是毫秒时间戳（数字）：服务收到请求的时间、生成响应的时间）
用 `Resp.ok(...)` 返回，声明返回类型为 `Resp<T>`，接口文档里也就自然是这个结构：

```java
@GetMapping("/orders/{id}")
Resp<Order> get(@PathVariable long id) {
    return Resp.ok(orderService.get(id));
}
```

异常则**统一**由 starter 转成 `Resp`（见下表），不需要每个 controller 自己处理。
- 抛业务异常，业务码是实现了 `BusinessCode` 的枚举：

  ```java
  @Getter @AllArgsConstructor
  enum OrderCode implements BusinessCode {
      ORDER_NOT_FOUND("订单不存在", HttpStatus.NOT_FOUND),   // 业务码自带 HTTP 状态码
      ORDER_CLOSED("订单已关闭");                            // 不写的默认 200
      private final String msg;
      private final HttpStatusCode httpStatus;
      OrderCode(String msg) { this(msg, HttpStatus.OK); }   // 有的项不写状态码时才需要这个单参构造器
  }

  throw new BusinessException(OrderCode.ORDER_NOT_FOUND);                        // 用业务码自带的状态：404
  throw new BusinessException(OrderCode.ORDER_CLOSED, HttpStatus.CONFLICT);      // 显式指定，覆盖业务码自带的
  ```

  `BusinessCode#getHttpStatus()` 有默认实现（200），已有的枚举不用改。

| 异常 | HTTP 状态 | `code` |
|---|---|---|
| `BusinessException` | 显式指定的优先；否则取业务码的 `getHttpStatus()`（默认 200；`CommonCode.UNAUTHORIZED` 401、`SYSTEM_ERROR` 500）；`(String, String)` 构造固定 200；无参和包装其他异常的构造器为 500 + `SYSTEM_ERROR` | 业务码 |
| 参数绑定 / 校验失败、缺少参数、类型不匹配、请求体无法解析 | 400 | `PARAMS_ERROR` / `PARAMS_LOSS` / `PARAMS_TRANSFER_ERROR` |
| Spring MVC 自己的 404 / 405 / 415 等 | 沿用其状态码 | 状态名，如 `NOT_FOUND` |
| 下游返回了错误响应（`RemoteCallException`） | 下游业务错误（4xx 且带业务码）透传；其余 502 | 透传的业务码 / `BAD_GATEWAY` |
| 下游根本没有响应：地址解析失败、连接被拒、无可用实例、超时 | 超时 504，其余 502；只打一行 WARN，不带堆栈 | `GATEWAY_TIMEOUT` / `BAD_GATEWAY` |
| 下游 gRPC 调用失败：服务不可达（`StatusRuntimeException`，状态 `UNAVAILABLE`）或超过 deadline（`DEADLINE_EXCEEDED`），裸的或被 `GrpcRemoteException` 包装的都算 | 不可达 502、超时 504；只打一行 WARN，不带堆栈。其它 gRPC 状态仍按未知异常 500。类路径上没有 grpc-api 时不生效 | `BAD_GATEWAY` / `GATEWAY_TIMEOUT` |
| 其他未知异常 | 500，不暴露细节 | `SYSTEM_ERROR` |

- 应用自己的 `@RestControllerAdvice` 优先于 starter 的处理（starter 的优先级最低），可以覆盖其中任何一项
- 错误格式可切换：`ss.web.error-format=problem-detail` 输出 RFC 9457（`application/problem+json`），
  业务码和 `traceId` 作为扩展成员 `code`、`traceId`；这个格式下业务异常的 2xx 状态码改为 422。
  成功响应由控制器自己决定，想要纯标准 REST 风格就直接返回对象，不用 `Resp.ok`

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
ss.web.client.propagate-user-headers: all       # 默认 all：所有分组都透传 X-User-* 头；有第三方接口时改成只列内部服务的分组，如 wallet,order
```

- 非 2xx 响应统一转成 `RemoteCallException`（带 `status`、下游的 `code` / `remoteMsg`、原始响应体，最多读 64KB）；
  返回 `Resp<T>` 的方法用 `resp.unwrap()` 取数据，失败时抛 `BusinessException`
- 默认超时：连接 3 秒、读取 30 秒，用 `spring.http.clients.connect-timeout` / `read-timeout` 调整
- **按服务名调用**（服务发现，如 Consul）用 Spring Cloud 官方的写法：引入 `spring-cloud-starter-loadbalancer` 和服务发现后，
  `base-url` 写成 `lb://服务名`，本 starter 不需要任何额外配置：

  ```yaml
  spring.http.serviceclient.wallet.base-url: lb://wallet     # 服务名，经 Spring Cloud LoadBalancer 在实例间轮询
  ```

  本 starter 在此之上只做一件事：没有可用实例时（Spring Cloud 抛的是普通的 `IllegalStateException`）统一返回 502，
  和"下游连不上"一致，而不是被当成未知异常变成 500。只有 `lb://` 开头的地址才走负载均衡，
  `http://wallet` 这样的写法**不会**被当成服务名；普通地址的分组不受影响。
  已用真实的 Spring Cloud LoadBalancer 测试（两个实例轮询、没有实例返回 502、普通地址不受影响），
  并在 Boot 4.1.1 + Spring Cloud 2025.1.3（spring-cloud-consul 5.0.3）+ Consul 1.20.6 上用真实的 `DiscoveryClient` 和 TTL 健康检查
  验证了轮询、无实例和实例宕机 / 恢复（JVM 模式）。注意官方 2025.1 列车对应的是 Boot 4.0.x，目前没有基于 Boot 4.1 构建的列车，但 2025.1.3 的版本校验接受 4.1.x；升级到 Boot 4.2 时要同步升级列车。
  原生镜像下的要求见"原生镜像"一节
- **实例宕机后的失败窗口由 LoadBalancer 的实例缓存决定**：默认缓存 35 秒（`spring.cloud.loadbalancer.cache.ttl`），
  实例宕机后，Consul 在健康检查失败时（实测 6 秒）就不再返回它，但缓存没过期前调用方仍会选中它，且**没有重试**。
  实测（两个实例，其中一个宕机，每 0.5 秒调用一次）：默认值下约一半的请求失败持续了约 35 秒；
  设成 `5s` 后失败只持续到 9 秒（Consul 判定的 6 秒 + 最多 5 秒的缓存），实例恢复后 1 秒内就被重新选中。
  缓存越短，有请求时向 Consul 查询得越频繁：每个服务每个缓存周期至少一次，缓存到期的瞬间如果有并发请求，
  它们会各查一次（早先实测过：5 个并发发出了 5 次查询，没有合并）。Consul 的实际请求量在 5 秒缓存下没有测过。
  按服务对故障的容忍度调整；这个值没有在本 starter 里改默认，由应用决定
- 这些处理只作用于 `@HttpExchange` 客户端，不影响应用自己创建的 `RestClient`
- **用户头透传的规则**（`ss.web.client.propagate-user-headers`，默认 `all`：所有分组都透传；改成分组名列表则只有列出的分组透传。
  **调用第三方接口的分组不要透传**，会把用户标识发出去，此时不要用 `all`，列出内部服务的分组；`all` 是保留值，不要当分组名）：
  - 透传 6 个头：`X-Channel-Id`、`X-User-Id`、`X-User-Name`、`X-Client`、`X-User-Language`、`X-Request-Ip`；
    当前请求里没有的头不补，调用方在 `@HttpExchange` 方法上显式设置的同名头不覆盖
  - **`X-Request-Ip` 缺失（或为空白）时透传 `0.0.0.0`**，包括当前没有请求的场景（定时任务等），下游总能拿到一个 IP 值；
    其它头缺失则不补。下游要的是用户真实 IP 就读 `UserInfo.ip()`，不要用 `WebUtils.getRemoteIp()`（那是上游服务的地址）
  - **异步线程也透传**：提交任务时在请求线程里复制头的值（不持有请求对象，请求结束后仍然有效），执行时在异步线程里恢复。
    Boot 的 `@Async` 线程池（`spring.task.execution.propagate-context`，starter 默认开启）自动带上；
    **自建线程池、`CompletableFuture` 默认池、`new Thread` 不会自动带**，需要包一层：
    `ContextSnapshotFactory.builder().build().captureAll().wrap(任务)`（`Runnable` / `Callable` 都可以）
  - 不透传 `Authorization`、`Cookie`、`X-Forwarded-For` 等其它头；`traceparent` 由链路追踪自动带，所有分组都带，不受上面的配置影响
  - 这套规则只作用于 `@HttpExchange`；gRPC 不会自动带这些头
- 链路追踪的上下文会自动延续到下游

## 静态取 Bean

`SpringBeans.getBean(...)` 给无法注入的地方使用（MyBatis 的 `TypeHandler`、静态方法、框架反射创建的对象）；能注入的地方直接注入。

- 只能取已有的 Bean，不提供运行时注册 Bean（原生镜像里运行时新增的 Bean 定义没有反射元数据）
- 容器关闭时释放；多个容器并存（测试、父子容器）时用最后启动且仍在运行的那个
- 容器未启动或已关闭时抛 `IllegalStateException`；这是进程级全局状态，业务代码里能注入就不要用

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
- **缓存默认 30 分钟过期**（`spring.cache.redis.time-to-live: 30m`，Boot 默认是永不过期；可在 `application.yml` 覆盖）。
  个别缓存项要不同的过期时间，注册 `CacheTtlResolver` Bean，按 key 和值决定，返回 `null` 用默认值，`Duration.ZERO` 表示永不过期：
  ```java
  @Bean
  CacheTtlResolver ttlResolver() {
      return (key, value) -> value instanceof VipUser ? Duration.ofHours(6) : null;
  }
  ```
  `key` 是 `@Cacheable` 解析出的原始 key 对象，不是带缓存名前缀的字符串。注解没有"每次调用单独指定过期时间"的能力，
  要在调用时才知道过期时间的，直接用 `redisTemplate.opsForValue().set(key, value, ttl)`

### 使用约定

- **能放进 Redis 的类型受白名单限制**（见上）：自己的类要在 `men.sbb8.` 包下，否则读回来会被拒绝；缓存其他包的类型，
  先想清楚是不是真的要缓存它，再按上面的办法覆盖序列化器
- **类型和字段变化会让旧数据读不回来**（value 里带 `@class` 和字段）：改了缓存对象的类名、包名或字段后，旧 key 要么等过期，要么换一个 key 前缀
- **缓存要有过期时间**：`@Cacheable` 默认 30 分钟（见上）；直接用 `redisTemplate` 写入时自己带过期时间，不要写永不过期的 key
- 最外层的 `Map.of` / `List.of` / `Set.of` 等 JDK 内部集合写入前会换成普通集合，读回来是 `LinkedHashMap` / `ArrayList` / `LinkedHashSet`，不要依赖它们不可变；嵌套在对象或集合里的不受影响
- 需要互斥用 `DistributedLock`，不要用 `setIfAbsent` 自己拼锁（没有续期、没有重入、释放时容易误删别人的锁）


## 分布式锁（Redisson）

应用自己引入 `org.redisson:redisson` 和 `spring-boot-starter-data-redis`（starter 里都是可选依赖，Redisson 版本由父 POM 管理）后，会按 `spring.data.redis.*`
（地址、端口、用户名、密码、database、SSL、超时）自动创建单机模式的 `RedissonClient`，不用再配一遍。

业务代码注入 `DistributedLock`，**不要直接用 `RedissonClient` / `RLock`**，以后换实现（比如换成别的锁服务）只需要提供另一个 `DistributedLock` Bean：

```java
// 拿到锁就执行；等 3 秒仍拿不到则不执行，返回 false
boolean ran = distributedLock.tryRun("order:" + orderId, Duration.ofSeconds(3), () -> payService.pay(orderId));

// 拿到锁执行并返回结果；拿不到抛 LockNotAcquiredException（没捕获时按未知异常返回 500）
Order order = distributedLock.call("order:" + orderId, Duration.ofSeconds(3), () -> orderService.create(req));
```

接口的约定：同一个 key 跨实例互斥、可重入；持有期间锁不会因超时被抢走（执行时间不确定也安全），持有者宕机后锁自动过期；
`action` 抛出的异常原样抛出，锁一定释放。Redisson 实现靠看门狗续期（默认 30 秒）。

- 创建时就会连接 Redis，连不上则应用启动失败；不需要时去掉依赖，或用 `spring.autoconfigure.exclude` 排除 `SsRedissonAutoConfiguration`
- 哨兵、集群模式，或需要自定义编解码器等，自己提供 `RedissonClient` Bean 即可覆盖；
  `SsRedissonAutoConfiguration.toConfig(DataRedisProperties)` 可以拿来在单机配置的基础上调整
- **原生镜像：** 已用 Redisson 4.8.0 + Boot 4.1.2 + GraalVM 25.0.4 在依赖本 starter 的示例应用上实测（直接用 `RLock`，`DistributedLock` 这层封装是纯 Java，没有单独在原生镜像上跑过）：加锁解锁、
  可重入、互斥、看门狗续期（持有 15 秒，默认 30 秒的租期被续期）、读写锁，结果与 JVM 模式一致，镜像启动 0.1 秒。Netty 的 `ReferenceCountUtil` 需要反射提示，已在 `SsWebRuntimeHints` 里注册，
  不注册会在连接 Redis 时报 `NoClassDefFoundError: Could not initialize class io.netty.util.ReferenceCountUtil`。
  **只验证了单机模式、默认编解码器下的锁**；哨兵/集群、`RBucket` 等数据结构、Redisson 的 Spring Cache 集成、
  `redisson-spring-boot-starter` 都没有验证

### 使用约定

- **锁要包在事务外面**：先拿锁，再开事务，事务提交之后才释放锁。反过来（事务里面拿锁）时，锁释放了事务还没提交，
  下一个拿到锁的人读到的是旧数据，互斥就白做了。在 Spring 里要让事务方法是**另一个 Bean** 的 `@Transactional` 方法
  （同一个类里的自调用不会开事务）：
  ```java
  distributedLock.run("order:" + orderId, Duration.ofSeconds(3),
          () -> orderTxService.pay(orderId));   // pay 是 orderTxService 上的 @Transactional 方法
  ```
- **key 只放业务标识**：同一份资源的所有入口必须用同一个 key（比如都用 `order:<id>`），不同资源的 key 不能撞；
  key 不要带用户输入的任意长文本
- **等待时间按业务能容忍的来定**：用户请求里别等太久（几秒），拿不到由调用方决定怎么办——`tryRun` 返回 `false`，
  或 `call` / `run` 抛 `LockNotAcquiredException` 再转成业务提示；不要无限等待
- **锁只保证"同一时刻只有一个执行者"，不保证幂等**：拿到锁之后仍然要判断"这件事是不是已经做过"
- `action` 在调用线程里同步执行，不要在里面再把工作丢给别的线程后立刻返回（锁会在 `action` 返回时释放）

## 接口文档（OpenAPI）

内置 springdoc：`/v3/api-docs` 和 Swagger UI。文档里的响应就是控制器声明的返回类型（用 `Resp<T>` 时即信封结构），
`UserInfo` 参数不会出现；**prod 环境默认关闭**。

## 配置项一览

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ss.web.error-format` | `resp` | 错误响应格式：`resp` 或 `problem-detail` |
| `ss.web.wrap-response.base-packages` | 空 | 这些包（含子包）里的控制器，成功返回值自动包装成 `Resp`；空表示不包装。`String`、`byte[]`、`Resource`、流、已是 `Resp` 的和错误响应不包装 |
| `spring.cache.redis.time-to-live` | `30m` | `@Cacheable` 缓存项的默认过期时间（Boot 的配置，starter 改了默认值）；按缓存项调整见 `CacheTtlResolver` |
| `ss.web.user-info.required` | `false` | 缺少 `X-User-Id` 时是否返回 401 |
| `ss.web.client.propagate-user-headers` | `all` | 透传用户头的客户端分组名；`all` 表示所有分组，否则只透传列出的分组 |
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
- `http.server.requests`、`http.client.requests` 已配置固定的 11 个桶（5ms、10ms、25ms、50ms、100ms、250ms、500ms、1s、2.5s、5s、10s），可跨实例聚合 p95 / p99；桶少是为了控制时间序列数量（比默认直方图的 60 多个桶少 5 倍以上），要更细的精度可自行配置 `management.metrics.distribution.slo.*` 或 `percentiles-histogram.*`
- 要换端口：`management.server.port`；非 prod 想用同样的路径，可设 `management.endpoints.web.base-path=/` 和
  `management.endpoints.web.path-mapping.prometheus=metrics`

## 原生镜像（GraalVM）

已用 GraalVM 25（Oracle GraalVM 25.0.4）实际编译并运行验证，需要 GraalVM 25 及以上（Boot 4 的要求）。
验证覆盖：统一响应、异常处理、参数校验、`UserInfo`、`@HttpExchange` 客户端、`JacksonUtils`、Redis 序列化、
OpenAPI、Prometheus 指标、链路追踪，结果与 JVM 模式一致。示例应用编译约 1 分 10 秒，启动 0.09 秒（JVM 模式约 1.7 秒）。

另已在 prod 配置的原生镜像上验证：独立管理端口 `:9091/metrics`、`/health/liveness|readiness`、接口文档关闭、
OTLP 导出（含服务名和请求路径）、真实 Redis 读写与 `@Cacheable`（值为带 `@class` 的 JSON）、Swagger UI（非 prod）。

`ss.web.error-format=problem-detail` 也已在原生镜像上实测（它是运行期读取的值，不是条件 Bean，运行期设置即可生效）：
业务异常 422、未知异常 500、参数校验失败 400、不存在的路径 404、下游失败 502，都是 `application/problem+json`，
带 `code`、`detail`、`title`、`status`、`instance`、`traceId`；成功响应仍是 `Resp`。

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
- 同理，各类 `*.enabled` 开关要在编译期确定。已实测：`springdoc.*.enabled`、独立管理端口、示例里自己写的
  `@ConditionalOnProperty` 开关、以及 gRPC 的 `ss.grpc.metrics.prometheus.enabled`，运行期设置在原生镜像里都不生效
  （JVM 模式下是生效的）

### Spring Cloud 与原生镜像

- **LoadBalancer（`lb://服务名`）在原生镜像里可用，但必须在编译期声明用到的服务名**（官方文档的要求）：
  `spring.cloud.loadbalancer.eager-load.clients=wallet,order`，或 `@LoadBalancerClient(name = "wallet")`。
  已实测（静态实例列表，两个实例）：声明后的轮询结果与 JVM 完全一致；不声明则每次调用都失败
  （`GenericApplicationContext must be an instance of …`，对外变成 500）。这个值在编译期固定，运行期再改无效。
- **Consul 相关的问题另算**（已实测，原生镜像下基本不可用，JVM 模式正常）：`@RefreshScope` 让 AOT 编译直接失败；
  读取 Consul KV 配置因 `GetValue` 缺反射提示而加载失败；服务注册请求发出后 Consul 里没有该服务（原因未查明）。
  Consul 的 `DiscoveryClient` 在原生镜像下没有单独验证过。
  要同时使用 GraalVM 和 Consul，需要自己补反射提示或等上游支持。

## 异步任务与线程池

starter 默认开启虚拟线程（`spring.threads.virtual.enabled=true`），`@Async` 和 `@Scheduled` 都跑在虚拟线程上。线程池的配置**全部用 Boot 官方的 `spring.task.*`**，
starter 不自己创建线程池 Bean，只改了几个不适合生产的默认值（见 `ss-web-defaults.yaml`）：

| 配置 | starter 默认 | Boot 默认 | 说明 |
|---|---|---|---|
| `spring.task.execution.propagate-context` | `true` | `false` | `@Async` 里的日志和链路带上调用方的 `traceId`。已测试：关闭后异步线程里的 `traceId` 为空 |
| `spring.task.execution.shutdown.await-termination(-period)` | `true` / `30s` | 不等待 | 停机时等进行中的异步任务，与 `spring.lifecycle.timeout-per-shutdown-phase` 一致 |
| `spring.task.scheduling.shutdown.*` | 同上 | 不等待 | |
| `spring.task.execution.pool.max-size` / `queue-capacity` | `64` / `200` | 都不设上限 | **仅关闭虚拟线程时生效**。Boot 默认实际是 8 个核心线程加无界队列，过载时不拒绝、只堆积；数值只是起点，按负载调整 |
| `spring.task.scheduling.pool.size` | `4` | `1` | **仅关闭虚拟线程时生效**。默认的 1 会让一个慢任务堵住所有 `@Scheduled` |

- **传递的是 Observation 上下文**：HTTP 请求本来就在 Observation 里处理，所以 `@Async` 能拿到 `traceId`。自己用 `Tracer.nextSpan()` 手动开的 span 不在其中，
  不会被传到异步线程
- **线程池满了的处理**：关闭虚拟线程时，线程池拒绝任务会记一条错误日志并计数 `executor.rejected`（标签 `executor` 为线程名前缀），然后抛出
  `TaskRejectedException`，不让请求线程代跑任务。线程池的活跃数、队列长度等指标由 Boot 自动提供
- **虚拟线程没有队列，也不限并发**。要保护下游连接池，设 `spring.task.execution.simple.concurrency-limit`，
  配合 `reject-tasks-when-limit-reached=true` 可以在超限时拒绝而不是排队
- **需要隔离的线程池**（例如把慢的外部调用与其他任务隔开）由应用自己定义，用 Boot 的 `ThreadPoolTaskExecutorBuilder` 创建会自动继承上面的默认值。
  starter 不预置 `ioExecutor` / `cpuExecutor` 这类命名线程池，因为怎么隔离是应用的设计
- 已用 GraalVM 25 的原生镜像实测：默认（虚拟线程）模式下 `@Async` 线程里的 `traceId` 与请求一致；用 `prod` 之外的 `spring.threads.virtual.enabled=false` 编译的镜像里，线程池满了会拒绝任务并计数 `executor.rejected`。注意虚拟线程还是线程池是**编译期**就定下的，运行期改这个开关对原生镜像无效

## 生产环境默认值

激活 `prod` / `production` 时：关闭 Swagger UI 和 `/v3/api-docs`、actuator 监控端点改用独立端口 `9091`（指标 `:9091/metrics`，健康 `:9091/health/liveness`、`/health/readiness`）、
链路追踪采样率 `0.1`、控制台日志使用 ECS 结构化 JSON。均可在应用配置里覆盖。
