# ss-grpc-client-starter

gRPC **客户端**规范 starter。引入后获得：调用失败还原为带业务码的异常、一元调用默认超时，
并包含 `ss-grpc-core` 的错误模型、指标与链路追踪（见 `ss-grpc-core/README.md`）。不带服务端。

## 失败还原

用 `GrpcCalls.call(() -> stub.xxx(req))`（无返回值用 `GrpcCalls.run`）调用，失败时 `StatusRuntimeException`
会转成 `GrpcRemoteException`：`getStatus()` 是 gRPC 状态，`getCode()` / `getRemoteMsg()` 是下游返回的业务码和消息
（下游没有返回详情时为 `null`）。注意这不是自动生效的，需要业务代码主动用它包住 stub 调用。

服务端流式调用用 `GrpcCalls.stream(() -> stub.listXxx(req))`：阻塞 stub 返回的迭代器是在 `hasNext` / `next` 时才抛出失败的，
不在 `call` 的范围内；`stream` 返回的迭代器会在迭代时同样转成 `GrpcRemoteException`，已经收到的消息不受影响。

## 客户端默认超时

一元调用没有显式 deadline 时使用 `ss.grpc.client.default-deadline`（默认 30s，0 关闭）。
流式调用不受影响；调用方自己设置的 deadline 优先。

## 在 k8s 里调用其他服务（headless Service）

目前 gRPC 的服务发现用 k8s 自带的 headless Service，不引入注册中心（Spring gRPC / Spring Cloud 还没有官方方案，
不自己维护服务发现）。

被调用方的 Service 要声明为 headless（`clusterIP: None`），DNS 会直接返回所有就绪 pod 的地址：

```yaml
apiVersion: v1
kind: Service
metadata:
  name: order-service
spec:
  clusterIP: None
  selector:
    app: order-service
  ports:
    - name: grpc
      port: 9001
```

调用方把通道的目标写成 `dns:///`，并用 `@ImportGrpcClients` 引用这个通道：

```yaml
spring.grpc.client.channel.order.target: dns:///order-service.prod.svc.cluster.local:9001
```

```java
@ImportGrpcClients(target = "order", types = OrderGrpc.OrderBlockingStub.class)
```

- **不能用普通的 ClusterIP Service**：它是连接层面的虚拟 IP，gRPC 的一条长连接会把所有请求发给同一个 pod，没有负载均衡
- 负载均衡策略默认 `round_robin`（`spring.grpc.client.channel.<名>.default.load-balancing-policy`），请求按 pod 轮询；
  每个 RPC 在本地选择连接，请求路径上不查 DNS
- 目标写全限定名（`服务.命名空间.svc.cluster.local`），不依赖搜索域
- **新扩容的 pod 靠服务端的 `connection.max-age` 才会被发现**（默认已设 10 分钟），见 `ss-grpc-server-starter/README.md`
- 不引入 Spring Cloud，原生镜像下没有额外限制；将来 Spring 出了官方的服务发现方案，只需要改通道的 `target`
- 只适用于 k8s 集群内；跨集群或集群外的服务需要另外的办法

已验证：`dns:///` 目标、`@ImportGrpcClients(target = ...)` 的写法和默认 `round_robin` 都有测试（`HeadlessTargetConfigTest`），
max-age 触发重新解析有测试（`ConnectionRecyclingTest`）。**没有在真实 k8s 集群里验证过**扩容、缩容和滚动发布时的流量分布。
