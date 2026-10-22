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
- **链路追踪**：OTLP/HTTP 导出，可对接 Jaeger、Tempo 或 OTel Collector；
  配置了 `management.opentelemetry.tracing.export.otlp.endpoint` 才会导出，prod 采样率默认 0.1
- **健康检查**：web 用 actuator 的 HTTP 探针；gRPC 用 k8s 的 gRPC 探针（`grpc.health.v1`，`liveness` / `readiness`）
- **错误**：web 返回 `Resp` 信封（可切换为 RFC 9457）；gRPC 用 gRPC 状态加 `ErrorDetail`。两者的业务码体系互相独立
- **GraalVM 原生镜像**：已用 GraalVM 25 实测可用，但**必须按环境编译**，详见各模块 README 的"原生镜像"一节

## 构建

需要 JDK 25 和 Maven 3.9（已用 3.9.15 验证）：

```bash
mvn clean verify
```

首次构建需要联网：gRPC 模块用 `protobuf-maven-plugin` 从 Maven 仓库下载 `protoc`，不要求本机安装。
原生镜像需要 GraalVM 25（Oracle GraalVM 25.0.4 已验证）。

## 已定的设计决策

- 不使用 Spring Cloud Alibaba；暂不引入服务发现和配置中心。项目依赖 Consul，用到 Spring Cloud Consul 时要注意：
  Spring Cloud BOM 已在父 POM 里导入但**目前没有任何模块使用**，而官方的 2025.1 列车对应 Boot 4.0.x，
  暂无正式对应 Boot 4.1 的列车（下一条 2026.0 对应 Boot 4.2），引入前需先实测兼容性，或让 Boot 与列车对齐
- HTTP 客户端用 `@HttpExchange` + `RestClient`，不用 Feign、不用手写 HTTP 客户端
- Web 相关的封装（Jackson、Redis 序列化、JSON 持久化转换等）都在 `ss-web-starter`，gRPC 走 protobuf，不带 Jackson
- 原有的 hkx-cloud4 项目只作参考，不在本仓库里修改
