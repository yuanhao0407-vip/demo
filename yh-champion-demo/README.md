# yh-champion-demo 参赛客户端

`yh-champion-demo` 是“一骑红尘：荔枝争运战”的 Java 21 参赛客户端。项目目标不是演示，而是作为可提交、可对战、可复盘迭代的强对战客户端。

项目目录：

```text
D:\Lychee-game\Lychee-Demo\official-battle-demo\yh-champion-demo
```

## 工程结构

```text
YhChampionClientApplication
  -> YhChampionConfig
  -> NettyGameClient
      -> LengthPrefixedJsonFrameDecoder
      -> LengthPrefixedJsonFrameEncoder
      -> ProtocolMessages
  -> YhChampionPlannerAgent
      -> MapGraph
      -> GrandmasterStrategyTables
      -> RoleActionCommand
```

客户端自包含协议、网络、地图、策略和测试代码，不依赖其他 demo 工程源码运行。

## 核心策略

- 交付闭环优先：始终围绕 S14 验核和 S15 交付构造路线，不为低价值机会牺牲交付。
- S04 水路争夺：开局从 S02 出发时，如果 S04/T_002 仍可赶上处理窗口，优先抢水路线，切断 L5 类对手的节奏。
- S03 反制兜底：如果 S04/S05 水路窗口已经明显失先手，则切到 S03 官道基础任务线。
- 任务分门槛：原始任务分不足 90 时放宽绕路预算，优先补足能改变交付档位和总分结构的任务。
- 远端抢点过滤：根据对手 `currentNodeId`、`nextNodeId`、`edgeProgressMs`、`edgeTotalMs` 估计到点时间，停止追逐已明显失先手的资源或任务。
- 强制处理站点：S02、S04、S05、S11、S13 必须先 `PROCESS`，不能跳过。
- 窗口出牌：DOCK 争夺保留 `XIAN_GONG` 作为第三张有效牌，避免关键 S04 窗口因低价值牌掉节奏。

## 构建

```bash
mvn test
mvn package
```

主要产物：

```text
target/yh-champion-demo.jar
target/start.sh
```

## 运行

比赛平台通过三位置参数启动：

```bash
target/start.sh <playerId> <host> <port>
```

也可以直接运行 jar：

```bash
java -jar target/yh-champion-demo.jar <playerId> <host> <port>
```

本地调试支持命名参数：

```bash
java -jar target/yh-champion-demo.jar \
  --backend-host 127.0.0.1 \
  --backend-port 30000 \
  --player-id 1001 \
  --player-name demo-yh-champion
```

## 环境变量

```text
LYCHEE_BACKEND_HOST
LYCHEE_BACKEND_PORT
LYCHEE_PLAYER_ID
LYCHEE_PLAYER_NAME
LYCHEE_CLIENT_VERSION
```

命令行参数优先级高于环境变量。

## 实战结果摘要

最新验收范围只包含 `battle-demo/client` 下 4 个对手，不再运行 `official-tier-demos`。

输出目录：

```text
D:\Lychee-game\Lychee-Demo\battle-demo\result\yh-final-client4-20260622-203621
```

| 对手 | 我方分数 | 对手分数 | 结果 |
| --- | ---: | ---: | --- |
| champion_agent.py | 498 | 80 | YH 胜 |
| champion-battle-demo.jar | 683 | 497 | YH 胜 |
| Lychee-Arena-L5-Client-1.0.0-SNAPSHOT.jar | 603 | 444 | YH 胜 |
| official-l6-champion-planner-java-client.jar | 701 | 428 | YH 胜 |

每个对战输出目录都包含 `analysis.md`。分析结论由 AI 手工阅读回放、日志和结果文件后写入，不使用脚本生成分析结论。

## 交付要求

最终交付物命名必须和项目一致：

```text
yh-champion-demo.jar
yh-champion-demo.zip
```

项目不保留 `legacy` 目录，不把 `champion-battle-demo.jar` 作为本项目交付物。`champion-battle-demo.jar` 只作为 `battle-demo/client` 下的对战对手名称出现。
