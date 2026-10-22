package men.sbb8.grpc.starter.client;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.MethodDescriptor;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 给没有设置超时的一元调用补上默认 deadline，防止下游卡住时调用方无限等待。
 * 调用方自己设置的 deadline（{@code stub.withDeadlineAfter(...)}）优先。
 * 流式调用可能合法地长时间存在，不补默认值。默认超时为 0 或负数时不做任何事。
 *
 * @author Stephen
 */
public class DefaultDeadlineClientInterceptor implements ClientInterceptor {

    private final Duration defaultDeadline;

    public DefaultDeadlineClientInterceptor(Duration defaultDeadline) {
        this.defaultDeadline = defaultDeadline;
    }

    private boolean enabled() {
        return !defaultDeadline.isZero() && !defaultDeadline.isNegative();
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method,
                                                              CallOptions callOptions, Channel next) {
        if (enabled() && callOptions.getDeadline() == null && method.getType() == MethodDescriptor.MethodType.UNARY) {
            callOptions = callOptions.withDeadlineAfter(defaultDeadline.toNanos(), TimeUnit.NANOSECONDS);
        }
        return next.newCall(method, callOptions);
    }
}
