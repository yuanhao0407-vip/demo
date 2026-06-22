# yh-champion-demo 实施计划

## 目标

在 `Lychee-Demo/official-battle-demo/yh-champion-demo` 下实现 Java 21 参赛客户端，项目名、Maven 产物、入口类、启动脚本和提交包统一命名为 `yh-champion-demo`。

## 约束

- 项目目录固定为 `official-battle-demo/yh-champion-demo`。
- 不使用旧产物名 `champion-battle-demo.jar`。
- 不创建或保留 `legacy` 目录。
- Markdown 文档使用中文。
- 对战验收只覆盖 `battle-demo/client` 下 4 个对手。
- 每个对战输出目录写入中文 `analysis.md`。
- 回放分析结论由 AI 手工判断，不使用脚本生成分析结论。

## 已完成

1. 读取参赛任务书和通信协议，确认 5 位长度前缀加 UTF-8 JSON 的 TCP 长连接协议。
2. 创建 Java Maven 客户端工程，入口、配置、Netty 通信、协议帧、地图图模型和策略代理已实现。
3. 使用 TDD 补齐关键策略：
   - S02 强制 PROCESS。
   - S04 开局水路争夺。
   - S03 反制兜底。
   - S09/S10 中局任务优先。
   - DOCK 第三牌保留 `XIAN_GONG`。
4. 完成 4 个 `battle-demo/client` 对手的实战验收，全胜。
5. 每局输出目录已写入中文 `analysis.md`。

## 验收输出

```text
D:\Lychee-game\Lychee-Demo\battle-demo\result\yh-final-client4-20260622-203621
```

| 对手 | YH | 对手 | 结果 |
| --- | ---: | ---: | --- |
| champion_agent.py | 498 | 80 | YH 胜 |
| champion-battle-demo.jar | 683 | 497 | YH 胜 |
| Lychee-Arena-L5-Client-1.0.0-SNAPSHOT.jar | 603 | 444 | YH 胜 |
| official-l6-champion-planner-java-client.jar | 701 | 428 | YH 胜 |

## 交付物

```text
target/yh-champion-demo.jar
target/start.sh
yh-champion-demo.zip
README.md
docs/yh-champion-demo-sdd.md
docs/SELF_PLAY_SUMMARY.md
```
