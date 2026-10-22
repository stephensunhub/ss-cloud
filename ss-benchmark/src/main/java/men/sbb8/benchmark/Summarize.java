package men.sbb8.benchmark;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 把 {@link BenchClient} 输出的 {@code RESULT} 行按"标签 + 并发 + 连接数"汇总成表格（各窗口取平均）。
 * 同一个标签可以对应多个并发档位（实验 1），所以并发和连接数也是分组的键。
 * 给出第二个文件时作为基线，额外显示吞吐和每请求 CPU 相对基线的变化。
 * <pre>
 * java -cp ... men.sbb8.benchmark.Summarize 本次结果.txt [基线.txt]
 * </pre>
 *
 * @author Stephen
 */
public final class Summarize {

    /** 一种配置在一个并发档位下的汇总 */
    record Row(String label, int concurrency, int channels, int runs, int windows, double rps, double rpsRangePct,
               double p50, double p99, double p999, long errors, double cores, double usPerReq,
               double usRangePct, double clientUsPerReq, double clientUsRangePct) {

        /** 与基线对应的键：同一个标签在不同并发、不同连接数下是不同的行 */
        String key() {
            return label + "|" + concurrency + "|" + channels;
        }
    }

    private Summarize() {
    }

    static List<Row> summarize(List<String> lines) {
        Map<String, List<Map<String, String>>> groups = new LinkedHashMap<>();
        for (String line : lines) {
            if (!line.startsWith("RESULT ")) {
                continue;
            }
            Map<String, String> fields = new HashMap<>();
            for (String token : line.substring("RESULT ".length()).trim().split("\\s+")) {
                int eq = token.indexOf('=');
                if (eq > 0) {
                    fields.put(token.substring(0, eq), token.substring(eq + 1).replace("ms", ""));
                }
            }
            String key = fields.get("label") + "|" + fields.get("conc") + "|" + fields.get("ch");
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(fields);
        }
        List<Row> rows = new ArrayList<>();
        groups.forEach((key, windows) -> {
            Map<String, String> first = windows.getFirst();
            rows.add(new Row(first.get("label"), Integer.parseInt(first.get("conc")), Integer.parseInt(first.get("ch")),
                    runsOf(windows).size(), windows.size(), mean(windows, "rps"), rangePct(windows, "rps"),
                    mean(windows, "p50"), mean(windows, "p99"), mean(windows, "p999"),
                    (long) windows.stream().mapToDouble(w -> Double.parseDouble(w.get("errs"))).sum(),
                    mean(windows, "srvCores"), mean(windows, "srvUsPerReq"), rangePct(windows, "srvUsPerReq"),
                    has(windows, "cliUsPerReq") ? mean(windows, "cliUsPerReq") : 0,
                    has(windows, "cliUsPerReq") ? rangePct(windows, "cliUsPerReq") : 0));
        });
        return rows;
    }

    /** 同一个配置的各次服务端启动；没有 run 字段的旧结果文件都算第 1 次 */
    private static Map<String, List<Map<String, String>>> runsOf(List<Map<String, String>> windows) {
        Map<String, List<Map<String, String>>> runs = new LinkedHashMap<>();
        windows.forEach(w -> runs.computeIfAbsent(w.getOrDefault("run", "1"), k -> new ArrayList<>()).add(w));
        return runs;
    }

    /**
     * 跨启动的波动：每次启动先取各窗口平均，再看最大和最小相差多少（占总平均的百分比）。
     * 只有一次启动时为 0，看不出启动之间的差别
     */
    private static double rangePct(List<Map<String, String>> windows, String key) {
        List<Double> perRun = runsOf(windows).values().stream().map(run -> mean(run, key)).toList();
        double average = mean(windows, key);
        if (perRun.size() < 2 || average == 0) {
            return 0;
        }
        return (java.util.Collections.max(perRun) - java.util.Collections.min(perRun)) / average * 100;
    }

    /** 旧的结果文件没有客户端 CPU 字段 */
    private static boolean has(List<Map<String, String>> windows, String key) {
        return windows.stream().allMatch(w -> w.containsKey(key));
    }

    private static double mean(List<Map<String, String>> windows, String key) {
        return windows.stream().mapToDouble(w -> Double.parseDouble(w.get(key))).average().orElse(0);
    }

    static String table(List<Row> rows, List<Row> baseline) {
        Map<String, Row> base = new HashMap<>();
        if (baseline != null) {
            baseline.forEach(row -> base.put(row.key(), row));
        }
        boolean client = rows.stream().anyMatch(row -> row.clientUsPerReq() > 0);
        StringBuilder out = new StringBuilder();
        out.append("| 配置 | 并发 | 连接 | 启动 | 窗口 | QPS | QPS跨启动相差 | P50(ms) | P99(ms) | P99.9(ms) | 错误 | 服务端核数 | 每请求CPU(µs) | CPU跨启动相差 |");
        out.append(client ? " 客户端每请求CPU(µs) | 客户端CPU跨启动相差 |" : "");
        out.append(baseline == null ? "\n" : " QPS变化 | CPU变化 |\n");
        out.append("|---|---|---|---|---|---|---|---|---|---|---|---|---|---|");
        out.append(client ? "---|---|" : "");
        out.append(baseline == null ? "\n" : "---|---|\n");
        for (Row row : rows) {
            out.append(String.format(Locale.ROOT,
                    "| %s | %d | %d | %d | %d | %.0f | %s | %.3f | %.3f | %.3f | %d | %.2f | %.1f | %s |",
                    row.label(), row.concurrency(), row.channels(), row.runs(), row.windows(), row.rps(),
                    range(row.runs(), row.rpsRangePct()), row.p50(), row.p99(), row.p999(), row.errors(),
                    row.cores(), row.usPerReq(), range(row.runs(), row.usRangePct())));
            if (client) {
                out.append(String.format(Locale.ROOT, " %.1f | %s |", row.clientUsPerReq(),
                        range(row.runs(), row.clientUsRangePct())));
            }
            if (baseline != null) {
                Row reference = base.get(row.key());
                out.append(reference == null ? " - | - |"
                        : String.format(Locale.ROOT, " %+.1f%% | %+.1f%% |",
                        percent(row.rps(), reference.rps()), percent(row.usPerReq(), reference.usPerReq())));
            }
            out.append('\n');
        }
        return out.toString();
    }

    private static String range(int runs, double pct) {
        return runs < 2 ? "-" : String.format(Locale.ROOT, "%.1f%%", pct);
    }

    private static double percent(double value, double reference) {
        return reference == 0 ? 0 : (value - reference) / reference * 100;
    }

    private static List<Row> read(String file) throws IOException {
        return summarize(Files.readAllLines(Path.of(file)));
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1 || args.length > 2) {
            System.err.println("用法: Summarize <本次结果文件> [基线结果文件]");
            System.exit(2);
        }
        System.out.print(table(read(args[0]), args.length == 2 ? read(args[1]) : null));
    }
}
