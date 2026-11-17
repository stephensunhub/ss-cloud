# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

ss-cloud 是微服务的"规范 starter"集合：应用继承本 POM、按需引入 starter，就按统一约定运行。JDK 25、Spring Boot 4.1.2、Jackson 3（`tools.jackson`，注解仍是 `com.fasterxml.jackson.annotation`）。各模块的详细用法、配置项、实测结论都在各自的 README 里，**改了行为要同步改 README**（README 是这个项目的主要文档，写得很细）。

## 常用命令

```bash
./mvnw clean verify                                   # 全量构建 + 测试（Maven Wrapper 固定 3.9.15，不用本机 Maven）
./mvnw test -pl ss-web-starter                        # 单个模块
./mvnw test -pl ss-web-starter -Dtest=ResponseWrapTest            # 单个测试类
./mvnw test -pl ss-web-starter -Dtest=ResponseWrapTest#stringIsReturnedAsIs   # 单个方法
./mvnw -Pbenchmark -pl ss-benchmark -am verify        # 基准模块默认不在构建里，必须带 -Pbenchmark
./mvnw versions:set -Pbenchmark -DnewVersion=X -DgenerateBackupPoms=false     # 升版本号：不要手改各个 pom
```

- gRPC 模块首次构建要联网下载 `protoc`（protobuf-maven-plugin）
- 没有 lint / 格式化命令
- `RedissonLockTest` 的加锁用例需要本机 6379 有 Redis，没有会自动跳过
- 原生镜像：GraalVM 25，`SPRING_PROFILES_ACTIVE=prod ./mvnw -Pnative -DskipTests native:compile`；**必须在编译期激活目标 profile**（见下文）

## 定位与边界

这是**通用能力 starter**，只放"所有服务都需要、而且应该统一"的横切能力：统一响应与异常、用户上下文透传、HTTP/gRPC 客户端与错误映射、Jackson/时间格式、Redis 序列化、分布式锁、指标/追踪/健康检查、线程池与虚拟线程默认值、原生镜像支持。

**不属于本仓库，不要往里加**（由业务项目自己决定和实现）：
- 数据库访问与事务：数据源、连接池、多数据源、迁移工具、事务边界。`@Transactional` 是 Spring 标准能力，不再封装
- 鉴权与权限：网关负责认证，starter 只透传用户头；"谁能做什么"是业务规则
- 业务幂等的语义（幂等键取什么、重复请求怎么应答）、业务 ID 规则、审计、国际化文案
- 消息队列：RocketMQ → Aeron 是后续的事，要做也是独立的可选模块，不放进 `ss-web-starter`

判断标准：能脱离具体业务、对所有服务都成立的才进来；拿不准是不是通用能力时，先问用户。新增通用能力要写使用约定（怎么用、和事务/其他能力的先后顺序），不只是写 API，参考 README 里 Redis 和分布式锁两节的"使用约定"。

## 模块与依赖方向

`ss-web-starter`（Servlet Web 服务）、`ss-grpc-server-starter`、`ss-grpc-client-starter` 是三个应用入口；`ss-grpc-core` 是 gRPC 服务端/客户端共用（错误模型 proto、Prometheus 抓取端口、追踪/监控默认配置），不直接引入；`ss-benchmark` 只测框架开销，不是容量测试。

- **web 与 gRPC 不在同一个进程**；gRPC 服务不带 web 容器（没有 Tomcat、MVC），所以也**不要做 gRPC 异常到 `Resp` 的桥接**
- **两套错误模型互相独立**：web 是 `Resp` 信封（可切到 RFC 9457）+ `BusinessCode`；gRPC 是 gRPC 状态 + `ErrorDetail` proto + 自己的 `BusinessCode`。不要让一边引用另一边的类
- Jackson、Redis 序列化、时间格式、JSON 持久化转换等都是 Web 的封装，只放 `ss-web-starter`；gRPC 走 protobuf，**不带 Jackson**
- `ss-common` 模块已删除（原为空壳）。除非出现 web 与 gRPC 真正共用的类，不要重建；也不要单独建 `ss-clients`
- 业务客户端接口、`Resp`、异常、DTO、Servlet 约定都在 `ss-web-starter`

## 约束与已定决策（不要擅自推翻）

- 不用 Spring Cloud Alibaba；不引入配置中心；**不自己造服务发现**（gRPC 在 k8s 用 headless Service：`dns:///…` + `round_robin`，服务端默认 `max-age` 10m 让客户端重新解析；等官方方案）。曾实现的 `discovery:///` 已撤销，不要再往仓库里加注册中心代码
- HTTP 按服务名调用用 Spring Cloud 官方 `base-url: lb://服务名`；HTTP 客户端只用 `@HttpExchange` + `RestClient`，不用 Feign / WebClient / 手写客户端。starter 给所有分组加最外层的 `NoInstanceAsIoErrorInterceptor`（order 0，必须小于官方 LB 配置器的 10）把"无实例"转成 502
- Spring Cloud BOM 在父 POM 导入，但官方 2025.1 列车对应 Boot 4.0.x、没有 4.1 的列车；升 Boot 到 4.2 时必须同步升列车
- **hkx-cloud4 是另一个仓库，只读参考，不要改动**。从它迁功能的原则：官方（Boot / Spring Cloud / Micrometer）已有的用配置解决，不自己造 Bean（没迁的项和理由见根 README "已定的设计决策"）
- 日志、WARN 里不带用户原始输入；参数/校验异常的完整信息只在 DEBUG
- 方向：骨架先定，之后 hkx 的 RocketMQ 要改造成 Aeron；还没开始，**不要主动做这项分析或迁移**

## 编码约定

- 注释、Javadoc、README、提交信息都用中文；提交信息是 `type(scope): 说明`（`feat(web)`、`fix(web)`、`docs`、`chore`、`build`、`ci`、`refactor`、`style`）
- 类 Javadoc 带 `@author Stephen`；可空性用 `org.jspecify.annotations`（`@Nullable` / `@NonNull`）
- Lombok 在父 POM 统一声明为 `provided`，子模块不再各自声明
- 没有"只传消息"的 `BusinessException`：不带业务码的失败就是系统异常（500）

## 实现 starter 功能时的规则

- **默认配置**放在 `META-INF/*-defaults.yaml`，由 `EnvironmentPostProcessor`（在 `spring.factories` 注册）以**最低优先级**加载；`prod` / `production` 激活时有一组更严格的默认值
- 自动配置在 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册，Bean 一般带 `@ConditionalOnMissingBean`，应用可以用同类型 Bean 覆盖。可选能力（Redis、Redisson、MyBatis、gRPC 状态识别）是 `optional` 依赖 + `@ConditionalOnClass`，应用自己引入才生效
- **原生镜像：AOT 在编译期固定 Bean 集合**，`@ConditionalOnProperty` 的值运行期改了不生效。需要运行期可改的开关，就总是注册 Bean、在运行时读配置（`ResponseWrapAdvice` 就是这样做的）；只改值的属性（端口、URL、采样率）不受影响
- 运行期反射需要的提示放在 `RuntimeHintsRegistrar`，并在 **`META-INF/spring/aot.factories`**（不是 `spring.factories`）里注册，并写测试证明它能被发现（见 `SsWebRuntimeHintsTest`）
- 新能力要**在真实原生镜像上验证**后才能在 README 里写"已实测"；没验证的范围要写明（README 里已有这种写法）

## web 约定要点（`ss-web-starter`）

- 成功响应默认由控制器自己决定怎么返回，starter 不包装；配置 `ss.web.wrap-response.base-packages` 后，这些包里的控制器自动包成 `Resp`（`String`、`byte[]`、`Resource`、流、已是 `Resp`、`ProblemDetail` 不包装）
- `BusinessCode#getHttpStatus()` 是业务码默认的 HTTP 状态（默认 200，`CommonCode.UNAUTHORIZED`=401、`SYSTEM_ERROR`=500，`PARAMS_*` 仍是 200）；`BusinessException` 显式指定的状态优先，`(String, String)` 构造固定 200。`CommonCode` 里禁止加有业务含义的枚举
- `UserInfo` 来自网关透传的请求头，**直接信任、不验签**，所以服务只能经网关访问；用户头会透传给下游内部服务（含异步线程，`ss.web.client.propagate-user-headers`）
- 分布式锁：业务代码只依赖 `DistributedLock`（`tryRun` / `call` / `run`），**不要直接用 `RedissonClient` / `RLock`**，换实现时提供另一个 `DistributedLock` Bean。Redisson 实现靠看门狗续期，创建时就连 Redis
- Redis 序列化：key 字符串、value JSON（带 `@class`，类型白名单 `men.sbb8.*` 等）
- 管理端口：web（prod）与 gRPC 的 Prometheus 抓取都是 `:9091/metrics`，没有认证，访问控制交给部署层（NetworkPolicy）

## 测试要点

- `ss-web-starter` 的 `TestApp` 位于 starter 基础包下，组件扫描**排除了** starter 自己的 `handler|config|resolver|client|redis|json|persistence` 包，否则 starter 的 Bean 会被当成应用 Bean 扫描进来、绕过自动配置；它还用 `exclude` 排掉了 `SsRedissonAutoConfiguration`（Redisson 创建时就连 Redis）
- 新增 Spring 测试优先用 `ApplicationContextRunner` 或带 `@Import` 内嵌控制器的 `@SpringBootTest`（参考 `RespTimeFieldsTest`、`ResponseWrapTest`），不要往共享的 `TestApp` 里堆端点
- gRPC 的测试用真实 TCP；`ErrorDetail` 参数上限按 UTF-8 字节，由 `MetadataSizeLimitTest` 按最坏情况校准，改上限要先看它
- 性能对比要多次启动、交错排列：单次启动的 A/B 不可信（噪声下限：QPS 2%、每请求 CPU 5%）

## 发布

- 发布到 GitHub Packages：`.github/workflows/publish.yml`，推 `v*` tag 或在 Actions 页手动 **Run workflow** 触发，执行 `./mvnw clean deploy -DskipTests`（用内置 `GITHUB_TOKEN`，无需 PAT）
- **同一个版本不能重复发布**（409 Conflict）：发布前先用 `versions:set` 改版本号；**Re-run 会复用旧提交**，要发新代码必须新建一次运行，不能 Re-run
- 应用接入方式见根 README（继承 `ss-cloud` 为 parent）；拉取包需要带 `read:packages` 的 token，即使仓库是公开的
