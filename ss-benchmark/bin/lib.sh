#!/bin/bash
# 框架开销基准的公共函数，被 run-all.sh、check-flags.sh 引用，不单独运行。

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MODULE="$ROOT/ss-benchmark"
PORT="${BENCH_PORT:-19101}"
SERVER_OPTS="${BENCH_SERVER_OPTS:--Xms1g -Xmx1g}"
CLIENT_OPTS="${BENCH_CLIENT_OPTS:--Xms512m -Xmx1g}"
SERVER_PID=""

# 构建模块并取得运行时类路径（会把相关模块安装到本地 Maven 仓库）
build() {
  (cd "$ROOT" && ./mvnw -q -Pbenchmark -pl ss-benchmark -am install -DskipTests) || return 1
  (cd "$ROOT" && ./mvnw -q -Pbenchmark -pl ss-benchmark dependency:build-classpath \
      -Dmdep.outputFile=target/classpath.txt) || return 1
  CP="$MODULE/target/classes:$(cat "$MODULE/target/classpath.txt")"
}

# start_server raw|spring [-Dxxx ...]   raw 是原生 grpc-java，spring 是 ss-grpc-server-starter
start_server() {
  local kind=$1
  shift
  local main=men.sbb8.benchmark.SpringServer
  if [ "$kind" = raw ]; then
    main=men.sbb8.benchmark.RawServer
  fi
  if (echo > /dev/tcp/127.0.0.1/"$PORT") 2>/dev/null; then
    echo "端口 $PORT 已被占用，请先停掉占用它的进程，或设置 BENCH_PORT" >&2
    return 1
  fi
  # shellcheck disable=SC2086  # SERVER_OPTS 需要按空格拆成多个参数
  java $SERVER_OPTS -Dbench.port="$PORT" "$@" -cp "$CP" "$main" > "$MODULE/target/server.log" 2>&1 &
  SERVER_PID=$!
  for _ in $(seq 1 150); do
    if (echo > /dev/tcp/127.0.0.1/"$PORT") 2>/dev/null; then
      return 0
    fi
    sleep 0.2
  done
  echo "服务端没有在 30 秒内启动：" >&2
  tail -20 "$MODULE/target/server.log" >&2
  return 1
}

stop_server() {
  if [ -n "$SERVER_PID" ]; then
    kill "$SERVER_PID" 2>/dev/null
    wait "$SERVER_PID" 2>/dev/null
    SERVER_PID=""
  fi
}

# client 参数...   自动带上服务端进程号（用来统计服务端 CPU），只输出 RESULT 行。
# 环境变量 CLIENT_EXTRA 里的内容追加到客户端 JVM 的参数（-D 开关，用于 --client spring）
client() {
  # shellcheck disable=SC2086
  java $CLIENT_OPTS $CLIENT_EXTRA -cp "$CP" men.sbb8.benchmark.BenchClient \
      --target "127.0.0.1:$PORT" --pid "$SERVER_PID" "$@" | grep '^RESULT'
}
