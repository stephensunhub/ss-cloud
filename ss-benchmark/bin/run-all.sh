#!/bin/bash
# 框架开销基准：三个实验（并发扫描、观测开关对比、连接数），结果写到文件并和最新的基线对比。
#
#   ss-benchmark/bin/run-all.sh [结果文件]
#
# 环境变量：
#   QUICK=1      缩短时间只做自检（确认脚本能跑通），结果不能用来比较
#   BASELINE=文件 用来对比的基线，默认取 baselines/ 里最新的一个
#   BENCH_PORT   服务端口，默认 19101
# 运行期间请不要在这台机器上做别的耗 CPU 的事，否则数字噪声会很大。

source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
build || exit 1
trap stop_server EXIT

OUT="${1:-$MODULE/target/results-$(date +%Y%m%d-%H%M%S).txt}"
if [ "${QUICK:-0}" = 1 ]; then
  W1="--warmup 2 --measure 3 --windows 1"; W2="$W1"; W3="$W1"; CONCS="1 32"; CHANNELS="1 2"; RUNS1=1; RUNS2=1
else
  W1="--warmup 4 --measure 8 --windows 2"; W2="--warmup 8 --measure 10 --windows 2"; W3="--warmup 5 --measure 10 --windows 2"
  CONCS="1 8 32 128"; CHANNELS="1 2 4 8 16"; RUNS1=2; RUNS2=3
fi

log() { echo "$@" | tee -a "$OUT"; }
: > "$OUT"
log "### 环境: $(sysctl -n machdep.cpu.brand_string 2>/dev/null || uname -m), $(getconf _NPROCESSORS_ONLN) 核, JDK $(java -version 2>&1 | head -1 | cut -d'"' -f2), 开始 $(date '+%F %T')$([ "${QUICK:-0}" = 1 ] && echo ', QUICK 模式')"
log "### 负载(开始前): $(uptime | sed 's/.*load averages*: //')"

log; log "### 实验 1：并发扫描（raw = 原生 grpc-java；spring-prod = ss-grpc-server-starter 的 prod 配置；8 条连接；每种启动 ${RUNS1} 次）"
for run in $(seq 1 "$RUNS1"); do
  for kind in raw spring-prod; do
    if [ "$kind" = raw ]; then start_server raw || exit 1; else start_server spring $PROD_OPTS || exit 1; fi
    for conc in $CONCS; do
      # shellcheck disable=SC2086
      client --label "exp1-$kind" --run "$run" --concurrency "$conc" --channels 8 $W1 | tee -a "$OUT"
    done
    stop_server
  done
done

# 实验 2 的配置变体：名称|种类|-D 开关。每个变体启动 ${RUNS2} 次，按"第 1 轮全部变体、第 2 轮全部变体……"交错排列，
# 这样不同次启动之间的差别（JIT、GC 布局等）和机器状态的缓慢漂移会均摊到每个变体上
VARIANTS=(
  "raw|raw|"
  "obs-off|spring|-Dspring.grpc.server.observation.enabled=false"
  "metrics-no-buckets|spring|-Dmanagement.tracing.enabled=false -Dmanagement.metrics.distribution.slo.grpc.server="
  "metrics-buckets|spring|-Dmanagement.tracing.enabled=false"
  "prod-sampling-0.1|spring|$PROD_OPTS"
  "dev-sampling-1.0|spring|"
)
log; log "### 实验 2：观测开关对比（并发 64，8 条连接；每个变体启动 ${RUNS2} 次，交错进行）"
for run in $(seq 1 "$RUNS2"); do
  for variant in "${VARIANTS[@]}"; do
    IFS='|' read -r name kind opts <<< "$variant"
    # shellcheck disable=SC2086  # opts 需要按空格拆成多个 -D 参数
    start_server "$kind" $opts || exit 1
    # shellcheck disable=SC2086
    client --label "exp2-$name" --run "$run" --concurrency 64 --channels 8 $W2 | tee -a "$OUT"
    stop_server
  done
done

log; log "### 实验 3：连接数（spring-prod，并发 256）"
start_server spring $PROD_OPTS || exit 1
for channels in $CHANNELS; do
  # shellcheck disable=SC2086
  client --label "exp3-ch$channels" --run 1 --concurrency 256 --channels "$channels" $W3 | tee -a "$OUT"
done
stop_server

log; log "### 结束 $(date '+%F %T')，负载: $(uptime | sed 's/.*load averages*: //')"

echo
echo "================ 汇总（各窗口取平均）================"
BASELINE="${BASELINE:-$(ls "$MODULE"/baselines/*.txt 2>/dev/null | sort | tail -1)}"
if [ "${QUICK:-0}" != 1 ] && [ -n "$BASELINE" ]; then
  echo "对比基线: $BASELINE（同一台机器上才有意义；差别在 2% 以内属于噪声）"
  java -cp "$CP" men.sbb8.benchmark.Summarize "$OUT" "$BASELINE"
else
  java -cp "$CP" men.sbb8.benchmark.Summarize "$OUT"
fi
echo "原始结果: $OUT"
