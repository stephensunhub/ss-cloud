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

## 生产环境默认值

激活 `prod` / `production` 时：关闭 Swagger UI 和 `/v3/api-docs`、actuator 监控端点改用独立端口 `8081`、
链路追踪采样率 `0.1`、控制台日志使用 ECS 结构化 JSON。均可在应用配置里覆盖。
