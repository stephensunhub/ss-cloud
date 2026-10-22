package men.sbb8.benchmark;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SummarizeTest {

    private static final List<String> RESULTS = List.of(
            "### 环境: 测试",
            "RESULT label=a conc=64 ch=8 win=1 rps=100000 p50=0.600ms p99=1.500ms p999=2.000ms max=9.0ms errs=0 srvCores=14.00 srvUsPerReq=140.0",
            "RESULT label=a conc=64 ch=8 win=2 rps=102000 p50=0.620ms p99=1.700ms p999=2.400ms max=11.0ms errs=1 srvCores=14.20 srvUsPerReq=142.0",
            "RESULT label=b conc=1 ch=1 win=1 rps=20000 p50=0.048ms p99=0.080ms p999=0.250ms max=2.0ms errs=0 srvCores=0.70 srvUsPerReq=35.0",
            "");

    @Test
    void windowsOfTheSameLabelAreAveragedAndOtherLinesIgnored() {
        List<Summarize.Row> rows = Summarize.summarize(RESULTS);
        assertThat(rows).extracting(Summarize.Row::label).containsExactly("a", "b");

        Summarize.Row a = rows.getFirst();
        assertThat(a.windows()).isEqualTo(2);
        assertThat(a.concurrency()).isEqualTo(64);
        assertThat(a.channels()).isEqualTo(8);
        assertThat(a.rps()).isEqualTo(101_000);
        assertThat(a.p99()).isCloseTo(1.6, within(1e-9));
        assertThat(a.errors()).isEqualTo(1);
        assertThat(a.usPerReq()).isCloseTo(141.0, within(1e-9));
    }

    @Test
    void sameLabelAtDifferentConcurrencyIsNotMerged() {
        // 实验 1：同一个标签对应多个并发档位，不能被合并成一行
        List<Summarize.Row> rows = Summarize.summarize(List.of(
                "RESULT label=sweep conc=1 ch=1 win=1 rps=20000 p50=0.05ms p99=0.08ms p999=0.2ms max=1ms errs=0 srvCores=0.7 srvUsPerReq=35.0",
                "RESULT label=sweep conc=32 ch=8 win=1 rps=100000 p50=0.3ms p99=0.8ms p999=1.5ms max=5ms errs=0 srvCores=13 srvUsPerReq=130.0",
                "RESULT label=sweep conc=32 ch=8 win=2 rps=102000 p50=0.3ms p99=0.9ms p999=1.7ms max=5ms errs=0 srvCores=13 srvUsPerReq=132.0"));

        assertThat(rows).extracting(Summarize.Row::concurrency).containsExactly(1, 32);
        assertThat(rows).extracting(Summarize.Row::windows).containsExactly(1, 2);
        assertThat(rows.get(1).rps()).isEqualTo(101_000);
    }

    @Test
    void differencesBetweenServerLaunchesAreReported() {
        // 同一个配置启动了两次服务端：第 1 次约 10 万，第 2 次约 9.4 万
        List<Summarize.Row> rows = Summarize.summarize(List.of(
                "RESULT label=x run=1 conc=64 ch=8 win=1 rps=100000 p50=1ms p99=2ms p999=3ms max=9ms errs=0 srvCores=14 srvUsPerReq=140.0",
                "RESULT label=x run=1 conc=64 ch=8 win=2 rps=100000 p50=1ms p99=2ms p999=3ms max=9ms errs=0 srvCores=14 srvUsPerReq=140.0",
                "RESULT label=x run=2 conc=64 ch=8 win=1 rps=94000 p50=1ms p99=2ms p999=3ms max=9ms errs=0 srvCores=14 srvUsPerReq=150.0",
                "RESULT label=x run=2 conc=64 ch=8 win=2 rps=94000 p50=1ms p99=2ms p999=3ms max=9ms errs=0 srvCores=14 srvUsPerReq=150.0"));

        Summarize.Row row = rows.getFirst();
        assertThat(row.runs()).isEqualTo(2);
        assertThat(row.windows()).isEqualTo(4);
        assertThat(row.rps()).isEqualTo(97_000);
        // (100000 - 94000) / 97000
        assertThat(row.rpsRangePct()).isCloseTo(6.186, within(0.001));
        // (150 - 140) / 145
        assertThat(row.usRangePct()).isCloseTo(6.897, within(0.001));
        assertThat(Summarize.table(rows, null)).contains("| 2 | 4 | 97000 | 6.2% |");
    }

    @Test
    void resultsWithoutARunFieldCountAsASingleLaunch() {
        Summarize.Row row = Summarize.summarize(RESULTS).getFirst();
        assertThat(row.runs()).isEqualTo(1);
        assertThat(row.rpsRangePct()).isZero();
        assertThat(Summarize.table(List.of(row), null)).contains("| 1 | 2 | 101000 | - |");
    }

    @Test
    void baselineIsMatchedByLabelConcurrencyAndChannels() {
        List<Summarize.Row> baseline = Summarize.summarize(List.of(
                "RESULT label=sweep conc=1 ch=1 win=1 rps=20000 p50=0.05ms p99=0.08ms p999=0.2ms max=1ms errs=0 srvCores=0.7 srvUsPerReq=35.0",
                "RESULT label=sweep conc=32 ch=8 win=1 rps=100000 p50=0.3ms p99=0.8ms p999=1.5ms max=5ms errs=0 srvCores=13 srvUsPerReq=130.0"));
        List<Summarize.Row> current = Summarize.summarize(List.of(
                "RESULT label=sweep conc=1 ch=1 win=1 rps=22000 p50=0.05ms p99=0.08ms p999=0.2ms max=1ms errs=0 srvCores=0.7 srvUsPerReq=35.0",
                "RESULT label=sweep conc=32 ch=8 win=1 rps=90000 p50=0.3ms p99=0.8ms p999=1.5ms max=5ms errs=0 srvCores=13 srvUsPerReq=130.0"));

        String table = Summarize.table(current, baseline);

        assertThat(table).contains("+10.0% |").contains("-10.0% |");
    }

    @Test
    void tableShowsTheChangeAgainstTheBaselineByLabel() {
        List<Summarize.Row> baseline = Summarize.summarize(RESULTS);
        List<Summarize.Row> current = Summarize.summarize(List.of(
                "RESULT label=a conc=64 ch=8 win=1 rps=90900 p50=0.6ms p99=1.6ms p999=2.1ms max=9.0ms errs=0 srvCores=14.00 srvUsPerReq=155.1",
                "RESULT label=c conc=1 ch=1 win=1 rps=1 p50=1ms p99=1ms p999=1ms max=1ms errs=0 srvCores=1 srvUsPerReq=1"));

        String table = Summarize.table(current, baseline);

        assertThat(table).contains("| a | 64 | 8 | 1 | 1 | 90900 |").contains("-10.0% | +10.0% |");
        // 基线里没有的配置不报变化
        assertThat(table).contains("| c | 1 | 1 | 1 | 1 | 1 |").contains("- | - |");
    }

    @Test
    void tableWithoutBaselineHasNoChangeColumns() {
        String table = Summarize.table(Summarize.summarize(RESULTS), null);
        assertThat(table).doesNotContain("QPS变化").contains("| a | 64 | 8 | 1 | 2 |");
    }
}
