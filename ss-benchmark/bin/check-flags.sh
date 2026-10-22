#!/bin/bash
# 确认实验 2 里"去掉直方图桶"的开关确实有效：默认配置有桶，覆盖成空值后没有。
# 改动指标配置或升级 Spring Boot 后，先跑这个，再信任实验 2 的 metrics-no-buckets 结果。

source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
build || exit 1
trap stop_server EXIT

for opts in "-Dmanagement.tracing.enabled=false" \
            "-Dmanagement.tracing.enabled=false -Dmanagement.metrics.distribution.slo.grpc.server="; do
  # shellcheck disable=SC2086
  start_server spring -Dbench.scrape.port=19191 $opts || exit 1
  client --label check --concurrency 8 --channels 2 --warmup 1 --measure 1 --windows 1 > /dev/null
  echo "[$opts] -> grpc_server_seconds_bucket 行数: $(curl -s localhost:19191/metrics | grep -c 'grpc_server_seconds_bucket')"
  stop_server
done
