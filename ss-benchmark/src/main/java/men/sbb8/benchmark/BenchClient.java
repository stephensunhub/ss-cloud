package men.sbb8.benchmark;

import io.grpc.CallOptions;
import io.grpc.ManagedChannel;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.StreamObserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * 闭环压测客户端：保持 {@code concurrency} 个请求在途，每个请求完成立刻发下一个。
 * 回调用 directExecutor 直接在 Netty 线程里发下一个请求，尽量压低客户端自身的开销。
 * 预热阶段的数据丢弃；之后分 {@code windows} 个窗口，每个窗口测 {@code measure} 秒并单独报告，
 * 窗口之间有一小段间隙不计入，避免互相串扰。每行结果的格式由 {@link Summarize} 解析。
 * {@code --run} 标记这是同一个配置的第几次服务端启动：不同次启动之间（JIT、GC 布局不同）的差别，
 * 往往比同一次启动内各窗口之间的差别大得多，所以关键的对比要多次启动、交错进行。
 * <p>
 * 传入服务端进程号 {@code --pid} 时，用 {@code ps} 取窗口前后的 CPU 时间，得到服务端的占用核数和每个请求的 CPU 微秒数
 * （同机运行时，这是比吞吐更稳的开销指标，但只在相同负载下才能互相比较）。
 *
 * @author Stephen
 */
public final class BenchClient {

    private static final int MAX_WINDOWS = 60;
    /** 窗口结束后等一会儿再汇总，让已经读到旧阶段的在途回调把数据写完 */
    private static final long SETTLE_MILLIS = 100;

    /** 0 = 预热或窗口间隙（丢弃）；1..N = 测量窗口；-1 = 停止 */
    private static volatile int phase;

    private static final ScheduledExecutorService RETRY = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "bench-retry");
        thread.setDaemon(true);
        return thread;
    });

    private static CountDownLatch stopped;
    private static AtomicLongArray errors;

    private record Options(String label, int run, String host, int port, int concurrency, int channels, int warmup,
                           int measure, int windows, int payload, String pid) {

        static Options parse(String[] argv) {
            Map<String, String> args = new HashMap<>();
            for (int i = 0; i + 1 < argv.length; i += 2) {
                args.put(argv[i], argv[i + 1]);
            }
            String[] target = args.getOrDefault("--target", "127.0.0.1:19101").split(":");
            int concurrency = Integer.parseInt(args.getOrDefault("--concurrency", "32"));
            int windows = Integer.parseInt(args.getOrDefault("--windows", "1"));
            if (concurrency < 1 || windows < 1 || windows > MAX_WINDOWS) {
                throw new IllegalArgumentException("--concurrency 至少为 1，--windows 在 1 到 " + MAX_WINDOWS + " 之间");
            }
            return new Options(args.getOrDefault("--label", "run"), Integer.parseInt(args.getOrDefault("--run", "1")),
                    target[0], Integer.parseInt(target[1]),
                    concurrency, Math.min(Integer.parseInt(args.getOrDefault("--channels", "1")), concurrency),
                    Integer.parseInt(args.getOrDefault("--warmup", "5")),
                    Integer.parseInt(args.getOrDefault("--measure", "10")), windows,
                    Integer.parseInt(args.getOrDefault("--payload", "64")), args.get("--pid"));
        }
    }

    /**
     * 一个在途请求的位置：同一个位置的回调总在同一条连接的 Netty 线程上串行执行，所以不需要加锁
     */
    private static final class Slot implements StreamObserver<byte[]> {

        private final ManagedChannel channel;
        private final byte[] payload;
        private final long[][] samples;
        private final int[] counts;
        private long startedAt;

        Slot(ManagedChannel channel, byte[] payload, int windows) {
            this.channel = channel;
            this.payload = payload;
            this.samples = new long[windows + 1][1024];
            this.counts = new int[windows + 1];
        }

        void send() {
            if (phase < 0) {
                stopped.countDown();
                return;
            }
            startedAt = System.nanoTime();
            ClientCalls.asyncUnaryCall(channel.newCall(Echo.METHOD, CallOptions.DEFAULT), payload, this);
        }

        @Override
        public void onNext(byte[] value) {
            int window = phase;
            if (window <= 0) {
                return;
            }
            if (counts[window] == samples[window].length) {
                samples[window] = Arrays.copyOf(samples[window], samples[window].length * 2);
            }
            samples[window][counts[window]++] = System.nanoTime() - startedAt;
        }

        @Override
        public void onError(Throwable t) {
            int window = phase;
            if (window > 0) {
                errors.incrementAndGet(window);
            }
            // 不能在 Netty 线程里睡眠；稍后再发，避免连接被拒时变成一个空转的死循环
            RETRY.schedule(this::send, 1, TimeUnit.MILLISECONDS);
        }

        @Override
        public void onCompleted() {
            send();
        }

        /** 汇总用：先取计数再取数组，即使窗口刚结束仍有一个回调在写，也不会越界 */
        long[] windowSamples(int window) {
            int count = counts[window];
            long[] array = samples[window];
            return Arrays.copyOf(array, Math.min(count, array.length));
        }
    }

    private BenchClient() {
    }

    public static void main(String[] argv) throws Exception {
        Options options = Options.parse(argv);
        errors = new AtomicLongArray(options.windows() + 1);

        ManagedChannel[] channels = new ManagedChannel[options.channels()];
        for (int i = 0; i < channels.length; i++) {
            channels[i] = NettyChannelBuilder.forAddress(options.host(), options.port())
                    .usePlaintext().directExecutor().build();
        }
        Slot[] slots = new Slot[options.concurrency()];
        stopped = new CountDownLatch(slots.length);
        byte[] payload = new byte[options.payload()];
        phase = 0;
        for (int i = 0; i < slots.length; i++) {
            slots[i] = new Slot(channels[i % channels.length], payload, options.windows());
        }
        for (Slot slot : slots) {
            slot.send();
        }

        Thread.sleep(options.warmup() * 1000L);
        for (int window = 1; window <= options.windows(); window++) {
            double cpuBefore = cpuSeconds(options.pid());
            long start = System.nanoTime();
            phase = window;
            Thread.sleep(options.measure() * 1000L);
            phase = 0;
            long elapsed = System.nanoTime() - start;
            double cpuAfter = cpuSeconds(options.pid());
            Thread.sleep(SETTLE_MILLIS);
            report(options, slots, window, elapsed / 1e9, cpuAfter - cpuBefore);
        }

        phase = -1;
        stopped.await(10, TimeUnit.SECONDS);
        for (ManagedChannel channel : channels) {
            channel.shutdownNow();
        }
        System.exit(0);
    }

    private static void report(Options options, Slot[] slots, int window, double seconds, double cpuSeconds) {
        long[][] parts = new long[slots.length][];
        int total = 0;
        for (int i = 0; i < slots.length; i++) {
            parts[i] = slots[i].windowSamples(window);
            total += parts[i].length;
        }
        long[] all = new long[total];
        int position = 0;
        for (long[] part : parts) {
            System.arraycopy(part, 0, all, position, part.length);
            position += part.length;
        }
        Arrays.sort(all);
        System.out.printf(Locale.ROOT,
                "RESULT label=%s run=%d conc=%d ch=%d win=%d rps=%.0f p50=%.3fms p99=%.3fms p999=%.3fms max=%.1fms errs=%d srvCores=%.2f srvUsPerReq=%.1f%n",
                options.label(), options.run(), options.concurrency(), options.channels(), window, total / seconds,
                percentile(all, 0.50), percentile(all, 0.99), percentile(all, 0.999),
                total == 0 ? 0 : all[total - 1] / 1e6, errors.get(window),
                cpuSeconds / seconds, total == 0 ? 0 : cpuSeconds / total * 1e6);
    }

    /** 最近邻法，单位毫秒 */
    private static double percentile(long[] sorted, double p) {
        if (sorted.length == 0) {
            return 0;
        }
        return sorted[Math.min(sorted.length - 1, (int) Math.ceil(p * sorted.length) - 1)] / 1e6;
    }

    /** 进程累计的 CPU 秒数（{@code ps -o cputime=} 的格式为 [[h:]m:]s.cc）；没有传进程号时返回 0 */
    private static double cpuSeconds(String pid) throws IOException {
        if (pid == null) {
            return 0;
        }
        Process process = new ProcessBuilder("ps", "-o", "cputime=", "-p", pid).start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line = reader.readLine();
            if (line == null) {
                throw new IOException("取不到进程 " + pid + " 的 CPU 时间，服务端是否已退出？");
            }
            double total = 0;
            for (String part : line.trim().split(":")) {
                total = total * 60 + Double.parseDouble(part);
            }
            return total;
        }
    }
}
