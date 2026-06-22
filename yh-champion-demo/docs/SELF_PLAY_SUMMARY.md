# yh-champion-demo 对战复盘摘要

本文记录当前策略的实战证据、失败实验和保留结论。所有回放结论均由 AI 阅读 `data.csv`、`debug_replay.txt`、`replay.txt` 与客户端日志后手工分析，不使用脚本生成分析结论。

## 当前策略版本

```text
yh-champion-v1
```

主要代码：

```text
src/main/java/com/huawei/game/demo/yhchampion/YhChampionPlannerAgent.java
src/main/resources/strategy/yh-champion-v1.json
```

## 已保留的关键增强

### 1. S04 开局水路争夺

L5 失利复盘显示，旧策略在 S04 码头窗口输掉 DOCK 争夺后，会在 S05/S09 连续落后。当前策略在以下条件满足时从 S02 主动争 S04：

- 当前位于 S02。
- round 不超过 120。
- 原始任务分仍低于 30。
- S04 存在 WATER/CLAIM_TASK 高价值任务。
- 对手尚未能在我方到达前完成该任务。

保留测试：

```text
contestsOpeningS04WaterTaskWhenOpponentLeadIsStillSmall
switchesToS03RoadTaskWhenOpponentWinsEarlyS04WaterLead
```

### 2. DOCK 第三张牌修正

旧 DOCK 牌序第三张可能退化到 `BING_ZHENG`，导致与 L5 同窗口时丢 S04 主动权。当前配置和内置兜底均保留：

```text
DOCK: QIANG_XING, YAN_DIE, XIAN_GONG
```

在实际窗口选择中，当高价值牌可用时持续选择 `XIAN_GONG`，避免第三回合弱牌导致窗口失败。

保留测试：

```text
keepsXianGongOnThirdDockWindowWhenFuelCardsAreUnavailable
```

### 3. 远端抢点过滤

策略使用对手移动边进度判断远端节点是否还值得抢：

- `currentNodeId`
- `nextNodeId`
- `edgeProgressMs`
- `edgeTotalMs`
- 地图最短路成本
- 明显先手阈值 2 回合

如果对手预计至少领先 2 回合到达远端任务或资源节点，YH 不再盲目追逐，转向交付、当前路线任务或其他窗口。

### 4. S02 不能跳过 PROCESS

曾尝试从 S02 直接跳往 S03，结果服务端连续拒绝移动，分数明显下降。结论是 S02 属于强制 `TRANSFER` 站点，必须完成 `PROCESS` 后才能离站。

保留测试：

```text
keepsS02TransferBeforeEarlyRoadTaskChainBecauseServerRequiresIt
```

### 5. S07 冰盒让路给 S09 中局任务

当 S09/S10 中局任务窗口已经打开时，S07 冰盒收益不如抢 S09/S10 任务确定。当前策略会在指定窗口内跳过远端低收益冰盒，避免被 L5/L6 抢走中局任务节奏。

保留测试：

```text
skipsS07IceBoxWhenMidgameS09TaskWindowIsOpen
```

## 已回滚或不再采用的实验

### 强追已失先手的 S03/ROAD

为了追 Python Champion，曾尝试在对手已经明显占据 S03 先手时仍强行跟进 ROAD 主干。实战结果下降：YH 抢不到 S03 首任务，又放弃了 S05 稳定补分，任务分和交付档位都变差。

结论：不保留该规则。ROAD 只能作为明确可抢任务或水路已失窗口时的反制，而不是盲追。

## 最新验收对战

本轮用户已收敛范围：只和 `battle-demo/client` 下 4 个对手对战，赢这 4 个即可；不再运行 `official-tier-demos`。

输出目录：

```text
D:\Lychee-game\Lychee-Demo\battle-demo\result\yh-final-client4-20260622-203621
```

| 序号 | 对手 | YH 总分 | 对手总分 | 关键结论 |
| ---: | --- | ---: | ---: | --- |
| 1 | champion_agent.py | 498 | 80 | 对手未交付，YH 用水路闭环稳定取胜 |
| 2 | champion-battle-demo.jar | 683 | 497 | 任务分和交付档位领先；服务端 GameOver 汇总落盘异常，但回放最后帧已结束并给出比分 |
| 3 | Lychee-Arena-L5-Client-1.0.0-SNAPSHOT.jar | 603 | 444 | S04 水路争夺和 DOCK 第三牌修正追回关键节奏 |
| 4 | official-l6-champion-planner-java-client.jar | 701 | 428 | L6 早 5 回合交付但任务分为 0，YH 靠 140 任务分和更高交付档位胜出 |

每局输出目录均已写入 `analysis.md`。

## 当前结论

YH 已在最新指定的 4 个 `battle-demo/client` 对手上全胜。下一轮优化只应围绕这些对手和同一服务端环境展开，避免引入与当前验收范围无关的 official-tier 调整。
