# ss-cloud

微服务的"规范 starter"集合：引入依赖，服务就按统一约定运行，不需要逐个项目自定义。
基于 Spring Boot 4.1.1、JDK 25、Jackson 3；本 POM 是所有模块的父 POM，统一管理 Spring Boot、Spring Cloud BOM
和内部模块的版本。

## 模块

| 模块 | 用途 | 文档 |
|---|---|---|
| [`ss-web-starter`](ss-web-starter/README.md) | **Servlet Web 服务**：统一异常、`UserInfo`、`@HttpExchange` 客户端、Jackson / 时间格式、Redis、OpenAPI | [README](ss-web-starter/README.md) |
| [`ss-grpc-server-starter`](ss-grpc-server-starter/README.md) | **gRPC 服务端**：异常转换、健康检查（存活 / 就绪）、端口默认值 | [README](ss-grpc-server-starter/README.md) |
| [`ss-grpc-client-starter`](ss-grpc-client-starter/README.md) | **gRPC 客户端**：失败还原为带业务码的异常、默认超时 | [README](ss-grpc-client-starter/README.md) |
| [`ss-grpc-core`](ss-grpc-core/README.md) | gRPC 服务端、客户端共用：错误模型、指标抓取、链路追踪与监控默认配置。**一般不直接引入** | [README](ss-grpc-core/README.md) |
| [`ss-benchmark`](ss-benchmark/README.md) | **框架开销基准**（不是容量测试）：不参与默认构建，`-Pbenchmark` 才启用；有[基线数字](ss-benchmark/BASELINE.md) | [README](ss-benchmark/README.md) |

怎么选：

- 对外提供 HTTP / REST 接口的服务 → `ss-web-starter`
- 提供 gRPC 接口的服务 → `ss-grpc-server-starter`
- 只调用 gRPC、自己不提供接口的服务（批处理、消费者等）→ `ss-grpc-client-starter`
- 既提供又调用 gRPC → 两个都引入

gRPC 服务**不带 web 容器**（没有 Tomcat、Spring MVC），web 服务和 gRPC 服务不放在同一个进程里。

## 应用如何接入

让应用继承 `ss-cloud` 作为父 POM（它自己继承 `spring-boot-starter-parent`，所以应用不需要再单独声明 Boot Parent），
然后按需引入 starter，版本不用写：

```xml
<parent>
    <groupId>men.sbb8</groupId>
    <artifactId>ss-cloud</artifactId>
    <version>2026.10.21.0</version>
    <relativePath/>
</parent>

<dependencies>
    <dependency>
        <groupId>men.sbb8</groupId>
        <artifactId>ss-web-starter</artifactId>
    </dependency>
</dependencies>
```

- **目前版本只能从本地构建安装**（在本仓库执行 `mvn install`），还没有发布到制品库，别的机器上的项目暂时引入不了
- 目前**没有独立的 BOM**。如果某个服务已经有别的公司级 Parent、不能继承 `ss-cloud`，需要时再拆出 `ss-dependencies`，
  让它能在自己的 Parent 下用 `<scope>import</scope>` 导入

## 共同约定

- **默认配置**放在各模块的 `META-INF/*-defaults.yaml`，通过 `EnvironmentPostProcessor` 以**最低优先级**加载：
  应用的 `application.yml`、环境变量、命令行都可以覆盖。激活 `prod` / `production` 环境时有一组更严格的默认值
- **指标**：Prometheus 抓取，所有指标带 `application` 标签，耗时指标配置了固定的 11 个桶（5ms–10s），可跨实例聚合分位数。
  web（prod）和 gRPC 的抓取地址相同，都是 **`:9091/metrics`**，Prometheus 对所有服务用同一份配置
- **管理端口 9091 没有认证**（指标、健康检查，不含业务数据），访问控制交给部署层：用 NetworkPolicy 只允许 Prometheus 和
  kubelet 访问。业务端口（web 的 HTTP、gRPC 服务端口）不受影响。示例（`monitoring` 命名空间里的 Prometheus 抓取）：
  ```yaml
  apiVersion: networking.k8s.io/v1
  kind: NetworkPolicy
  metadata: {name: allow-metrics-scrape}
  spec:
    podSelector: {matchLabels: {app: my-service}}   # 选中业务 Pod
    policyTypes: [Ingress]
    ingress:
      - from:
          - namespaceSelector: {matchLabels: {kubernetes.io/metadata.name: monitoring}}
        ports: [{port: 9091}]
      # 业务端口按需另写规则；一旦选中 Pod，未列出的入站流量都会被拒绝
  ```
  kubelet 探针来自节点本身，多数 CNI 默认放行节点到本节点 Pod 的流量，但这取决于具体的 CNI，需要在自己的集群里确认
  （这份示例没有在真实集群里验证过）。要在应用内认证的话，不建议给 9091 加共享 token：Prometheus 抓取和探针都要跟着配，
  还要轮换；需要时用服务网格的 mTLS 或 Prometheus 的 TLS 抓取
- **链路追踪**：OTLP/HTTP 导出，可对接 Jaeger、Tempo 或 OTel Collector；
  配置了 `management.opentelemetry.tracing.export.otlp.endpoint` 才会导出，prod 采样率默认 0.1
- **健康检查**：web 用 actuator 的 HTTP 探针；gRPC 用 k8s 的 gRPC 探针（`grpc.health.v1`，`liveness` / `readiness`）
- **gRPC 服务发现**：k8s 里用 headless Service（`dns:///`，`round_robin`），服务端默认设连接最长存活 10 分钟让客户端定期重新解析；
  用法见 `ss-grpc-client-starter` 和 `ss-grpc-server-starter` 的 README
- **错误**：web 返回 `Resp` 信封（可切换为 RFC 9457）；gRPC 用 gRPC 状态加 `ErrorDetail`。两者的业务码体系互相独立
- **GraalVM 原生镜像**：已用 GraalVM 25 实测可用，但**必须按环境编译**，详见各模块 README 的"原生镜像"一节

## 构建

需要 JDK 25。仓库自带 Maven Wrapper（固定 Maven 3.9.15，已验证），不需要本机预装 Maven：

```bash
./mvnw clean verify
```

首次构建需要联网：gRPC 模块用 `protobuf-maven-plugin` 从 Maven 仓库下载 `protoc`，不要求本机安装。
原生镜像需要 GraalVM 25（Oracle GraalVM 25.0.4 已验证）。

升版本号时用 Maven 的 versions 插件一次改全部模块，不要手工逐个 pom 改（带 `-Pbenchmark` 才会包含基准模块）：

```bash
./mvnw versions:set -Pbenchmark -DnewVersion=2026.11.1.0 -DgenerateBackupPoms=false
```

## 已定的设计决策

- 不使用 Spring Cloud Alibaba；暂不引入配置中心。服务发现：gRPC 在 k8s 里先用 headless Service（`dns:///`），
  等 Spring gRPC / Spring Cloud 出官方方案，不自己造服务发现的轮子；HTTP 按服务名调用用 Spring Cloud 官方的 `lb://服务名`
- 项目依赖 Consul，用到 Spring Cloud 组件时要注意：Spring Cloud BOM 已在父 POM 里导入，但官方的 2025.1 列车对应 Boot 4.0.x，
  暂无基于 Boot 4.1 构建的列车（下一条 2026.0 对应 Boot 4.2，在 Boot 4.1.1 上启动会被它的版本校验拒绝）；2025.1.3 自带的版本校验接受 4.0.x 和 4.1.x。升级 Boot 到 4.2 时要同步升级列车。已实测 Spring Cloud LoadBalancer 和 Spring Cloud Consul 5.0.3
  在 Boot 4.1.1 的 JVM 模式下可用；其他组件引入前需先实测兼容性，或让 Boot 与列车对齐
- HTTP 客户端用 `@HttpExchange` + `RestClient`，不用 Feign、不用手写 HTTP 客户端
- Web 相关的封装（Jackson、Redis 序列化、JSON 持久化转换等）都在 `ss-web-starter`，gRPC 走 protobuf，不带 Jackson
- 原有的 hkx-cloud4 项目只作参考，不在本仓库里修改
- 从 hkx-cloud4 迁移功能的原则：官方（Boot / Spring Cloud / Micrometer）已经有的用配置解决，不自己造 Bean；只迁官方没有、且确实有价值的部分。
  线程池只调了 `spring.task.*` 的默认值并补了"拒绝计数"（见 `ss-web-starter` README）。**没有迁移**的：
  - `ioExecutor` / `cpuExecutor` / 自制虚拟线程执行器：命名线程池的隔离是应用的设计；并发上限官方已有 `spring.task.execution.simple.concurrency-limit`
  - RocketMQ 指标：ss-cloud 没有 RocketMQ 依赖；hkx 的实现每条消息都带 `queue_id`、`broker_name` 标签，基数偏高。要用时做成独立的可选模块，并先看客户端自带的指标
  - Loki：hkx 里其实没有 Loki appender。日志输出结构化 JSON 到标准输出，由 Alloy / Promtail 采集，应用里不放网络 appender
  - 写操作日志（拦截器打印完整请求和响应）：与"日志不带用户输入"冲突，且依赖外部 proto 的自定义选项。需要"谁做了什么"的审计，应在应用层按业务事件记录并带上操作人
