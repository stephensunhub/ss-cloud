package men.sbb8.grpc.server;

import org.jspecify.annotations.Nullable;

/**
 * 在异常的 cause 链里找指定类型：异步代码里业务异常常被 CompletionException、ExecutionException 等包着
 *
 * @author Stephen
 */
final class CauseChain {

    private static final int MAX_DEPTH = 10;

    private CauseChain() {
    }

    static <T extends Throwable> @Nullable T find(@Nullable Throwable throwable, Class<T> type) {
        Throwable current = throwable;
        for (int depth = 0; current != null && depth < MAX_DEPTH; depth++, current = current.getCause()) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
        }
        return null;
    }
}
