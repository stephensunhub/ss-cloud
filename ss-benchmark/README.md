# ss-benchmark

**框架开销基准**，不是系统容量测试。用一个空处理器的 gRPC 服务，测 `ss-grpc-server-starter`（以及它带来的指标、追踪）
本身花多少 CPU 和延迟，用来：

- 回答"某个开关值不值得开"：直方图桶、追踪采样率、观测整体开销是多少
- 改动 starter 之后做**回归对比**，确认没有无意中拖慢热路径
- 校准对连接数的判断（单条连接能扛多少）

它**不能**告诉你业务系统的容量：没有业务逻辑、数据库、缓存，也不涉及真实网络、TLS、Aeron。
基线数字和解读见 [BASELINE.md](BASELINE.md)。

## 不参与默认构建

模块放在父 POM 的 `benchmark` profile 里，默认的 `./mvnw verify` 不会构建它：

```bash
./mvnw -Pbenchmark -pl ss-benchmark -am verify      # 构建并跑它的单元测试
```

在 IDE 里要看到这个模块，需要在 Maven 面板勾选 `benchmark` profile。
升版本号用 `./mvnw versions:set -Pbenchmark ...`，否则这个模块的版本号不会一起更新。

## 怎么跑

```bash
ss-benchmark/bin/run-all.sh              # 三个实验，约 18 分钟，结果写到 ss-benchmark/target/results-<时间>.txt
QUICK=1 ss-benchmark/bin/run-all.sh      # 缩短时间，只确认脚本能跑通，结果不能用来比较
ss-benchmark/bin/run-client.sh           # 实验 4：客户端 starter 的开销，约 12 分钟，和基线（baselines/client/）对比
ss-benchmark/bin/check-flags.sh          # 确认"去掉直方图桶"的开关确实有效（改动指标配置后先跑这个）
```

脚本会自己构建模块、启动服务端、跑完后汇总，并和 `baselines/` 里最新的基线对比（`BASELINE=文件` 可指定）。
也可以单独汇总已有的结果文件：

```bash
java -cp ss-benchmark/target/classes men.sbb8.benchmark.Summarize 本次结果.txt [基线.txt]
```

**运行期间请不要在这台机器上做别的耗 CPU 的事。** 压测的客户端和服务端在同一台机器上，会占满大部分核。

## 三个实验

| 实验 | 变量 | 回答什么 |
|---|---|---|
| 1 并发扫描 | 并发 1 / 8 / 32 / 128，原生 grpc-java 对 starter 的 prod 配置；每种服务端启动 2 次 | starter 相对原生有没有额外开销；这台机器的饱和点在哪 |
| 2 观测开关 | 原生 / 关闭观测 / 指标（有无直方图桶）/ 追踪采样 0.1 与 1.0；每个配置启动 3 次，**交错**排列 | 观测的各个部分各花多少 |
| 3 连接数 | 1 / 2 / 4 / 8 / 16 条连接，并发 256；启动 1 次 | 单条连接够不够用 |
| 4 客户端 starter（`bin/run-client.sh`，单独运行） | 服务端固定为原生；客户端：原生 / 只加默认超时 / Spring 的各种开关；每个启动 3 次，**交错** | 客户端 starter 的默认超时和观测各花多少客户端 CPU |

**关键的对比必须多次启动、交错进行**：同一次服务端启动内各窗口很稳定，但不同次启动之间（JIT、GC 布局不同）可以差到 5% 以上，
单次启动的 A/B 对比曾经得出过自相矛盾的结论（见 [BASELINE.md](BASELINE.md)）。

要增加一个配置变体，在 `bin/run-all.sh` 的实验 2 里加一行 `run2 名称 spring -D开关`，服务端的所有开关都是 `-D` 系统属性。

## 怎么读结果

| 列 | 含义 |
|---|---|
| QPS | 窗口内完成的请求数 / 时长。闭环压测：保持固定数量的请求在途，所以饱和后 QPS 是**这台机器**的上限，不是服务端的上限 |
| P50 / P99 / P99.9 | 客户端测到的完整往返延迟（毫秒） |
| 服务端核数 | 服务端进程在窗口内平均占用的核数（`ps` 的 CPU 时间之差） |
| 每请求 CPU（µs） | 服务端核数 / QPS。**只在相同并发下才能互相比较**：并发越高争抢越厉害，这个数越大，与框架无关 |

- 同一个配置的多个窗口、多次启动，表里是平均值；"QPS 跨启动相差"和"CPU 跨启动相差"是各次启动的平均值里最大和最小相差多少（占总平均的百分比），
  只启动一次时显示 `-`（看不出启动之间的差别，结论要保守）
- 基线上的经验：**QPS 差 2% 以内、每请求 CPU 差 5% 以内属于噪声**，不要当成变化
- 比较只在**同一台机器**上有意义，换机器、JDK、Spring Boot 大版本后要重新建立基线

## 局限（读结果时请记住）

- 客户端和服务端在同一台机器上，回环、明文、64 字节、空处理器；服务端的真实处理器、数据库、网络、TLS 都不在其中
- 追踪**没有配置导出地址**，所以只测了创建 span 的开销，没测发送（批处理、OTLP/HTTP）的开销
- 实验 1–3 的压测客户端是原生 grpc-java，不含客户端 starter 的开销；实验 4 专门测它，并且看的是**客户端自己的 CPU**
- 多机、真实 k8s、Aeron 都没有覆盖

## 结构

| 路径 | 说明 |
|---|---|
| `src/main/java/.../Echo` | 空处理器的服务定义，Spring 版和原生版共用 |
| `.../RawServer`、`SpringServer` | 对照组（原生 grpc-java）和使用 starter 的服务端 |
| `.../BenchClient` | 闭环压测客户端：预热、分窗口测量、统计延迟分位数、服务端和客户端自己的 CPU；`--client raw/deadline/spring` 选择连接的创建方式 |
| `.../SpringChannels` | 用 ss-grpc-client-starter（Spring gRPC 客户端、观测、指标、追踪）创建压测连接，线程模型与原生一致 |
| `.../Summarize` | 把结果汇总成表格，可与基线对比 |
| `bin/` | `run-all.sh`（实验 1–3）、`run-client.sh`（实验 4）、`check-flags.sh`、`lib.sh`（公共函数） |
| `baselines/` | 历史基线的原始结果，文件名带日期和机器 |
