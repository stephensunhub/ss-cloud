#!/bin/bash
# 客户端 starter 开销：服务端固定为原生 grpc-java（RawServer，空处理器），只改变客户端的创建方式和开关，
# 看客户端自己的 CPU（cliUsPerReq，每请求微秒）和延迟。每个变体启动 3 次，按轮交错排列，
# 每一轮重启一次服务端；服务端一侧的差别不是这个实验要看的。
#
#   ss-benchmark/bin/run-client.sh [结果文件]
#
# 环境变量：QUICK=1 缩短时间只做自检；BASELINE=文件 用来对比的基线，默认取 baselines/client/ 里最新的；BENCH_PORT 服务端口。
# 运行期间请不要在这台机器上做别的耗 CPU 的事。

source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
build || exit 1
trap stop_server EXIT

OUT="${1:-$MODULE/target/client-results-$(date +%Y%m%d-%H%M%S).txt}"
if [ "${QUICK:-0}" = 1 ]; then
  W="--warmup 2 --measure 3 --windows 1"; RUNS=1
else
  W="--warmup 8 --measure 10 --windows 2"; RUNS=3
fi

# 名称|--client 取值|客户端 JVM 的 -D 开关
VARIANTS=(
  "raw|raw|"
  "deadline-only|deadline|"
  "spring-obs-off-no-deadline|spring|-Dspring.grpc.client.observation.enabled=false -Dss.grpc.client.default-deadline=0s"
  "spring-obs-off|spring|-Dspring.grpc.client.observation.enabled=false"
  "spring-metrics|spring|-Dmanagement.tracing.enabled=false"
  "spring-prod-sampling-0.1|spring|-Dspring.profiles.active=prod"
  "spring-dev-sampling-1.0|spring|"
)

log() { echo "$@" | tee -a "$OUT"; }
: > "$OUT"
log "### 环境: $(sysctl -n machdep.cpu.brand_string 2>/dev/null || uname -m), $(getconf _NPROCESSORS_ONLN) 核, JDK $(java -version 2>&1 | head -1 | cut -d'"' -f2), 开始 $(date '+%F %T')$([ "${QUICK:-0}" = 1 ] && echo ', QUICK 模式')"
log "### 负载(开始前): $(uptime | sed 's/.*load averages*: //')"
log; log "### 实验 4：客户端 starter 开销（服务端固定为 raw；并发 64，8 条连接；每个变体 ${RUNS} 次，交错进行）"
for run in $(seq 1 "$RUNS"); do
  start_server raw || exit 1
  for variant in "${VARIANTS[@]}"; do
    IFS='|' read -r name mode opts <<< "$variant"
    # shellcheck disable=SC2086
    CLIENT_EXTRA="$opts" client --label "exp4-$name" --client "$mode" --run "$run" --concurrency 64 --channels 8 $W | tee -a "$OUT"
  done
  stop_server
done
log; log "### 结束 $(date '+%F %T')，负载: $(uptime | sed 's/.*load averages*: //')"

echo
echo "================ 汇总（各窗口取平均）================"
BASELINE="${BASELINE:-$(ls "$MODULE"/baselines/client/*.txt 2>/dev/null | sort | tail -1)}"
if [ "${QUICK:-0}" != 1 ] && [ -n "$BASELINE" ]; then
  echo "对比基线: $BASELINE（同一台机器上才有意义）"
  java -cp "$CP" men.sbb8.benchmark.Summarize "$OUT" "$BASELINE"
else
  java -cp "$CP" men.sbb8.benchmark.Summarize "$OUT"
fi
echo "原始结果: $OUT"
