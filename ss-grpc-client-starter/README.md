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
