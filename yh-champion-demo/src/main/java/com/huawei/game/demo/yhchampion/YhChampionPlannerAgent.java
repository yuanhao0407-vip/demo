package com.huawei.game.demo.yhchampion;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.huawei.game.demo.yhchampion.client.DemoAgent;
import com.huawei.game.demo.yhchampion.map.MapGraph;
import com.huawei.game.demo.yhchampion.protocol.ProtocolMessages;
import com.huawei.game.demo.yhchampion.protocol.RoleActionCommand;
import com.huawei.game.demo.yhchampion.strategy.GrandmasterStrategyTables;
import com.huawei.game.demo.yhchampion.strategy.StrategyIntent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class YhChampionPlannerAgent implements DemoAgent {
    private static final Logger LOG = LoggerFactory.getLogger(YhChampionPlannerAgent.class);
    private static final String VERIFY_GATE_NODE_ID = "S14";
    private static final String PALACE_STATION_NODE_ID = "S13";
    private static final String TERMINAL_NODE_ID = "S15";
    private static final String STATE_MOVING = "MOVING";
    private static final String STATE_WAITING = "WAITING";
    private static final String STATE_PROCESSING = "PROCESSING";
    private static final String STATE_CONTESTING = "CONTESTING";
    private static final String STATE_RESTING = "RESTING";
    private static final String RESOURCE_ICE_BOX = "ICE_BOX";
    private static final String RESOURCE_FAST_HORSE = "FAST_HORSE";
    private static final String RESOURCE_SHORT_HORSE = "SHORT_HORSE";
    private static final String RESOURCE_BOAT_RIGHT = "BOAT_RIGHT";
    private static final String RESOURCE_PASS_TOKEN = "PASS_TOKEN";
    private static final String RESOURCE_OFFICIAL_PERMIT = "OFFICIAL_PERMIT";
    private static final String EFFECT_MOVE_MULTIPLIER = "MOVE_MULTIPLIER";
    private static final String ERROR_RESOURCE_NOT_ENOUGH = "RESOURCE_NOT_ENOUGH";
    private static final double ICE_BOX_FRESHNESS_THRESHOLD = 80.0D;
    private static final double FINAL_LEG_ICE_BOX_FRESHNESS_THRESHOLD = 95.0D;
    private static final int MIN_RESOURCE_CLAIM_VALUE = 70;
    private static final int REMOTE_NODE_CLEAR_LEAD_ROUNDS = 2;
    private static final int TASK_SCORE_CAP = 180;
    private static final int TASK_MILESTONE_60 = 60;
    private static final int TASK_MILESTONE_90 = 90;
    private static final int TASK_MILESTONE_110 = 110;
    private static final Set<String> KEY_GUARD_NODES = Set.of("S10", "S11");
    private static final Map<String, Set<String>> DOWNSTREAM_NODES_AFTER_GUARD = Map.of(
            "S10", Set.of("S11", "S12", "S13", "S14", "S15"),
            "S11", Set.of("S12", "S13", "S14", "S15"));
    private static final Set<String> SCOUT_PRIORITY_NODES = Set.of("S10", "S11", "S13", "S14");
    private static final Set<String> PROCESS_STATIONS = Set.of("S02", "S04", "S05", "S11", "S13");
    private static final Set<String> EARLY_OPTIONAL_PROCESS_STATIONS = Set.of("S04", "S05");
    private static final Set<String> CLAIM_TASK_FUEL_TYPES = Set.of(
            RESOURCE_FAST_HORSE,
            RESOURCE_SHORT_HORSE,
            RESOURCE_BOAT_RIGHT);
    private final int playerId;
    private final GrandmasterStrategyTables strategyTables;
    private final Set<String> locallyScoutedNodes = new HashSet<>();
    private final Set<String> processedStations = new HashSet<>();
    private final Set<String> gateBreakVerifyArmedMatches = new HashSet<>();
    private final Set<String> gateBreakVerifyUsedMatches = new HashSet<>();
    private final Set<String> abandonedClaims = new HashSet<>();
    private final Set<String> abandonedTasks = new HashSet<>();
    private final Set<String> resourceBlockedTasks = new HashSet<>();
    private final Set<String> moveBlockedGuardNodes = new HashSet<>();
    private final Set<String> dispatchedSquadOrders = new HashSet<>();
    private final Map<String, Integer> processConflictCounts = new HashMap<>();
    private String lastClaimResourceKey;
    private String lastClaimTaskId;
    private int lastClaimTaskRound;
    private int processBackoffUntilRound;
    private MapGraph graph = new MapGraph();

    public YhChampionPlannerAgent(int playerId) {
        this(playerId, GrandmasterStrategyTables.loadDefault());
    }

    YhChampionPlannerAgent(int playerId, GrandmasterStrategyTables strategyTables) {
        this.playerId = playerId;
        this.strategyTables = strategyTables;
    }

    @Override
    public void onStart(JSONObject startData) {
        graph = MapGraph.fromStart(startData);
        locallyScoutedNodes.clear();
        processedStations.clear();
        gateBreakVerifyArmedMatches.clear();
        gateBreakVerifyUsedMatches.clear();
        abandonedClaims.clear();
        abandonedTasks.clear();
        resourceBlockedTasks.clear();
        moveBlockedGuardNodes.clear();
        dispatchedSquadOrders.clear();
        processConflictCounts.clear();
        lastClaimResourceKey = null;
        lastClaimTaskId = null;
        lastClaimTaskRound = 0;
        processBackoffUntilRound = 0;
        LOG.info("YH 收到 start，已初始化路线图。playerId={}, strategyVersion={}",
                playerId, strategyTables.version());
    }

    @Override
    public String onInquire(JSONObject inquireData) {
        graph.addEdges(inquireData.getJSONArray("edges"));
        graph.updateBlockedNodes(inquireData.getJSONArray("nodes"));
        markCompletedProcessStations(inquireData);
        updateProcessBackoff(inquireData);
        markResolvedGuardBlocks(inquireData);
        rememberRejectedTacticalActions(inquireData);
        String matchId = inquireData.getString("matchId");
        int round = inquireData.getIntValue("round");
        Optional<JSONObject> playerState = playerState(inquireData);
        List<RoleActionCommand> actions = playerState
                .map(player -> chooseActions(player, inquireData, round))
                .orElseGet(() -> {
                    LOG.warn("YH 未找到己方玩家状态，保守提交 WAIT。round={}, playerId={}", round, playerId);
                    return List.of(RoleActionCommand.waitAction());
                });
        return ProtocolMessages.action(matchId, playerId, round, actions);
    }

    private List<RoleActionCommand> chooseActions(JSONObject playerState, JSONObject inquireData, int round) {
        StrategyIntent intent = chooseIntent(playerState, inquireData, round);
        LOG.info("YH StrategyIntent round={}, intent={}, strategyVersion={}, intentBias={}",
                round, intent, strategyTables.version(), strategyTables.intentBias(intent));

        if (playerState.getBooleanValue("delivered")) {
            RoleActionCommand wait = decision(round, playerState, RoleActionCommand.waitAction(),
                    "已经交付，风险门控：不再提交窗口/小队等附加动作");
            return List.of(wait);
        }

        RoleActionCommand mainAction = chooseMainAction(playerState, inquireData, round);
        List<RoleActionCommand> actions = new ArrayList<>();
        actions.add(mainAction);

        chooseWindowCard(playerState, inquireData, round).ifPresent(actions::add);
        chooseSquadAction(playerState, inquireData, round, mainAction).ifPresent(actions::add);

        LOG.info("YH 回合动作汇总 round={}, main={}, extraActions={}, reason=主车队稳定推进，附加动作用于窗口和小队对抗",
                round, mainAction.getAction(), Math.max(0, actions.size() - 1));
        return actions;
    }

    private RoleActionCommand chooseMainAction(JSONObject playerState, JSONObject inquireData, int round) {
        MainActionContext context = new MainActionContext(
                playerState,
                inquireData,
                round,
                playerState.getString("state"),
                safeText(playerState.getString("currentNodeId")),
                safeText(playerState.getString("nextNodeId")),
                weatherPenalties(inquireData.getJSONObject("weather")),
                playerState.getBooleanValue("verified"));
        Optional<RoleActionCommand> stateAction = chooseStateAction(context);
        if (stateAction.isPresent()) {
            return stateAction.orElseThrow();
        }

        Optional<RoleActionCommand> stationAction = chooseStationAction(context);
        if (stationAction.isPresent()) {
            return stationAction.orElseThrow();
        }

        Optional<RoleActionCommand> gateOrTerminalAction = chooseGateOrTerminalAction(context);
        if (gateOrTerminalAction.isPresent()) {
            return gateOrTerminalAction.orElseThrow();
        }

        Optional<RoleActionCommand> tacticalAction = chooseImmediateTacticalAction(context);
        if (tacticalAction.isPresent()) {
            return tacticalAction.orElseThrow();
        }

        Optional<RoleActionCommand> localOpportunityAction = chooseLocalOpportunityAction(context);
        if (localOpportunityAction.isPresent()) {
            return localOpportunityAction.orElseThrow();
        }

        return chooseRouteAction(context);
    }

    private Optional<RoleActionCommand> chooseStateAction(MainActionContext context) {
        if (context.playerState().getBooleanValue("delivered")) {
            return Optional.of(decision(context.round(), context.playerState(), RoleActionCommand.waitAction(),
                    "已经交付，后续回合保持等待"));
        }

        if (STATE_MOVING.equals(context.state()) && hasText(context.nextNodeId())) {
            return chooseMovingAction(context);
        }
        if (STATE_WAITING.equals(context.state())) {
            if (hasText(context.nextNodeId())) {
                return Optional.of(decision(context.round(), context.playerState(),
                        RoleActionCommand.moveTo(context.nextNodeId()),
                        "WAITING 状态保留 nextNode，续发 MOVE 防止移动心跳丢失"));
            }
        }
        if (isBusy(context.state())) {
            return Optional.of(decision(context.round(), context.playerState(), RoleActionCommand.waitAction(),
                    "正在处理/争夺/休整中，等待服务端结算"));
        }

        if (context.currentNodeId().isBlank()) {
            return Optional.of(decision(context.round(), context.playerState(), RoleActionCommand.waitAction(),
                    "当前位置缺失，保守等待"));
        }
        return Optional.empty();
    }

    private Optional<RoleActionCommand> chooseMovingAction(MainActionContext context) {
        if (moveBlockedGuardNodes.contains(context.nextNodeId())) {
            Optional<RoleActionCommand> guardResponse = chooseGuardResponse(
                    context.playerState(),
                    context.inquireData(),
                    context.nextNodeId(),
                    context.round());
            if (guardResponse.isPresent()) {
                return Optional.of(decision(
                        context.round(),
                        context.playerState(),
                        guardResponse.orElseThrow(),
                        "移动中遭遇敌方设卡拦截，切换为强通/攻坚而不是继续重发 MOVE"));
            }
        }
        return Optional.of(decision(context.round(), context.playerState(), RoleActionCommand.moveTo(context.nextNodeId()),
                "移动中，继续沿服务端下发的 nextNodeId 前进"));
    }

    private Optional<RoleActionCommand> chooseStationAction(MainActionContext context) {
        if (!PROCESS_STATIONS.contains(context.currentNodeId())) {
            return Optional.empty();
        }

        Optional<RoleActionCommand> stationTask = claimCurrentTask(
                context.currentNodeId(),
                context.playerState(),
                context.inquireData());
        if (stationTask.isPresent()) {
            return Optional.of(decision(context.round(), context.playerState(), stationTask.orElseThrow(),
                    "当前强制站点存在活跃任务，先抢任务窗口再处理离站流程"));
        }

        if (processedStations.contains(context.currentNodeId())) {
            return Optional.empty();
        }
        if (shouldDeferEarlyStationProcessForTask(context)) {
            return Optional.empty();
        }
        if (context.round() < processBackoffUntilRound) {
            return Optional.of(decision(context.round(), context.playerState(), RoleActionCommand.waitAction(),
                    "PROCESS 同节点冲突后按 playerId 优先级让出处理窗口，避免双方锁步重试"));
        }
        return Optional.of(decision(context.round(), context.playerState(),
                RoleActionCommand.process(context.currentNodeId()),
                "到达强制处理站点，先提交 PROCESS，完成后再继续移动"));
    }

    private boolean shouldDeferEarlyStationProcessForTask(MainActionContext context) {
        if (!EARLY_OPTIONAL_PROCESS_STATIONS.contains(context.currentNodeId())
                || context.round() > 120
                || context.verified()
                || !shouldPressTaskScore(context.playerState(), context.inquireData(), context.round())) {
            return false;
        }
        return nextHopTowardUsefulTask(
                context.currentNodeId(),
                context.targetNodeId(),
                context.playerState(),
                context.inquireData(),
                context.weatherPenalties()).isPresent();
    }

    private Optional<RoleActionCommand> chooseGateOrTerminalAction(MainActionContext context) {
        if (context.verified() && TERMINAL_NODE_ID.equals(context.currentNodeId())) {
            return Optional.of(decision(context.round(), context.playerState(), RoleActionCommand.deliver(),
                    "已验核并到达终点，提交交付"));
        }
        if (!context.verified() && VERIFY_GATE_NODE_ID.equals(context.currentNodeId())) {
            Optional<RoleActionCommand> staggeredVerify = chooseStaggeredGateVerify(
                    context.playerState(),
                    context.inquireData(),
                    context.round());
            if (staggeredVerify.isPresent()) {
                return Optional.of(decision(context.round(), context.playerState(), staggeredVerify.orElseThrow(),
                        "Rush宫门错峰：避开同步窗口后绑定破令验核"));
            }
            return Optional.of(decision(context.round(), context.playerState(),
                    RoleActionCommand.verifyGate(VERIFY_GATE_NODE_ID), "已到朱雀门，提交宫门验核"));
        }
        if (!context.verified() && TERMINAL_NODE_ID.equals(context.currentNodeId())) {
            return Optional.of(graph.nextHopAllowingBlocked(
                            context.currentNodeId(),
                            VERIFY_GATE_NODE_ID,
                            context.weatherPenalties())
                    .map(next -> decision(context.round(), context.playerState(), RoleActionCommand.moveTo(next),
                            "未验核却位于终点安全区，风险门控：回到朱雀门"))
                    .orElseGet(() -> decision(context.round(), context.playerState(), RoleActionCommand.waitAction(),
                            "未验核却位于终点安全区，找不到回朱雀门路线，保守等待")));
        }
        return Optional.empty();
    }

    private Optional<RoleActionCommand> chooseImmediateTacticalAction(MainActionContext context) {
        Optional<RoleActionCommand> rushTactic = chooseRushTactic(
                context.playerState(),
                context.inquireData(),
                context.currentNodeId(),
                context.round());
        if (rushTactic.isPresent()) {
            return Optional.of(decision(context.round(), context.playerState(), rushTactic.orElseThrow(),
                    "YH Rush规划：交付窗口变窄，优先用急策压缩移动或保鲜成本"));
        }

        Optional<RoleActionCommand> guardAction = chooseSetGuard(
                context.playerState(),
                context.inquireData(),
                context.currentNodeId());
        if (guardAction.isPresent()) {
            return Optional.of(decision(context.round(), context.playerState(), guardAction.orElseThrow(),
                    "位于关键汇合点且资源足够，主动布防拖慢对手"));
        }
        return Optional.empty();
    }

    private Optional<RoleActionCommand> chooseLocalOpportunityAction(MainActionContext context) {
        Optional<RoleActionCommand> currentTask = claimCurrentTask(
                context.currentNodeId(),
                context.playerState(),
                context.inquireData());
        if (currentTask.isPresent()) {
            return Optional.of(decision(context.round(), context.playerState(), currentTask.orElseThrow(),
                    "当前位置存在低风险活跃任务，优先处理"));
        }

        Optional<RoleActionCommand> resourceUse = usefulResourceUse(
                context.playerState(),
                context.inquireData(),
                context.weatherPenalties());
        if (resourceUse.isPresent()) {
            return Optional.of(decision(context.round(), context.playerState(), resourceUse.orElseThrow(),
                    "当前库存存在可直接生效的资源"));
        }

        Optional<RoleActionCommand> currentResource = claimCurrentResource(
                context.currentNodeId(),
                context.playerState(),
                context.inquireData());
        if (currentResource.isPresent()) {
            return Optional.of(decision(context.round(), context.playerState(), currentResource.orElseThrow(),
                    "当前位置有高价值公开资源，优先领取"));
        }
        return Optional.empty();
    }

    private RoleActionCommand chooseRouteAction(MainActionContext context) {
        Optional<RoleActionCommand> antiGuard = chooseAntiGuard(
                context.playerState(),
                context.inquireData(),
                context.currentNodeId(),
                context.targetNodeId(),
                context.weatherPenalties(),
                context.round());
        if (antiGuard.isPresent()) {
            return decision(context.round(), context.playerState(), antiGuard.orElseThrow(),
                    "下一跳存在敌方关隘，优先破关或强行通过");
        }

        boolean taskScorePressure = shouldPressTaskScore(context.playerState(), context.inquireData(), context.round());
        if (taskScorePressure) {
            Optional<String> taskDetour = nextHopTowardUsefulTask(
                    context.currentNodeId(),
                    context.targetNodeId(),
                    context.playerState(),
                    context.inquireData(),
                    context.weatherPenalties());
            if (taskDetour.isPresent()) {
                return decision(context.round(), context.playerState(), RoleActionCommand.moveTo(taskDetour.orElseThrow()),
                        "任务基础分不足 90，放宽绕路预算优先补皇榜任务");
            }

        }

        Optional<String> resourceDetour = nextHopTowardUsefulResource(
                context.currentNodeId(),
                context.targetNodeId(),
                context.playerState(),
                context.inquireData(),
                context.weatherPenalties());
        if (resourceDetour.isPresent()) {
            return decision(context.round(), context.playerState(), RoleActionCommand.moveTo(resourceDetour.orElseThrow()),
                    "高价值资源绕路成本可接受，选择资源路线");
        }

        if (!taskScorePressure) {
            Optional<String> taskDetour = nextHopTowardUsefulTask(
                    context.currentNodeId(),
                    context.targetNodeId(),
                    context.playerState(),
                    context.inquireData(),
                    context.weatherPenalties());
            if (taskDetour.isPresent()) {
                return decision(context.round(), context.playerState(), RoleActionCommand.moveTo(taskDetour.orElseThrow()),
                        "高价值动态任务绕路成本可接受，选择任务路线");
            }
        }

        return moveOrClearToward(context.round(), context.playerState(), context.currentNodeId(),
                context.targetNodeId(), context.weatherPenalties(),
                "未发现低风险资源/任务机会，按交付目标移动", "找不到去目标点的可达路径，保守等待");
    }

    private Optional<RoleActionCommand> chooseStaggeredGateVerify(JSONObject playerState, JSONObject inquireData,
                                                                 int round) {
        if (strategyTables.threshold("staggerGateVerifyWithBreakOrder", 0) <= 0
                || !isRushPhase(inquireData, round) || !canBindBreakOrder(playerState)) {
            return Optional.empty();
        }
        String matchId = matchKey(inquireData);
        if (gateBreakVerifyArmedMatches.remove(matchId)) {
            gateBreakVerifyUsedMatches.add(matchId);
            LOG.info("YH 宫门错峰验核 round={}, tactic=BREAK_ORDER, reason=上一拍已避开同步争夺，本拍缩短验核读条",
                    round);
            return Optional.of(RoleActionCommand.verifyGate(VERIFY_GATE_NODE_ID).withRushTactic("BREAK_ORDER"));
        }
        if (gateBreakVerifyUsedMatches.contains(matchId)) {
            return Optional.empty();
        }
        if (opponentAlsoWaitingAtGate(inquireData)) {
            gateBreakVerifyArmedMatches.add(matchId);
            LOG.info("YH 宫门错峰等待 round={}, reason=对手同在朱雀门，等待一拍避免触发 GATE 同步争夺",
                    round);
            return Optional.of(RoleActionCommand.waitAction());
        }
        gateBreakVerifyUsedMatches.add(matchId);
        LOG.info("YH 宫门破令验核 round={}, tactic=BREAK_ORDER, reason=Rush阶段单独验核，缩短宫门读条",
                round);
        return Optional.of(RoleActionCommand.verifyGate(VERIFY_GATE_NODE_ID).withRushTactic("BREAK_ORDER"));
    }

    private boolean opponentAlsoWaitingAtGate(JSONObject inquireData) {
        JSONArray players = inquireData.getJSONArray("players");
        if (players == null) {
            return false;
        }
        for (int i = 0; i < players.size(); i++) {
            JSONObject player = players.getJSONObject(i);
            if (player == null || player.getIntValue("playerId") == playerId) {
                continue;
            }
            if (!player.getBooleanValue("verified")
                    && VERIFY_GATE_NODE_ID.equals(safeText(player.getString("currentNodeId")))
                    && !isBusy(player.getString("state"))) {
                return true;
            }
        }
        return false;
    }

    private StrategyIntent chooseIntent(JSONObject playerState, JSONObject inquireData, int round) {
        if (playerState.getBooleanValue("delivered")) {
            return StrategyIntent.LEAD_PROTECT;
        }
        if (isRushPhase(inquireData, round)) {
            return isLikelyBehind(playerState, inquireData) ? StrategyIntent.RUSH_COMEBACK : StrategyIntent.LEAD_PROTECT;
        }
        if (playerState.getDoubleValue("freshness") > 0.0D && playerState.getDoubleValue("freshness") < 78.0D) {
            return StrategyIntent.FRESH_DELIVERY;
        }
        if (hasHighValueTaskOpportunity(playerState, inquireData, round)) {
            return StrategyIntent.TASK_CAP_PUSH;
        }
        if (hasHighValueResourceOpportunity(inquireData)) {
            return StrategyIntent.RESOURCE_CONTROL;
        }
        String currentNodeId = safeText(playerState.getString("currentNodeId"));
        if (playerState.getIntValue("guardActionPoint") > 0 && playerState.getIntValue("goodFruit") >= 2
                && KEY_GUARD_NODES.contains(currentNodeId)
                && !opponentAlreadyBeyondGuardNode(playerState, inquireData, currentNodeId)) {
            return StrategyIntent.GUARD_PRESSURE;
        }
        return StrategyIntent.FAST_DELIVERY;
    }

    private Optional<RoleActionCommand> chooseSetGuard(JSONObject playerState, JSONObject inquireData,
                                                      String currentNodeId) {
        if (playerState.getBooleanValue("verified") || !KEY_GUARD_NODES.contains(currentNodeId)) {
            return Optional.empty();
        }
        if (playerState.getIntValue("guardActionPoint") <= 0 || playerState.getIntValue("goodFruit") < 2) {
            return Optional.empty();
        }
        JSONObject node = nodeById(inquireData.getJSONArray("nodes"), currentNodeId).orElse(null);
        if (hasActiveGuard(node)) {
            return Optional.empty();
        }
        // 设卡要挡在对手前面才有收益；对手已越过关键点时，保住主车队 tempo 比补一个空关更重要。
        if (opponentAlreadyBeyondGuardNode(playerState, inquireData, currentNodeId)) {
            return Optional.empty();
        }
        if (isRedundantS11GuardBehindOwnS10Guard(playerState, inquireData, currentNodeId)) {
            return Optional.empty();
        }
        int extraGoodFruit = Math.min(2, Math.max(1, playerState.getIntValue("goodFruit") - 1));
        return Optional.of(RoleActionCommand.setGuard(currentNodeId, extraGoodFruit));
    }

    private Optional<RoleActionCommand> chooseAntiGuard(JSONObject playerState, JSONObject inquireData,
                                                       String currentNodeId, String targetNodeId,
                                                       Set<String> weatherPenalties, int round) {
        Optional<String> nextHop = graph.nextHopAllowingBlocked(currentNodeId, targetNodeId, weatherPenalties);
        if (nextHop.isEmpty()) {
            return Optional.empty();
        }
        return chooseGuardResponse(playerState, inquireData, nextHop.orElseThrow(), round);
    }

    private Optional<RoleActionCommand> chooseGuardResponse(JSONObject playerState, JSONObject inquireData,
                                                           String guardedNodeId, int round) {
        JSONObject nextNode = nodeById(inquireData.getJSONArray("nodes"), guardedNodeId).orElse(null);
        boolean recentlyBlockedByGuard = moveBlockedGuardNodes.contains(guardedNodeId);
        if (!isEnemyGuard(nextNode, playerTeam(playerState)) && !recentlyBlockedByGuard) {
            return Optional.empty();
        }
        if (recentlyBlockedByGuard && !hasActiveGuard(nextNode)) {
            return Optional.of(RoleActionCommand.forcedPass(guardedNodeId));
        }
        int defense = guardDefense(nextNode);
        int goodSpend = playerState.getIntValue("goodFruit") > 0 ? 1 : 0;
        int badSpend = playerState.getIntValue("badFruit") > 0 && defense > goodSpend * 2 ? 1 : 0;
        while (goodSpend < 2 && goodSpend < playerState.getIntValue("goodFruit")
                && attackValue(goodSpend, badSpend) < defense) {
            goodSpend++;
        }
        while (badSpend < 2 && badSpend < playerState.getIntValue("badFruit")
                && attackValue(goodSpend, badSpend) < defense) {
            badSpend++;
        }
        if (attackValue(goodSpend, badSpend) >= defense && (goodSpend > 0 || badSpend > 0)) {
            RoleActionCommand action = RoleActionCommand.breakGuard(guardedNodeId, goodSpend, badSpend);
            if (isRushPhase(inquireData, round) && canBindBreakOrder(playerState)) {
                LOG.info("YH Rush破令绑定 round={}, target={}, reason=敌方关隘挡住主路线，绑定 BREAK_ORDER 提高破关确定性",
                        round, guardedNodeId);
                action = action.withRushTactic("BREAK_ORDER");
            }
            return Optional.of(action);
        }
        if (shouldChipGuardBeforeForcedPass(playerState, guardedNodeId, defense, goodSpend, badSpend, round)) {
            LOG.info("YH 终点前削防 round={}, target={}, defense={}, goodSpend={}, badSpend={}, reason=一次失败攻坚会触发短休整，但比高防强通税更划算",
                    round, guardedNodeId, defense, goodSpend, badSpend);
            return Optional.of(RoleActionCommand.breakGuard(guardedNodeId, goodSpend, badSpend));
        }
        if (round >= 120 || playerState.getDoubleValue("freshness") >= 78.0D) {
            return Optional.of(RoleActionCommand.forcedPass(guardedNodeId));
        }
        return Optional.empty();
    }

    private boolean shouldChipGuardBeforeForcedPass(JSONObject playerState, String guardedNodeId, int defense,
                                                    int goodSpend, int badSpend, int round) {
        int attackValue = attackValue(goodSpend, badSpend);
        if (!PALACE_STATION_NODE_ID.equals(guardedNodeId) || round < 300 || attackValue <= 0
                || attackValue >= defense) {
            return false;
        }
        int remainingDefense = defense - attackValue;
        // 服务端单次攻坚最多接收 2 个好果和 2 个坏果；这里判断一次削防后能否用下一次攻坚清掉。
        int followUpAttackBudget = Math.min(2, Math.max(0, playerState.getIntValue("goodFruit") - goodSpend)) * 2
                + Math.min(2, Math.max(0, playerState.getIntValue("badFruit") - badSpend)) * 3;
        return remainingDefense <= followUpAttackBudget;
    }

    private Optional<RoleActionCommand> chooseRushTactic(JSONObject playerState, JSONObject inquireData,
                                                        String currentNodeId, int round) {
        if (!isRushPhase(inquireData, round) || playerState.getIntValue("rushTacticUsedCount") > 0) {
            return Optional.empty();
        }
        if (playerState.getIntValue("goodFruit") <= 0) {
            return Optional.empty();
        }
        Set<String> weatherPenalties = weatherPenalties(inquireData.getJSONObject("weather"));
        OptionalInt terminalDistance = graph.distance(currentNodeId, TERMINAL_NODE_ID, weatherPenalties);
        boolean deliveryClockTight = round >= 180 || terminalDistance.orElse(0) >= 20;
        OptionalInt gateDistance = graph.distance(currentNodeId, VERIFY_GATE_NODE_ID, weatherPenalties);
        int preGateRushThreshold = strategyTables.threshold("preGateRushSpeedMinDistance", Integer.MAX_VALUE);
        if (!playerState.getBooleanValue("verified") && !VERIFY_GATE_NODE_ID.equals(currentNodeId)
                && gateDistance.isPresent() && gateDistance.getAsInt() >= preGateRushThreshold
                && !hasActiveMoveBuff(playerState)) {
            LOG.info("YH Rush规划 round={}, tactic=RUSH_SPEED, distanceToGate={}, reason=未验核且宫门距离偏长，提前提速抢 S14",
                    round, gateDistance.getAsInt());
            return Optional.of(RoleActionCommand.rushSpeed());
        }
        if (playerState.getBooleanValue("verified") && !TERMINAL_NODE_ID.equals(currentNodeId)
                && deliveryClockTight && !hasActiveMoveBuff(playerState)) {
            LOG.info("YH Rush规划 round={}, tactic=RUSH_SPEED, distanceToEnd={}, reason=已验核但交付距离偏长，优先提速",
                    round, terminalDistance.isPresent() ? terminalDistance.getAsInt() : -1);
            return Optional.of(RoleActionCommand.rushSpeed());
        }
        if (playerState.getDoubleValue("freshness") > 0.0D && playerState.getDoubleValue("freshness") < 70.0D) {
            LOG.info("YH Rush规划 round={}, tactic=RUSH_PROTECT, freshness={}, reason=新鲜度偏低，优先保鲜",
                    round, playerState.getDoubleValue("freshness"));
            return Optional.of(RoleActionCommand.rushProtect());
        }
        return Optional.empty();
    }

    private boolean canBindBreakOrder(JSONObject playerState) {
        if (playerState.getIntValue("rushTacticUsedCount") > 0) {
            return false;
        }
        return playerState.getIntValue("goodFruit") > 0 || playerState.getIntValue("badFruit") > 0;
    }

    private Optional<RoleActionCommand> chooseWindowCard(JSONObject playerState, JSONObject inquireData, int round) {
        JSONArray contests = activeContests(inquireData);
        if (contests == null) {
            return Optional.empty();
        }
        for (int i = 0; i < contests.size(); i++) {
            JSONObject contest = contests.getJSONObject(i);
            if (contest == null || contest.getBooleanValue("resolved") || !contestBelongsToPlayer(contest)) {
                continue;
            }
            int deadlineRound = contest.getIntValue("deadlineRound");
            if (deadlineRound > 0 && round > deadlineRound + 1) {
                continue;
            }
            String card = policyWindowCard(contest, playerState);
            if (!"ABSTAIN".equals(card)) {
                RoleActionCommand action = RoleActionCommand.windowCard(contest.getString("contestId"), card);
                if (shouldBindBreakOrderToWindow(contest, playerState, inquireData, round)) {
                    action = action.withRushTactic("BREAK_ORDER");
                    LOG.info("YH 窗口破令 round={}, contestId={}, card={}, reason=Rush宫门争夺绑定 BREAK_ORDER 抢窗口点",
                            round, contest.getString("contestId"), card);
                } else {
                    LOG.info("YH 窗口出牌 round={}, contestId={}, card={}, reason=根据资源/鲜度/关隘点选择最高可用卡",
                            round, contest.getString("contestId"), card);
                }
                return Optional.of(action);
            }
        }
        return Optional.empty();
    }

    private boolean shouldBindBreakOrderToWindow(JSONObject contest, JSONObject playerState,
                                                JSONObject inquireData, int round) {
        return strategyTables.shouldBindBreakOrderForContest(contest.getString("contestType"))
                && isRushPhase(inquireData, round)
                && canBindBreakOrder(playerState);
    }

    private String policyWindowCard(JSONObject contest, JSONObject playerState) {
        String policyCard = strategyTables.contestCard(contest.getString("contestType"), contest.getIntValue("roundIndex"));
        if (hasText(policyCard)) {
            if ("ABSTAIN".equals(policyCard)) {
                return "ABSTAIN";
            }
            if (canPlayWindowCard(policyCard, playerState)) {
                return policyCard;
            }
        }
        return bestWindowCard(playerState);
    }

    private Optional<RoleActionCommand> chooseSquadAction(JSONObject playerState, JSONObject inquireData, int round,
                                                         RoleActionCommand mainAction) {
        if (isRushPhase(inquireData, round) || playerState.getIntValue("squadAvailable") <= 0) {
            return Optional.empty();
        }
        String currentNodeId = safeText(playerState.getString("currentNodeId"));
        String targetNodeId = playerState.getBooleanValue("verified") ? TERMINAL_NODE_ID : VERIFY_GATE_NODE_ID;
        Set<String> weatherPenalties = weatherPenalties(inquireData.getJSONObject("weather"));

        Optional<String> obstacle = firstRouteObstacle(currentNodeId, targetNodeId, inquireData, weatherPenalties);
        if (obstacle.isPresent() && !"CLEAR".equals(mainAction.getAction())) {
            String obstacleNodeId = obstacle.orElseThrow();
            String key = squadOrderKey("SQUAD_CLEAR", obstacleNodeId);
            if (dispatchedSquadOrders.contains(key)) {
                return Optional.empty();
            }
            dispatchedSquadOrders.add(key);
            LOG.info("YH 小队派遣 round={}, action=SQUAD_CLEAR, target={}, reason=主车队继续推进，小队提前清理必经障碍",
                    round, obstacleNodeId);
            return Optional.of(RoleActionCommand.squadClear(obstacleNodeId));
        }

        if (playerState.getIntValue("squadAvailable") >= 2) {
            Optional<String> enemyGuard = enemyGuardOnRoute(currentNodeId, targetNodeId, inquireData, weatherPenalties,
                    playerTeam(playerState));
            if (enemyGuard.isPresent()) {
                String enemyGuardNodeId = enemyGuard.orElseThrow();
                if (!enemyGuardNodeId.equals(mainAction.getTargetNodeId())) {
                    String key = squadOrderKey("SQUAD_WEAKEN", enemyGuardNodeId);
                    if (dispatchedSquadOrders.contains(key)) {
                        return Optional.empty();
                    }
                    dispatchedSquadOrders.add(key);
                    LOG.info("YH 小队派遣 round={}, action=SQUAD_WEAKEN, target={}, reason=削弱敌方关隘，为后续破关降成本",
                            round, enemyGuardNodeId);
                    return Optional.of(RoleActionCommand.squadWeaken(enemyGuardNodeId));
                }
            }
        }

        Optional<String> scoutTarget = nextScoutTarget(currentNodeId, weatherPenalties, playerState);
        if (scoutTarget.isPresent()) {
            String scoutNodeId = scoutTarget.orElseThrow();
            locallyScoutedNodes.add(scoutNodeId);
            LOG.info("YH 小队派遣 round={}, action=SQUAD_SCOUT, target={}, reason=侦查关键节点的障碍/资源/关隘信息",
                    round, scoutNodeId);
            return Optional.of(RoleActionCommand.squadScout(scoutNodeId));
        }
        return Optional.empty();
    }

    private RoleActionCommand moveOrClearToward(int round, JSONObject playerState, String currentNodeId,
                                                String targetNodeId, Set<String> weatherPenalties,
                                                String moveReason, String waitReason) {
        Optional<String> nextHop = graph.nextHop(currentNodeId, targetNodeId, weatherPenalties);
        if (nextHop.isPresent()) {
            return decision(round, playerState, RoleActionCommand.moveTo(nextHop.orElseThrow()), moveReason);
        }

        // 默认地图会把 S11 等必经点设为障碍；没有可达路线时，先推进到障碍前并清障。
        return graph.nextHopAllowingBlocked(currentNodeId, targetNodeId, weatherPenalties)
                .map(next -> {
                    if (graph.isBlocked(next)) {
                        return decision(round, playerState, RoleActionCommand.clearObstacle(next),
                                "目标路线被障碍阻断，先清理相邻障碍点");
                    }
                    return decision(round, playerState, RoleActionCommand.moveTo(next),
                            moveReason + "，前方有障碍，先推进到清障点");
                })
                .orElseGet(() -> decision(round, playerState, RoleActionCommand.waitAction(), waitReason));
    }

    private Optional<RoleActionCommand> usefulResourceUse(JSONObject playerState, JSONObject inquireData,
                                                         Set<String> weatherPenalties) {
        JSONObject resources = playerState.getJSONObject("resources");
        double freshness = playerState.getDoubleValue("freshness");
        if (resourceCount(resources, RESOURCE_ICE_BOX) > 0 && shouldUseIceBoxBeforeMoving(playerState, freshness)) {
            return Optional.of(RoleActionCommand.useResource(RESOURCE_ICE_BOX));
        }
        if (resourceCount(resources, RESOURCE_FAST_HORSE) > 0 && !hasActiveMoveBuff(playerState)
                && !shouldReserveClaimTaskFuel(RESOURCE_FAST_HORSE, playerState, inquireData, weatherPenalties)) {
            return Optional.of(RoleActionCommand.useResource(RESOURCE_FAST_HORSE));
        }
        if (resourceCount(resources, RESOURCE_SHORT_HORSE) > 0 && !hasActiveMoveBuff(playerState)
                && !shouldReserveClaimTaskFuel(RESOURCE_SHORT_HORSE, playerState, inquireData, weatherPenalties)) {
            return Optional.of(RoleActionCommand.useResource(RESOURCE_SHORT_HORSE));
        }
        return Optional.empty();
    }

    private boolean shouldUseIceBoxBeforeMoving(JSONObject playerState, double freshness) {
        if (freshness <= 0.0D) {
            return false;
        }
        if (freshness <= ICE_BOX_FRESHNESS_THRESHOLD) {
            return true;
        }
        // ICE_BOX at S15 is ignored by the current server, but S14 after verify is safe.
        // Before the final leg, one extra tick is usually worth far less than 5-10 freshness score.
        return playerState.getBooleanValue("verified")
                && VERIFY_GATE_NODE_ID.equals(safeText(playerState.getString("currentNodeId")))
                && freshness <= FINAL_LEG_ICE_BOX_FRESHNESS_THRESHOLD;
    }

    private boolean shouldReserveClaimTaskFuel(String resourceType, JSONObject playerState, JSONObject inquireData,
                                               Set<String> weatherPenalties) {
        if (!CLAIM_TASK_FUEL_TYPES.contains(resourceType)) {
            return false;
        }
        JSONArray tasks = inquireData.getJSONArray("tasks");
        if (tasks == null) {
            return false;
        }
        String currentNodeId = safeText(playerState.getString("currentNodeId"));
        if (!hasText(currentNodeId)) {
            return false;
        }
        int round = inquireData.getIntValue("round");
        for (int i = 0; i < tasks.size(); i++) {
            JSONObject task = tasks.getJSONObject(i);
            if (!isReachableClaimTaskFuelTarget(task, currentNodeId, resourceType, round, playerState, inquireData,
                    weatherPenalties)) {
                continue;
            }
            // CLAIM_TASK 的真实资源门槛在部分服务端 inquire 里不展开；保留一份马类燃料能把 S09/T_004
            // 这类“先拿资源、后换分”的任务从 RESOURCE_NOT_ENOUGH 变成确定得分。
            LOG.info("YH 保留任务燃料 round={}, resourceType={}, taskId={}, taskNode={}, reason=可达 CLAIM_TASK 需要燃料换分",
                    round, resourceType, task.getString("taskId"), task.getString("nodeId"));
            return true;
        }
        return false;
    }

    private boolean isReachableClaimTaskFuelTarget(JSONObject task, String currentNodeId, String resourceType, int round,
                                                  JSONObject playerState, JSONObject inquireData,
                                                  Set<String> weatherPenalties) {
        if (task == null || !"CLAIM_TASK".equals(task.getString("processType"))
                || !task.getBooleanValue("active") || task.getBooleanValue("completed")
                || task.getBooleanValue("failed")) {
            return false;
        }
        String taskId = task.getString("taskId");
        if (hasText(taskId) && abandonedTasks.contains(taskId)) {
            return false;
        }
        String taskNodeId = task.getString("nodeId");
        if (!hasText(taskNodeId) || currentNodeId.equals(taskNodeId)) {
            return false;
        }
        if (!hasRequiredFreshness(task, playerState)) {
            return false;
        }
        if (!taskExplicitlyRequiresResource(task, resourceType)) {
            return false;
        }
        int score = task.getIntValue("score");
        if (score < 20) {
            return false;
        }
        OptionalInt toTask = graph.distance(currentNodeId, taskNodeId, weatherPenalties);
        if (toTask.isEmpty()) {
            return false;
        }
        if (opponentLikelyWinsRemoteNode(playerState, inquireData, taskNodeId, toTask.getAsInt(),
                weatherPenalties)) {
            return false;
        }
        int processRound = Math.max(1, task.getIntValue("processRound"));
        int expireRound = task.getIntValue("expireRound");
        return expireRound <= 0 || round + toTask.getAsInt() + processRound <= expireRound;
    }

    private boolean taskExplicitlyRequiresResource(JSONObject task, String resourceType) {
        JSONArray requiredResourceTypes = task == null ? null : task.getJSONArray("requiredResourceTypes");
        if (requiredResourceTypes == null || requiredResourceTypes.isEmpty()) {
            return false;
        }
        for (int i = 0; i < requiredResourceTypes.size(); i++) {
            if (resourceType.equals(requiredResourceTypes.getString(i))) {
                return true;
            }
        }
        return false;
    }

    private Optional<RoleActionCommand> claimCurrentResource(String currentNodeId, JSONObject playerState,
                                                            JSONObject inquireData) {
        return bestResourceAt(currentNodeId, inquireData.getJSONArray("nodes"))
                .filter(resource -> resourceValue(resource) >= MIN_RESOURCE_CLAIM_VALUE)
                .filter(resource -> shouldClaimCurrentResource(currentNodeId, resource, playerState, inquireData))
                .filter(resource -> !abandonedClaims.contains(resourceClaimKey(currentNodeId, resource)))
                .map(resource -> {
                    lastClaimResourceKey = resourceClaimKey(currentNodeId, resource);
                    return RoleActionCommand.claimResource(currentNodeId, resource);
                });
    }

    private boolean shouldClaimCurrentResource(String currentNodeId, String resourceType, JSONObject playerState,
                                               JSONObject inquireData) {
        int round = inquireData.getIntValue("round");
        if ("S13".equals(currentNodeId) && round >= 360 && isWindowFuelResource(resourceType)) {
            return false;
        }
        if (RESOURCE_ICE_BOX.equals(resourceType)
                && shouldLeaveS07IceBoxForMidgameTask(currentNodeId, playerState, inquireData, round)) {
            return false;
        }
        if (isWindowFuelResource(resourceType)) {
            return hasActionableContest(inquireData)
                    || hasKnownEnemyGuard(inquireData, playerTeam(playerState))
                    || !moveBlockedGuardNodes.isEmpty();
        }
        return true;
    }

    private boolean shouldLeaveS07IceBoxForMidgameTask(String currentNodeId, JSONObject playerState,
                                                       JSONObject inquireData, int round) {
        if (!"S07".equals(currentNodeId) || round < 100 || round > 260
                || playerState.getBooleanValue("verified") || rawTaskScore(playerState) >= TASK_MILESTONE_90) {
            return false;
        }
        JSONArray tasks = inquireData.getJSONArray("tasks");
        if (tasks == null) {
            return false;
        }
        for (int i = 0; i < tasks.size(); i++) {
            JSONObject task = tasks.getJSONObject(i);
            if (!isViableRemoteTask(task, currentNodeId, round, playerState)) {
                continue;
            }
            String taskId = task.getString("taskId");
            if (hasText(taskId) && abandonedTasks.contains(taskId)) {
                continue;
            }
            String taskNodeId = task.getString("nodeId");
            if (!"S09".equals(taskNodeId) && !"S10".equals(taskNodeId)) {
                continue;
            }
            if (task.getIntValue("score") < 30) {
                continue;
            }
            OptionalInt toTask = graph.distance(currentNodeId, taskNodeId, weatherPenalties(inquireData.getJSONObject("weather")));
            if (toTask.isEmpty()) {
                continue;
            }
            int processRound = Math.max(1, task.getIntValue("processRound"));
            int expireRound = task.getIntValue("expireRound");
            if (expireRound > 0 && round + toTask.getAsInt() + processRound > expireRound) {
                continue;
            }
            LOG.info("YH 中局任务窗口让路 round={}, resourceType={}, taskId={}, taskNode={}, reason=S07 冰盒收益低于 S09/S10 抢分窗口",
                    round, RESOURCE_ICE_BOX, taskId, taskNodeId);
            return true;
        }
        return false;
    }

    private boolean isWindowFuelResource(String resourceType) {
        return "PASS_TOKEN".equals(resourceType) || "OFFICIAL_PERMIT".equals(resourceType);
    }

    private Optional<RoleActionCommand> claimCurrentTask(String currentNodeId, JSONObject playerState,
                                                        JSONObject inquireData) {
        JSONArray tasks = inquireData.getJSONArray("tasks");
        if (tasks == null) {
            return Optional.empty();
        }
        int round = inquireData.getIntValue("round");
        for (int i = 0; i < tasks.size(); i++) {
            JSONObject task = tasks.getJSONObject(i);
            if (isLowRiskCurrentTask(task, currentNodeId, round, playerState)) {
                String taskId = task.getString("taskId");
                if (hasText(taskId) && !abandonedTasks.contains(taskId)) {
                    if (shouldSkipLateContestedPalaceTask(task, currentNodeId, round, playerState, inquireData)) {
                        continue;
                    }
                    if (resourceBlockedTasks.contains(taskId) && !hasClaimTaskFuel(playerState)) {
                        continue;
                    }
                    resourceBlockedTasks.remove(taskId);
                    lastClaimTaskId = taskId;
                    lastClaimTaskRound = round;
                    return Optional.of(RoleActionCommand.claimTask(taskId));
                }
            }
        }
        return Optional.empty();
    }

    private boolean shouldSkipLateContestedPalaceTask(JSONObject task, String currentNodeId, int round,
                                                      JSONObject playerState, JSONObject inquireData) {
        if (!PALACE_STATION_NODE_ID.equals(currentNodeId) || processedStations.contains(currentNodeId)) {
            return false;
        }
        if (task.getIntValue("score") > 15) {
            return false;
        }
        int expireRound = task.getIntValue("expireRound");
        int processRound = Math.max(1, task.getIntValue("processRound"));
        if (expireRound <= 0 || expireRound - round > processRound + 8) {
            return false;
        }
        OptionalInt readyOpponentTaskScore = readyOpponentTaskScoreAtNode(inquireData,
                playerState.getIntValue("playerId"), currentNodeId);
        if (readyOpponentTaskScore.isEmpty() || readyOpponentTaskScore.getAsInt() > 0) {
            return false;
        }
        // S13 后面只剩验核和终点。临期 15 分任务一旦触发三拍平局，损失的时间通常高过任务收益。
        LOG.info("YH 跳过终点前临期低分任务。round={}, taskId={}, score={}, expireRound={}, reason=对手同站可争夺，优先处理 S13 保交付节奏",
                round, task.getString("taskId"), task.getIntValue("score"), expireRound);
        return true;
    }

    private OptionalInt readyOpponentTaskScoreAtNode(JSONObject inquireData, int selfPlayerId, String currentNodeId) {
        JSONArray players = inquireData.getJSONArray("players");
        if (players == null) {
            return OptionalInt.empty();
        }
        for (int i = 0; i < players.size(); i++) {
            JSONObject player = players.getJSONObject(i);
            if (player == null || player.getIntValue("playerId") == selfPlayerId
                    || player.getIntValue("playerId") == playerId || player.getBooleanValue("delivered")) {
                continue;
            }
            String state = player.getString("state");
            if (isBusy(state) || !currentNodeId.equals(safeText(player.getString("currentNodeId")))) {
                continue;
            }
            if (canPlayWindowCard("BING_ZHENG", player)
                    || canPlayWindowCard("QIANG_XING", player)
                    || canPlayWindowCard("YAN_DIE", player)
                    || canPlayWindowCard("XIAN_GONG", player)) {
                return OptionalInt.of(Math.max(0, player.getIntValue("taskScore")));
            }
        }
        return OptionalInt.empty();
    }

    private Optional<String> nextHopTowardUsefulResource(String currentNodeId, String targetNodeId,
                                                        JSONObject playerState, JSONObject inquireData,
                                                        Set<String> weatherPenalties) {
        JSONArray nodes = inquireData.getJSONArray("nodes");
        if (nodes == null) {
            return Optional.empty();
        }
        OptionalInt direct = graph.distance(currentNodeId, targetNodeId, weatherPenalties);
        if (direct.isEmpty()) {
            return Optional.empty();
        }

        String bestNodeId = null;
        int bestValue = 0;
        int bestTotalCost = Integer.MAX_VALUE;
        for (int i = 0; i < nodes.size(); i++) {
            JSONObject node = nodes.getJSONObject(i);
            if (node == null) {
                continue;
            }
            String nodeId = node.getString("nodeId");
            if (!hasText(nodeId) || nodeId.equals(currentNodeId) || nodeId.equals(TERMINAL_NODE_ID)) {
                continue;
            }
            Optional<String> resource = bestResourceAt(nodeId, nodes);
            if (resource.isEmpty()) {
                continue;
            }
            String resourceType = resource.orElseThrow();
            if (abandonedClaims.contains(resourceClaimKey(nodeId, resourceType))) {
                continue;
            }
            if (!shouldClaimCurrentResource(nodeId, resourceType, playerState, inquireData)) {
                continue;
            }
            int value = resourceValue(resourceType);
            if (value < 70) {
                continue;
            }
            OptionalInt toResource = graph.distance(currentNodeId, nodeId, weatherPenalties);
            OptionalInt fromResource = graph.distance(nodeId, targetNodeId, weatherPenalties);
            if (toResource.isEmpty() || fromResource.isEmpty()) {
                continue;
            }
            if (opponentLikelyWinsRemoteNode(playerState, inquireData, nodeId, toResource.getAsInt(),
                    weatherPenalties)) {
                LOG.info("YH 跳过远端已失主动权资源。round={}, node={}, resourceType={}, reason=对手预计明显先到",
                        inquireData.getIntValue("round"), nodeId, resourceType);
                continue;
            }
            int totalCost = toResource.getAsInt() + fromResource.getAsInt();
            int allowedCost = direct.getAsInt() + detourBudget(value);
            if (totalCost > allowedCost) {
                continue;
            }
            if (value > bestValue || value == bestValue && totalCost < bestTotalCost) {
                bestNodeId = nodeId;
                bestValue = value;
                bestTotalCost = totalCost;
            }
        }
        if (!hasText(bestNodeId)) {
            return Optional.empty();
        }
        return graph.nextHop(currentNodeId, bestNodeId, weatherPenalties);
    }

    private Optional<String> nextHopTowardUsefulTask(String currentNodeId, String targetNodeId,
                                                    JSONObject playerState, JSONObject inquireData,
                                                    Set<String> weatherPenalties) {
        JSONArray tasks = inquireData.getJSONArray("tasks");
        if (tasks == null) {
            return Optional.empty();
        }
        OptionalInt direct = graph.distance(currentNodeId, targetNodeId, weatherPenalties);
        if (direct.isEmpty()) {
            return Optional.empty();
        }

        int round = inquireData.getIntValue("round");
        boolean taskScorePressure = shouldPressTaskScore(playerState, inquireData, round);
        Optional<String> openingS04Task = openingS04WaterContestTask(currentNodeId, tasks, round, playerState,
                inquireData, weatherPenalties);
        if (taskScorePressure && openingS04Task.isPresent()) {
            return openingS04Task;
        }
        Optional<String> openingS03Task = openingS03FoundationTask(currentNodeId, tasks, round, playerState,
                inquireData, weatherPenalties);
        if (taskScorePressure && openingS03Task.isPresent()) {
            return openingS03Task;
        }
        Optional<String> earlyS03Counter = earlyS03CounterTaskWhenWaterLeadLost(currentNodeId, tasks, round,
                playerState, inquireData, weatherPenalties);
        if (taskScorePressure && earlyS03Counter.isPresent()) {
            return earlyS03Counter;
        }
        Optional<String> earlyS07Breakthrough = earlyS07BreakthroughTask(currentNodeId, tasks, round,
                playerState, weatherPenalties);
        if (taskScorePressure && earlyS07Breakthrough.isPresent()) {
            return earlyS07Breakthrough;
        }
        String bestTaskNodeId = null;
        int bestScore = 0;
        int bestTotalCost = Integer.MAX_VALUE;
        for (int i = 0; i < tasks.size(); i++) {
            JSONObject task = tasks.getJSONObject(i);
            if (!isViableRemoteTask(task, currentNodeId, round, playerState)) {
                continue;
            }
            String taskId = task.getString("taskId");
            if (hasText(taskId) && abandonedTasks.contains(taskId)) {
                continue;
            }
            String taskNodeId = task.getString("nodeId");
            OptionalInt toTask = graph.distance(currentNodeId, taskNodeId, weatherPenalties);
            OptionalInt fromTask = graph.distance(taskNodeId, targetNodeId, weatherPenalties);
            if (toTask.isEmpty() || fromTask.isEmpty()) {
                continue;
            }
            if (toTask.getAsInt() > 0
                    && (opponentControlsRemoteTaskNode(playerState, inquireData, taskNodeId)
                    || opponentLikelyWinsRemoteNode(playerState, inquireData, taskNodeId, toTask.getAsInt(),
                    weatherPenalties))) {
                LOG.info("YH 跳过远端已失主动权任务。round={}, taskId={}, taskNode={}, reason=对手已在任务节点，转向交付或其他机会",
                        round, taskId, taskNodeId);
                continue;
            }

            int processRound = Math.max(1, task.getIntValue("processRound"));
            int totalCost = toTask.getAsInt() + processRound + fromTask.getAsInt();
            int expireRound = task.getIntValue("expireRound");
            if (expireRound > 0 && round + toTask.getAsInt() + processRound > expireRound) {
                continue;
            }
            int taskValue = taskValue(task, playerState, round, totalCost);
            int allowedCost = direct.getAsInt() + taskDetourBudget(taskValue, taskScorePressure);
            int minTaskValue = taskScorePressure ? 12 : 24;
            if (totalCost > allowedCost || taskValue < minTaskValue) {
                continue;
            }
            if (taskValue > bestScore || taskValue == bestScore && totalCost < bestTotalCost) {
                bestTaskNodeId = taskNodeId;
                bestScore = taskValue;
                bestTotalCost = totalCost;
            }
        }
        if (!hasText(bestTaskNodeId)) {
            return Optional.empty();
        }
        return graph.nextHop(currentNodeId, bestTaskNodeId, weatherPenalties);
    }

    private Optional<String> openingS04WaterContestTask(String currentNodeId, JSONArray tasks, int round,
                                                       JSONObject playerState, JSONObject inquireData,
                                                       Set<String> weatherPenalties) {
        if (!"S02".equals(currentNodeId) || round > 120 || rawTaskScore(playerState) >= 30
                || !graph.hasEdge("S02", "S04")) {
            return Optional.empty();
        }
        OptionalInt toS04 = graph.distance(currentNodeId, "S04", weatherPenalties);
        if (toS04.isEmpty() || opponentControlsRemoteTaskNode(playerState, inquireData, "S04")) {
            return Optional.empty();
        }
        for (int i = 0; i < tasks.size(); i++) {
            JSONObject task = tasks.getJSONObject(i);
            if (!isViableRemoteTask(task, currentNodeId, round, playerState)) {
                continue;
            }
            String taskId = task.getString("taskId");
            if (hasText(taskId) && abandonedTasks.contains(taskId)) {
                continue;
            }
            if (!"S04".equals(task.getString("nodeId")) || task.getIntValue("score") < 30) {
                continue;
            }
            if (!"WATER".equals(task.getString("routeBucket"))
                    && !"CLAIM_TASK".equals(task.getString("processType"))) {
                continue;
            }
            int processRound = Math.max(1, task.getIntValue("processRound"));
            int expireRound = task.getIntValue("expireRound");
            if (expireRound > 0 && round + toS04.getAsInt() + processRound > expireRound) {
                continue;
            }
            if (opponentCompletesTaskNodeBeforeArrival(playerState, inquireData, "S04", toS04.getAsInt(),
                    processRound, weatherPenalties)) {
                continue;
            }
            LOG.info("YH 开局水线争夺锚定 S04。round={}, taskId={}, reason=S04/T_002 仍可赶上处理窗口，优先切断对手水路线",
                    round, taskId);
            return graph.nextHop(currentNodeId, "S04", weatherPenalties);
        }
        return Optional.empty();
    }

    private Optional<String> openingS03FoundationTask(String currentNodeId, JSONArray tasks, int round,
                                                      JSONObject playerState, JSONObject inquireData,
                                                      Set<String> weatherPenalties) {
        if (!"S02".equals(currentNodeId) || round > 120 || rawTaskScore(playerState) >= 30
                || !graph.hasEdge("S02", "S03")) {
            return Optional.empty();
        }
        if (opponentControlsRemoteTaskNode(playerState, inquireData, "S03")) {
            return Optional.empty();
        }
        for (int i = 0; i < tasks.size(); i++) {
            JSONObject task = tasks.getJSONObject(i);
            if (!isViableRemoteTask(task, currentNodeId, round, playerState)) {
                continue;
            }
            String taskId = task.getString("taskId");
            if (hasText(taskId) && abandonedTasks.contains(taskId)) {
                continue;
            }
            if (!"S03".equals(task.getString("nodeId")) || task.getIntValue("score") < 20) {
                continue;
            }
            int processRound = Math.max(1, task.getIntValue("processRound"));
            int expireRound = task.getIntValue("expireRound");
            OptionalInt toS03 = graph.distance(currentNodeId, "S03", weatherPenalties);
            if (toS03.isEmpty() || expireRound > 0 && round + toS03.getAsInt() + processRound > expireRound) {
                continue;
            }
            LOG.info("YH 开局任务链锚定 S03。round={}, taskId={}, reason=S03 是皇榜基础分入口，普通总路程惩罚不适用于开局任务链",
                    round, taskId);
            return graph.nextHop(currentNodeId, "S03", weatherPenalties);
        }
        return Optional.empty();
    }

    private boolean opponentControlsRemoteTaskNode(JSONObject playerState, JSONObject inquireData, String taskNodeId) {
        JSONArray players = inquireData.getJSONArray("players");
        if (players == null || !hasText(taskNodeId)) {
            return false;
        }
        int selfPlayerId = playerState.getIntValue("playerId");
        for (int i = 0; i < players.size(); i++) {
            JSONObject player = players.getJSONObject(i);
            if (player == null || player.getIntValue("playerId") == selfPlayerId
                    || player.getIntValue("playerId") == playerId || player.getBooleanValue("delivered")) {
                continue;
            }
            if (!taskNodeId.equals(safeText(player.getString("currentNodeId")))) {
                continue;
            }
            String state = safeText(player.getString("state"));
            if (!STATE_MOVING.equals(state)) {
                return true;
            }
        }
        return false;
    }

    private boolean opponentLikelyWinsRemoteNode(JSONObject playerState, JSONObject inquireData, String nodeId,
                                                int selfArrivalRounds, Set<String> weatherPenalties) {
        if (!hasText(nodeId) || selfArrivalRounds <= 0) {
            return false;
        }
        JSONArray players = inquireData.getJSONArray("players");
        if (players == null) {
            return false;
        }
        int selfPlayerId = playerState.getIntValue("playerId");
        for (int i = 0; i < players.size(); i++) {
            JSONObject player = players.getJSONObject(i);
            if (player == null || player.getIntValue("playerId") == selfPlayerId
                    || player.getIntValue("playerId") == playerId || player.getBooleanValue("delivered")) {
                continue;
            }
            OptionalInt opponentArrival = estimatedOpponentArrivalRounds(player, nodeId, weatherPenalties);
            if (opponentArrival.isPresent()
                    && opponentArrival.getAsInt() + REMOTE_NODE_CLEAR_LEAD_ROUNDS <= selfArrivalRounds) {
                return true;
            }
        }
        return false;
    }

    private boolean opponentCompletesTaskNodeBeforeArrival(JSONObject playerState, JSONObject inquireData, String nodeId,
                                                          int selfArrivalRounds, int processRound,
                                                          Set<String> weatherPenalties) {
        JSONArray players = inquireData.getJSONArray("players");
        if (players == null) {
            return false;
        }
        int selfPlayerId = playerState.getIntValue("playerId");
        for (int i = 0; i < players.size(); i++) {
            JSONObject player = players.getJSONObject(i);
            if (player == null || player.getIntValue("playerId") == selfPlayerId
                    || player.getIntValue("playerId") == playerId || player.getBooleanValue("delivered")) {
                continue;
            }
            OptionalInt opponentArrival = estimatedOpponentArrivalRounds(player, nodeId, weatherPenalties);
            if (opponentArrival.isPresent()
                    && opponentArrival.getAsInt() + Math.max(1, processRound) <= selfArrivalRounds) {
                return true;
            }
        }
        return false;
    }

    private OptionalInt estimatedOpponentArrivalRounds(JSONObject player, String nodeId,
                                                       Set<String> weatherPenalties) {
        String currentNodeId = safeText(player.getString("currentNodeId"));
        String nextNodeId = safeText(player.getString("nextNodeId"));
        String state = safeText(player.getString("state"));
        if (nodeId.equals(currentNodeId)) {
            return OptionalInt.of(0);
        }
        if (!STATE_MOVING.equals(state)) {
            return OptionalInt.empty();
        }
        if (!hasText(nextNodeId)) {
            return OptionalInt.empty();
        }

        int remainingCurrentEdge = remainingCurrentEdgeRounds(player, currentNodeId, nextNodeId);
        if (nodeId.equals(nextNodeId)) {
            return OptionalInt.of(remainingCurrentEdge);
        }
        OptionalInt afterNext = graph.distance(nextNodeId, nodeId, weatherPenalties);
        if (afterNext.isEmpty()) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(remainingCurrentEdge + afterNext.getAsInt());
    }

    private int remainingCurrentEdgeRounds(JSONObject player, String currentNodeId, String nextNodeId) {
        if (player.containsKey("edgeTotalMs") && player.containsKey("edgeProgressMs")) {
            long totalMs = Math.max(0L, player.getLongValue("edgeTotalMs"));
            long progressMs = Math.max(0L, player.getLongValue("edgeProgressMs"));
            if (totalMs > 0L) {
                long remainingMs = Math.max(0L, totalMs - progressMs);
                return (int) ((remainingMs + 999L) / 1000L);
            }
        }
        return graph.distance(currentNodeId, nextNodeId, Set.of()).orElse(1);
    }

    private Optional<String> earlyS03CounterTaskWhenWaterLeadLost(String currentNodeId, JSONArray tasks, int round,
                                                                  JSONObject playerState, JSONObject inquireData,
                                                                  Set<String> weatherPenalties) {
        if (!"S02".equals(currentNodeId) || round > 110 || !graph.hasEdge("S02", "S03")) {
            return Optional.empty();
        }
        OptionalInt toS04 = graph.distance(currentNodeId, "S04", weatherPenalties);
        if (toS04.isEmpty() || !opponentLikelyWinsRemoteNode(playerState, inquireData, "S04", toS04.getAsInt(),
                weatherPenalties)) {
            return Optional.empty();
        }
        OptionalInt toS03 = graph.distance(currentNodeId, "S03", weatherPenalties);
        if (toS03.isEmpty()) {
            return Optional.empty();
        }
        for (int i = 0; i < tasks.size(); i++) {
            JSONObject task = tasks.getJSONObject(i);
            if (!isViableRemoteTask(task, currentNodeId, round, playerState)) {
                continue;
            }
            String taskId = task.getString("taskId");
            if (hasText(taskId) && abandonedTasks.contains(taskId)) {
                continue;
            }
            if (!"S03".equals(task.getString("nodeId")) || task.getIntValue("score") < 20) {
                continue;
            }
            int processRound = Math.max(1, task.getIntValue("processRound"));
            int expireRound = task.getIntValue("expireRound");
            if (expireRound > 0 && round + toS03.getAsInt() + processRound > expireRound) {
                continue;
            }
            LOG.info("YH S02 失去水路先手后切换 S03 任务线。round={}, taskId={}, reason=S04/S05 资源任务窗口已明显落后，改抢官道基础分",
                    round, taskId);
            return Optional.of("S03");
        }
        return Optional.empty();
    }

    private Optional<String> earlyS07BreakthroughTask(String currentNodeId, JSONArray tasks, int round,
                                                     JSONObject playerState, Set<String> weatherPenalties) {
        if (!"S02".equals(currentNodeId) || round > 110) {
            return Optional.empty();
        }
        for (int i = 0; i < tasks.size(); i++) {
            JSONObject task = tasks.getJSONObject(i);
            if (!isViableRemoteTask(task, currentNodeId, round, playerState)) {
                continue;
            }
            String taskId = task.getString("taskId");
            if (hasText(taskId) && abandonedTasks.contains(taskId)) {
                continue;
            }
            if (!"S07".equals(task.getString("nodeId")) || task.getIntValue("score") < 20) {
                continue;
            }
            OptionalInt toS07 = graph.distance(currentNodeId, "S07", weatherPenalties);
            if (toS07.isEmpty()) {
                continue;
            }
            int processRound = Math.max(1, task.getIntValue("processRound"));
            int expireRound = task.getIntValue("expireRound");
            if (expireRound > 0 && round + toS07.getAsInt() + processRound > expireRound) {
                continue;
            }
            // S07 同时覆盖早期任务、快马资源和官道/水路汇合；任务分不足时优先把它当中段突破点。
            if ("S02".equals(currentNodeId) && graph.hasEdge("S02", "S03")) {
                return Optional.of("S03");
            }
            return graph.nextHop(currentNodeId, "S07", weatherPenalties);
        }
        return Optional.empty();
    }

    private Optional<String> bestResourceAt(String nodeId, JSONArray nodes) {
        if (nodes == null) {
            return Optional.empty();
        }
        String bestResource = null;
        int bestValue = 0;
        for (int i = 0; i < nodes.size(); i++) {
            JSONObject node = nodes.getJSONObject(i);
            if (node == null || !nodeId.equals(node.getString("nodeId"))) {
                continue;
            }
            JSONObject stock = node.getJSONObject("resourceStock");
            if (stock == null || stock.isEmpty()) {
                return Optional.empty();
            }
            for (String resourceType : stock.keySet()) {
                if (stock.getIntValue(resourceType) <= 0) {
                    continue;
                }
                int value = resourceValue(resourceType);
                if (value > bestValue) {
                    bestResource = resourceType;
                    bestValue = value;
                }
            }
        }
        return hasText(bestResource) ? Optional.of(bestResource) : Optional.empty();
    }

    private boolean isViableRemoteTask(JSONObject task, String currentNodeId, int round, JSONObject playerState) {
        if (task == null || !hasText(task.getString("nodeId")) || currentNodeId.equals(task.getString("nodeId"))) {
            return false;
        }
        if (!task.getBooleanValue("active") || task.getBooleanValue("completed") || task.getBooleanValue("failed")) {
            return false;
        }
        if (strategyTables.taskProcessTypeBonus(task.getString("processType")) <= 0) {
            return false;
        }
        if (!hasRequiredFreshness(task, playerState)) {
            return false;
        }
        if (!hasRequiredResources(task, playerState)) {
            return false;
        }
        int expireRound = task.getIntValue("expireRound");
        return expireRound <= 0 || round + Math.max(1, task.getIntValue("processRound")) <= expireRound;
    }

    private boolean isLowRiskCurrentTask(JSONObject task, String currentNodeId, int round, JSONObject playerState) {
        if (task == null || !currentNodeId.equals(task.getString("nodeId"))) {
            return false;
        }
        if (!task.getBooleanValue("active") || task.getBooleanValue("completed") || task.getBooleanValue("failed")) {
            return false;
        }
        int expireRound = task.getIntValue("expireRound");
        int processRound = task.getIntValue("processRound");
        if (expireRound > 0 && round + Math.max(1, processRound) > expireRound) {
            return false;
        }
        String processType = task.getString("processType");
        if (strategyTables.taskProcessTypeBonus(processType) <= 0) {
            return false;
        }
        if (!hasRequiredFreshness(task, playerState)) {
            return false;
        }
        return true;
    }

    private boolean hasRequiredFreshness(JSONObject task, JSONObject playerState) {
        double requiredFreshness = task == null ? 0.0D : task.getDoubleValue("requiredFreshness");
        return requiredFreshness <= 0.0D || playerState.getDoubleValue("freshness") >= requiredFreshness;
    }

    private boolean hasRequiredResources(JSONObject task, JSONObject playerState) {
        JSONArray requiredResourceTypes = task == null ? null : task.getJSONArray("requiredResourceTypes");
        if (requiredResourceTypes == null || requiredResourceTypes.isEmpty()) {
            return true;
        }
        JSONObject resources = playerState.getJSONObject("resources");
        for (int i = 0; i < requiredResourceTypes.size(); i++) {
            String resourceType = requiredResourceTypes.getString(i);
            if (resourceCount(resources, resourceType) > 0) {
                return true;
            }
        }
        return false;
    }

    private int taskValue(JSONObject task, JSONObject playerState, int round, int totalCost) {
        int score = taskMarginalScore(task, playerState);
        int processRound = Math.max(1, task.getIntValue("processRound"));
        int expireRound = task.getIntValue("expireRound");
        int expireRisk = expireRound <= 0 ? 0 : Math.max(0, 18 - (expireRound - round - totalCost));
        return score
                + strategyTables.taskProcessTypeBonus(task.getString("processType"))
                - processRound * 2
                - Math.max(0, totalCost - 16)
                - expireRisk;
    }

    private int taskMarginalScore(JSONObject task, JSONObject playerState) {
        int rawBefore = rawTaskScore(playerState);
        int taskScore = Math.max(0, task.getIntValue("score"));
        int rawAfter = Math.min(TASK_SCORE_CAP, rawBefore + taskScore);
        return taskScoreWithMilestones(rawAfter) - taskScoreWithMilestones(rawBefore)
                + taskMilestoneUnlockValue(rawBefore, rawAfter);
    }

    private int rawTaskScore(JSONObject playerState) {
        if (playerState == null) {
            return 0;
        }
        if (playerState.containsKey("taskScore")) {
            return Math.max(0, playerState.getIntValue("taskScore"));
        }
        JSONObject scoreDetail = playerState.getJSONObject("scoreDetail");
        if (scoreDetail != null) {
            return Math.max(0, scoreDetail.getIntValue("tasks"));
        }
        return 0;
    }

    private int taskScoreWithMilestones(int rawScore) {
        int result = Math.max(0, rawScore);
        if (rawScore >= TASK_MILESTONE_60) {
            result += 15;
        }
        if (rawScore >= TASK_MILESTONE_90) {
            result += 20;
        }
        if (rawScore >= TASK_MILESTONE_110) {
            result += 15;
        }
        return Math.min(TASK_SCORE_CAP, result);
    }

    private int taskMilestoneUnlockValue(int rawBefore, int rawAfter) {
        int value = 0;
        if (rawBefore < TASK_MILESTONE_60 && rawAfter >= TASK_MILESTONE_60) {
            value += 35;
        }
        if (rawBefore < TASK_MILESTONE_90 && rawAfter >= TASK_MILESTONE_90) {
            value += 70;
        }
        if (rawBefore < TASK_MILESTONE_110 && rawAfter >= TASK_MILESTONE_110) {
            value += 45;
        }
        return value;
    }

    private int taskDetourBudget(int taskValue, boolean taskScorePressure) {
        if (taskScorePressure && taskValue >= 18) {
            return 14;
        }
        if (taskScorePressure && taskValue >= 12) {
            return 12;
        }
        if (taskValue >= 42) {
            return 10;
        }
        if (taskValue >= 32) {
            return 6;
        }
        if (taskValue >= 24) {
            return 3;
        }
        return 0;
    }

    private boolean hasHighValueTaskOpportunity(JSONObject playerState, JSONObject inquireData, int round) {
        JSONArray tasks = inquireData.getJSONArray("tasks");
        if (tasks == null) {
            return false;
        }
        for (int i = 0; i < tasks.size(); i++) {
            JSONObject task = tasks.getJSONObject(i);
            if (task == null || !task.getBooleanValue("active") || task.getBooleanValue("completed")
                    || task.getBooleanValue("failed")) {
                continue;
            }
            if (strategyTables.taskProcessTypeBonus(task.getString("processType")) <= 0) {
                continue;
            }
            if (!hasRequiredFreshness(task, playerState) || !hasRequiredResources(task, playerState)) {
                continue;
            }
            if (taskValue(task, playerState, round, Math.max(1, task.getIntValue("processRound"))) >= 24) {
                return true;
            }
        }
        return false;
    }

    private boolean shouldPressTaskScore(JSONObject playerState, JSONObject inquireData, int round) {
        return round < 360
                && !playerState.getBooleanValue("verified")
                && scoreOf(playerState) < strategyTables.threshold("taskScoreDeliveryDiscountThreshold", 90)
                && hasViableTaskOpportunity(playerState, inquireData, round);
    }

    private boolean hasViableTaskOpportunity(JSONObject playerState, JSONObject inquireData, int round) {
        JSONArray tasks = inquireData.getJSONArray("tasks");
        if (tasks == null) {
            return false;
        }
        String currentNodeId = safeText(playerState.getString("currentNodeId"));
        for (int i = 0; i < tasks.size(); i++) {
            JSONObject task = tasks.getJSONObject(i);
            if (task == null || !task.getBooleanValue("active") || task.getBooleanValue("completed")
                    || task.getBooleanValue("failed")) {
                continue;
            }
            String taskId = task.getString("taskId");
            if (hasText(taskId) && abandonedTasks.contains(taskId)) {
                continue;
            }
            if (strategyTables.taskProcessTypeBonus(task.getString("processType")) <= 0) {
                continue;
            }
            if (!hasRequiredFreshness(task, playerState) || !hasRequiredResources(task, playerState)) {
                continue;
            }
            String taskNodeId = task.getString("nodeId");
            if (!hasText(taskNodeId) || currentNodeId.equals(taskNodeId)) {
                return true;
            }
            int expireRound = task.getIntValue("expireRound");
            int processRound = Math.max(1, task.getIntValue("processRound"));
            if (expireRound <= 0 || round + processRound <= expireRound) {
                return true;
            }
        }
        return false;
    }

    private boolean hasHighValueResourceOpportunity(JSONObject inquireData) {
        JSONArray nodes = inquireData.getJSONArray("nodes");
        if (nodes == null) {
            return false;
        }
        for (int i = 0; i < nodes.size(); i++) {
            JSONObject node = nodes.getJSONObject(i);
            if (node == null) {
                continue;
            }
            JSONObject stock = node.getJSONObject("resourceStock");
            if (stock == null) {
                continue;
            }
            for (String resourceType : stock.keySet()) {
                if (stock.getIntValue(resourceType) > 0 && resourceValue(resourceType) >= 80) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isLikelyBehind(JSONObject playerState, JSONObject inquireData) {
        int ownScore = scoreOf(playerState);
        if (ownScore <= 0) {
            return false;
        }
        JSONArray players = inquireData.getJSONArray("players");
        if (players == null) {
            return false;
        }
        for (int i = 0; i < players.size(); i++) {
            JSONObject player = players.getJSONObject(i);
            if (player == null || player.getIntValue("playerId") == playerId) {
                continue;
            }
            if (scoreOf(player) >= ownScore + 30) {
                return true;
            }
        }
        return false;
    }

    private int scoreOf(JSONObject playerState) {
        return Math.max(playerState.getIntValue("score"),
                Math.max(playerState.getIntValue("totalScore"), playerState.getIntValue("finalScore")));
    }

    private Optional<String> firstRouteObstacle(String currentNodeId, String targetNodeId, JSONObject inquireData,
                                               Set<String> weatherPenalties) {
        JSONArray nodes = inquireData.getJSONArray("nodes");
        if (nodes == null) {
            return Optional.empty();
        }
        String bestNodeId = null;
        int bestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < nodes.size(); i++) {
            JSONObject node = nodes.getJSONObject(i);
            if (node == null || !node.getBooleanValue("hasObstacle")) {
                continue;
            }
            String nodeId = node.getString("nodeId");
            if (!hasText(nodeId) || nodeId.equals(currentNodeId)) {
                continue;
            }
            OptionalInt toObstacle = graph.distance(currentNodeId, nodeId, weatherPenalties);
            OptionalInt fromObstacle = graph.distance(nodeId, targetNodeId, weatherPenalties);
            if (toObstacle.isEmpty() || fromObstacle.isEmpty()) {
                continue;
            }
            if (toObstacle.getAsInt() < bestDistance) {
                bestDistance = toObstacle.getAsInt();
                bestNodeId = nodeId;
            }
        }
        return hasText(bestNodeId) ? Optional.of(bestNodeId) : Optional.empty();
    }

    private Optional<String> enemyGuardOnRoute(String currentNodeId, String targetNodeId, JSONObject inquireData,
                                             Set<String> weatherPenalties, String playerTeam) {
        JSONArray nodes = inquireData.getJSONArray("nodes");
        if (nodes == null) {
            return Optional.empty();
        }
        String bestNodeId = null;
        int bestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < nodes.size(); i++) {
            JSONObject node = nodes.getJSONObject(i);
            if (node == null || !isEnemyGuard(node, playerTeam)) {
                continue;
            }
            String nodeId = node.getString("nodeId");
            OptionalInt toGuard = graph.distance(currentNodeId, nodeId, weatherPenalties);
            OptionalInt fromGuard = graph.distance(nodeId, targetNodeId, weatherPenalties);
            if (toGuard.isEmpty() || fromGuard.isEmpty()) {
                continue;
            }
            if (toGuard.getAsInt() < bestDistance) {
                bestDistance = toGuard.getAsInt();
                bestNodeId = nodeId;
            }
        }
        return hasText(bestNodeId) ? Optional.of(bestNodeId) : Optional.empty();
    }

    private Optional<String> nextScoutTarget(String currentNodeId, Set<String> weatherPenalties,
                                             JSONObject playerState) {
        JSONArray scouted = playerState.getJSONArray("scoutedNodeIds");
        for (String nodeId : SCOUT_PRIORITY_NODES) {
            if (nodeId.equals(currentNodeId) || locallyScoutedNodes.contains(nodeId)
                    || arrayContains(scouted, nodeId)) {
                continue;
            }
            if (graph.distance(currentNodeId, nodeId, weatherPenalties).isPresent()) {
                return Optional.of(nodeId);
            }
        }
        return Optional.empty();
    }

    private Set<String> weatherPenalties(JSONObject weather) {
        Set<String> penalties = new HashSet<>();
        addWeatherPenalties(weather == null ? null : weather.getJSONArray("active"), penalties);
        addWeatherPenalties(weather == null ? null : weather.getJSONArray("forecast"), penalties);
        if (!penalties.isEmpty()) {
            LOG.info("YH 天气路线惩罚生效，penalizedRouteTypes={}", penalties);
        }
        return penalties;
    }

    private void addWeatherPenalties(JSONArray weatherEvents, Set<String> penalties) {
        if (weatherEvents == null) {
            return;
        }
        for (int i = 0; i < weatherEvents.size(); i++) {
            JSONObject weather = weatherEvents.getJSONObject(i);
            if (weather == null) {
                continue;
            }
            String type = weather.getString("type");
            String region = weather.getString("region");
            if ("HEAVY_RAIN".equals(type) || "WATER".equals(region)) {
                penalties.add("WATER");
            }
            if ("MOUNTAIN_FOG".equals(type) || "MOUNTAIN".equals(region)) {
                penalties.add("MOUNTAIN");
            }
        }
    }

    private String bestWindowCard(JSONObject playerState) {
        if (canPlayWindowCard("YAN_DIE", playerState)) {
            return "YAN_DIE";
        }
        if (canPlayWindowCard("QIANG_XING", playerState)) {
            return "QIANG_XING";
        }
        if (canPlayWindowCard("XIAN_GONG", playerState)) {
            return "XIAN_GONG";
        }
        if (canPlayWindowCard("BING_ZHENG", playerState)) {
            return "BING_ZHENG";
        }
        return "ABSTAIN";
    }

    private boolean canPlayWindowCard(String card, JSONObject playerState) {
        JSONObject resources = playerState.getJSONObject("resources");
        if ("BING_ZHENG".equals(card)) {
            return playerState.getIntValue("guardActionPoint") > 0;
        }
        if ("QIANG_XING".equals(card)) {
            return resourceCount(resources, RESOURCE_FAST_HORSE) > 0
                    || resourceCount(resources, RESOURCE_SHORT_HORSE) > 0
                    || hasActiveMoveBuff(playerState);
        }
        if ("YAN_DIE".equals(card)) {
            return resourceCount(resources, RESOURCE_PASS_TOKEN) > 0
                    || resourceCount(resources, RESOURCE_OFFICIAL_PERMIT) > 0;
        }
        if ("XIAN_GONG".equals(card)) {
            return playerState.getDoubleValue("freshness") >= 80.0D && playerState.getIntValue("goodFruit") > 0;
        }
        return false;
    }

    private JSONArray activeContests(JSONObject inquireData) {
        JSONArray contests = inquireData.getJSONArray("activeContests");
        if (contests != null) {
            return contests;
        }
        return inquireData.getJSONArray("contests");
    }

    private boolean contestBelongsToPlayer(JSONObject contest) {
        return contest.getIntValue("redPlayerId") == playerId
                || contest.getIntValue("bluePlayerId") == playerId
                || contest.getIntValue("playerId") == playerId;
    }

    private Optional<JSONObject> nodeById(JSONArray nodes, String nodeId) {
        if (nodes == null || !hasText(nodeId)) {
            return Optional.empty();
        }
        for (int i = 0; i < nodes.size(); i++) {
            JSONObject node = nodes.getJSONObject(i);
            if (node != null && nodeId.equals(node.getString("nodeId"))) {
                return Optional.of(node);
            }
        }
        return Optional.empty();
    }

    private boolean hasActiveGuard(JSONObject node) {
        JSONObject guard = node == null ? null : node.getJSONObject("guard");
        return guard != null && (guard.getBooleanValue("active") || guard.getIntValue("defense") > 0);
    }

    private boolean isEnemyGuard(JSONObject node, String playerTeam) {
        if (!hasActiveGuard(node)) {
            return false;
        }
        JSONObject guard = node.getJSONObject("guard");
        String owner = firstText(guard, "ownerTeamId", "teamId", "camp", "ownerCamp");
        return hasText(owner) && hasText(playerTeam) && !owner.equalsIgnoreCase(playerTeam);
    }

    private boolean hasActionableContest(JSONObject inquireData) {
        JSONArray contests = activeContests(inquireData);
        if (contests == null) {
            return false;
        }
        for (int i = 0; i < contests.size(); i++) {
            JSONObject contest = contests.getJSONObject(i);
            if (contest != null && !contest.getBooleanValue("resolved") && contestBelongsToPlayer(contest)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasKnownEnemyGuard(JSONObject inquireData, String playerTeam) {
        JSONArray nodes = inquireData.getJSONArray("nodes");
        if (nodes == null) {
            return false;
        }
        for (int i = 0; i < nodes.size(); i++) {
            JSONObject node = nodes.getJSONObject(i);
            if (isEnemyGuard(node, playerTeam)) {
                return true;
            }
        }
        return false;
    }

    private boolean opponentAlreadyBeyondGuardNode(JSONObject playerState, JSONObject inquireData,
                                                   String guardNodeId) {
        Set<String> downstreamNodes = DOWNSTREAM_NODES_AFTER_GUARD.get(guardNodeId);
        JSONArray players = inquireData.getJSONArray("players");
        if (downstreamNodes == null || downstreamNodes.isEmpty() || players == null) {
            return false;
        }
        int selfPlayerId = playerState.getIntValue("playerId");
        for (int i = 0; i < players.size(); i++) {
            JSONObject player = players.getJSONObject(i);
            if (player == null || player.getIntValue("playerId") == selfPlayerId
                    || player.getIntValue("playerId") == playerId) {
                continue;
            }
            if (player.getBooleanValue("delivered") || player.getBooleanValue("verified")) {
                return true;
            }
            String currentNodeId = safeText(player.getString("currentNodeId"));
            String nextNodeId = safeText(player.getString("nextNodeId"));
            if (downstreamNodes.contains(currentNodeId) || downstreamNodes.contains(nextNodeId)) {
                return true;
            }
        }
        return false;
    }

    private boolean isRedundantS11GuardBehindOwnS10Guard(JSONObject playerState, JSONObject inquireData,
                                                        String currentNodeId) {
        if (!"S11".equals(currentNodeId) || opponentAlreadyBeyondGuardNode(playerState, inquireData, "S10")) {
            return false;
        }
        JSONObject upstreamNode = nodeById(inquireData.getJSONArray("nodes"), "S10").orElse(null);
        if (!hasActiveGuard(upstreamNode)) {
            return false;
        }
        JSONObject guard = upstreamNode.getJSONObject("guard");
        String owner = firstText(guard, "ownerTeamId", "teamId", "camp", "ownerCamp");
        String selfTeam = playerTeam(playerState);
        return hasText(owner) && hasText(selfTeam) && owner.equalsIgnoreCase(selfTeam);
    }

    private int guardDefense(JSONObject node) {
        JSONObject guard = node == null ? null : node.getJSONObject("guard");
        return guard == null ? 0 : Math.max(0, guard.getIntValue("defense"));
    }

    private int attackValue(int goodFruit, int badFruit) {
        return goodFruit * 2 + badFruit * 3;
    }

    private int detourBudget(int resourceValue) {
        if (resourceValue >= 90) {
            return 6;
        }
        if (resourceValue >= 70) {
            return 4;
        }
        return 0;
    }

    private int resourceValue(String resourceType) {
        if (RESOURCE_FAST_HORSE.equals(resourceType)) {
            return 100;
        }
        if (RESOURCE_ICE_BOX.equals(resourceType)) {
            return 90;
        }
        if (RESOURCE_SHORT_HORSE.equals(resourceType)) {
            return 80;
        }
        if (RESOURCE_PASS_TOKEN.equals(resourceType) || RESOURCE_OFFICIAL_PERMIT.equals(resourceType)) {
            return 72;
        }
        if ("INTEL".equals(resourceType)) {
            return 20;
        }
        if ("BOAT_RIGHT".equals(resourceType)) {
            return 10;
        }
        return 0;
    }

    private int resourceCount(JSONObject resources, String resourceType) {
        return resources == null ? 0 : resources.getIntValue(resourceType);
    }

    private boolean hasActiveMoveBuff(JSONObject playerState) {
        JSONArray buffs = playerState.getJSONArray("buffs");
        if (buffs == null) {
            return false;
        }
        for (int i = 0; i < buffs.size(); i++) {
            JSONObject buff = buffs.getJSONObject(i);
            String type = buff == null ? "" : firstText(buff, "resourceType", "type");
            String effect = buff == null ? "" : buff.getString("effectType");
            if (RESOURCE_FAST_HORSE.equals(type)
                    || RESOURCE_SHORT_HORSE.equals(type)
                    || EFFECT_MOVE_MULTIPLIER.equals(effect)
                    || "RUSH_SPEED".equals(type)) {
                return true;
            }
        }
        return false;
    }

    private Optional<JSONObject> playerState(JSONObject inquireData) {
        JSONArray players = inquireData.getJSONArray("players");
        if (players == null) {
            return Optional.empty();
        }
        for (int i = 0; i < players.size(); i++) {
            JSONObject player = players.getJSONObject(i);
            if (player != null && player.getIntValue("playerId") == playerId) {
                return Optional.of(player);
            }
        }
        return Optional.empty();
    }

    private void markCompletedProcessStations(JSONObject inquireData) {
        markCompletedProcessStations(inquireData.getJSONArray("events"));
        markCompletedProcessStations(inquireData.getJSONArray("messages"));
    }

    private void updateProcessBackoff(JSONObject inquireData) {
        int round = inquireData.getIntValue("round");
        updateProcessBackoff(inquireData.getJSONArray("actionResults"), round, inquireData);
        updateProcessBackoff(inquireData.getJSONArray("events"), round, inquireData);
        updateProcessBackoff(inquireData.getJSONArray("messages"), round, inquireData);
    }

    private void updateProcessBackoff(JSONArray entries, int round, JSONObject inquireData) {
        if (entries == null) {
            return;
        }
        for (int i = 0; i < entries.size(); i++) {
            JSONObject entry = entries.getJSONObject(i);
            JSONObject payload = payloadOrSelf(entry);
            if (payload == null || payload.getIntValue("playerId") != playerId
                    || !"OBJECT_BUSY".equals(payload.getString("errorCode"))) {
                continue;
            }
            String targetNodeId = payload.getString("targetNodeId");
            if (isProcessBusyPayload(payload)) {
                String processNodeId = processNodeId(inquireData, targetNodeId);
                // PROCESS 是单对象读条；同节点同拍冲突时用 playerId 做稳定优先级，避免双方永远同拍申请。
                if (shouldYieldProcessPriority(inquireData, processNodeId)) {
                    boolean roundConflict = "ROUND_CONFLICT".equals(payload.getString("ownerProcessType"));
                    int conflictCount = roundConflict
                            ? processConflictCounts.merge(processNodeId, 1, Integer::sum)
                            : 1;
                    int waitRounds = processYieldRounds(inquireData, payload, processNodeId,
                            conflictCount, roundConflict);
                    processBackoffUntilRound = Math.max(processBackoffUntilRound, round + waitRounds);
                } else {
                    processBackoffUntilRound = 0;
                    if (hasText(processNodeId)) {
                        processConflictCounts.remove(processNodeId);
                    }
                }
            }
        }
    }

    private boolean isProcessBusyPayload(JSONObject payload) {
        String action = payload.getString("action");
        String objectKey = payload.getString("objectKey");
        if ("PROCESS".equals(action) || (objectKey != null && objectKey.startsWith("PROCESS:"))) {
            return true;
        }
        // 任务、资源、宫门等对象也可能落在 PROCESS_STATIONS 上；只看 targetNodeId 会把 TASK:T_007
        // 这类拒绝误判成站点读条冲突，导致主车队无意义等待。
        return isStationProcessType(payload.getString("ownerProcessType"));
    }

    private boolean isStationProcessType(String processType) {
        return "TRANSFER".equals(processType)
                || "BOARD".equals(processType)
                || "WATER_TRANSFER".equals(processType)
                || "PASS_TRANSFER".equals(processType)
                || "PALACE_TRANSFER".equals(processType);
    }

    private void markResolvedGuardBlocks(JSONObject inquireData) {
        markResolvedGuardBlocks(inquireData.getJSONArray("events"));
        markResolvedGuardBlocks(inquireData.getJSONArray("messages"));
        markResolvedGuardBlocks(inquireData.getJSONArray("actionResults"));
    }

    private void markResolvedGuardBlocks(JSONArray entries) {
        if (entries == null) {
            return;
        }
        for (int i = 0; i < entries.size(); i++) {
            JSONObject entry = entries.getJSONObject(i);
            if (entry == null || !"GUARD_BREAK".equals(entry.getString("type"))) {
                continue;
            }
            JSONObject payload = payloadOrSelf(entry);
            if (payload == null || payload.getIntValue("playerId") != playerId
                    || !"SUCCESS".equals(payload.getString("result"))) {
                continue;
            }
            String targetNodeId = payload.getString("targetNodeId");
            if (hasText(targetNodeId) && moveBlockedGuardNodes.remove(targetNodeId)) {
                LOG.info("YH 清除敌方设卡阻挡记忆。blockedNode={}, reason=GUARD_BREAK_SUCCESS", targetNodeId);
            }
        }
    }

    private void rememberRejectedTacticalActions(JSONObject inquireData) {
        rememberRejectedTacticalActions(inquireData.getJSONArray("actionResults"), inquireData);
        rememberRejectedTacticalActions(inquireData.getJSONArray("events"), inquireData);
        rememberRejectedTacticalActions(inquireData.getJSONArray("messages"), inquireData);
    }

    private void rememberRejectedTacticalActions(JSONArray entries, JSONObject inquireData) {
        if (entries == null) {
            return;
        }
        for (int i = 0; i < entries.size(); i++) {
            JSONObject entry = entries.getJSONObject(i);
            JSONObject payload = payloadOrSelf(entry);
            if (payload == null || payload.getIntValue("playerId") != playerId
                    || payload.getBooleanValue("accepted")) {
                continue;
            }
            String errorCode = payload.getString("errorCode");
            if (!hasText(errorCode)) {
                continue;
            }
            String action = payload.getString("action");
            String targetNodeId = payload.getString("targetNodeId");
            if (isRejectedGateVerifyBusy(errorCode, action, payload)) {
                String matchId = matchKey(inquireData);
                // OBJECT_BUSY 只说明宫门读条没抢到，服务端不会消耗急策；本地也要释放，下一拍继续绑破令。
                if (gateBreakVerifyUsedMatches.remove(matchId)) {
                    LOG.info("YH 宫门破令验核被占用拒绝，释放本地急策标记。matchId={}, target={}",
                            matchId, targetNodeId);
                }
            }
            if ("MOVE_BLOCKED_BY_GUARD".equals(errorCode)) {
                String blockedNodeId = hasText(targetNodeId) ? targetNodeId : currentNextNodeId(inquireData);
                if (hasText(blockedNodeId)) {
                    moveBlockedGuardNodes.add(blockedNodeId);
                    LOG.info("YH 记忆敌方设卡阻挡。blockedNode={}, sourceTarget={}",
                            blockedNodeId, hasText(targetNodeId) ? "event" : "player.nextNodeId");
                }
            }
            if ("CLAIM_RESOURCE".equals(action)) {
                String key = resourceClaimKey(targetNodeId, payload.getString("resourceType"));
                if (!hasText(key)) {
                    key = lastClaimResourceKey;
                }
                if (hasText(key)) {
                    abandonedClaims.add(key);
                    LOG.info("YH 放弃被拒资源领取。key={}, errorCode={}", key, errorCode);
                }
            }
            boolean taskClaimRejected = isTaskClaimRejection(action, payload)
                    || isAmbiguousRecentTaskResourceRejection(action, payload, errorCode,
                    inquireData.getIntValue("round"));
            if (taskClaimRejected) {
                String taskId = payload.getString("taskId");
                if (!hasText(taskId)) {
                    taskId = taskIdFromObjectKey(payload.getString("objectKey"));
                }
                if (!hasText(taskId)) {
                    taskId = lastClaimTaskId;
                }
                if (hasText(taskId)) {
                    if (ERROR_RESOURCE_NOT_ENOUGH.equals(errorCode)
                            && isRecoverableClaimTaskResourceShortage(taskId, inquireData)) {
                        // CLAIM_TASK 的资源要求在部分服务端事件里不会带具体字段；本地能补燃料时不要把任务永久拉黑。
                        resourceBlockedTasks.add(taskId);
                        lastClaimTaskId = null;
                        lastClaimTaskRound = 0;
                        LOG.info("YH 暂缓资源门槛皇榜任务，先补本地燃料。taskId={}, errorCode={}",
                                taskId, errorCode);
                        continue;
                    }
                    abandonedTasks.add(taskId);
                    resourceBlockedTasks.remove(taskId);
                    lastClaimTaskId = null;
                    lastClaimTaskRound = 0;
                    LOG.info("YH 放弃被拒皇榜任务。taskId={}, errorCode={}", taskId, errorCode);
                }
            }
        }
    }

    private boolean isRejectedGateVerifyBusy(String errorCode, String action, JSONObject payload) {
        if (!"OBJECT_BUSY".equals(errorCode)) {
            return false;
        }
        String objectKey = payload.getString("objectKey");
        String targetNodeId = payload.getString("targetNodeId");
        return "VERIFY_GATE".equals(action)
                || (objectKey != null && objectKey.startsWith("GATE:"))
                || VERIFY_GATE_NODE_ID.equals(safeText(targetNodeId));
    }

    private boolean isAmbiguousRecentTaskResourceRejection(String action, JSONObject payload, String errorCode,
                                                          int round) {
        if (!ERROR_RESOURCE_NOT_ENOUGH.equals(errorCode) || !hasText(lastClaimTaskId)) {
            return false;
        }
        if (hasText(action) || hasText(payload.getString("taskId")) || hasText(payload.getString("objectKey"))) {
            return false;
        }
        // 服务端部分拒绝事件只给 RESOURCE_NOT_ENOUGH，不带 taskId/action；只把紧邻 CLAIM_TASK 的拒绝归因到任务。
        return lastClaimTaskRound > 0 && round >= lastClaimTaskRound && round - lastClaimTaskRound <= 3;
    }

    private boolean isRecoverableClaimTaskResourceShortage(String taskId, JSONObject inquireData) {
        Optional<JSONObject> maybePlayer = playerState(inquireData);
        Optional<JSONObject> maybeTask = taskById(inquireData.getJSONArray("tasks"), taskId);
        if (maybePlayer.isEmpty() || maybeTask.isEmpty()) {
            return false;
        }
        JSONObject player = maybePlayer.orElseThrow();
        JSONObject task = maybeTask.orElseThrow();
        if (!"CLAIM_TASK".equals(task.getString("processType"))) {
            return false;
        }
        String currentNodeId = safeText(player.getString("currentNodeId"));
        if (!currentNodeId.equals(task.getString("nodeId"))) {
            return false;
        }
        return hasClaimTaskFuel(player)
                || nodeById(inquireData.getJSONArray("nodes"), currentNodeId).map(this::hasClaimTaskFuelStock)
                .orElse(false);
    }

    private Optional<JSONObject> taskById(JSONArray tasks, String taskId) {
        if (tasks == null || !hasText(taskId)) {
            return Optional.empty();
        }
        for (int i = 0; i < tasks.size(); i++) {
            JSONObject task = tasks.getJSONObject(i);
            if (task != null && taskId.equals(task.getString("taskId"))) {
                return Optional.of(task);
            }
        }
        return Optional.empty();
    }

    private boolean hasClaimTaskFuel(JSONObject playerState) {
        JSONObject resources = playerState == null ? null : playerState.getJSONObject("resources");
        for (String resourceType : CLAIM_TASK_FUEL_TYPES) {
            if (resourceCount(resources, resourceType) > 0) {
                return true;
            }
        }
        return false;
    }

    private boolean hasClaimTaskFuelStock(JSONObject node) {
        JSONObject stock = node == null ? null : node.getJSONObject("resourceStock");
        if (stock == null) {
            return false;
        }
        for (String resourceType : CLAIM_TASK_FUEL_TYPES) {
            if (stock.getIntValue(resourceType) > 0) {
                return true;
            }
        }
        return false;
    }

    private boolean isTaskClaimRejection(String action, JSONObject payload) {
        if ("CLAIM_TASK".equals(action)) {
            return true;
        }
        String objectKey = payload.getString("objectKey");
        return hasText(payload.getString("taskId")) || hasText(objectKey) && objectKey.startsWith("TASK:");
    }

    private String taskIdFromObjectKey(String objectKey) {
        if (!hasText(objectKey) || !objectKey.startsWith("TASK:")) {
            return "";
        }
        return objectKey.substring("TASK:".length());
    }

    private String currentNextNodeId(JSONObject inquireData) {
        return playerState(inquireData).map(player -> safeText(player.getString("nextNodeId"))).orElse("");
    }

    private boolean shouldYieldProcessPriority(JSONObject inquireData, String targetNodeId) {
        String nodeId = hasText(targetNodeId)
                ? targetNodeId
                : playerState(inquireData).map(player -> player.getString("currentNodeId")).orElse("");
        if (!hasText(nodeId)) {
            return false;
        }
        JSONArray players = inquireData.getJSONArray("players");
        if (players == null) {
            return false;
        }
        for (int i = 0; i < players.size(); i++) {
            JSONObject player = players.getJSONObject(i);
            if (player == null || player.getIntValue("playerId") == playerId) {
                continue;
            }
            if (nodeId.equals(player.getString("currentNodeId")) && !player.getBooleanValue("delivered")
                    && !player.getBooleanValue("retired") && player.getIntValue("playerId") < playerId) {
                return true;
            }
        }
        return false;
    }

    private String processNodeId(JSONObject inquireData, String targetNodeId) {
        return hasText(targetNodeId)
                ? targetNodeId
                : playerState(inquireData).map(player -> player.getString("currentNodeId")).orElse("");
    }

    private int processYieldRounds(JSONObject inquireData, JSONObject busyPayload, String targetNodeId,
                                   int conflictCount, boolean roundConflict) {
        String nodeId = processNodeId(inquireData, targetNodeId);
        OptionalInt ownerRemainingRound = ownerProcessRemainingRound(inquireData, busyPayload, nodeId);
        if (!roundConflict && ownerRemainingRound.isPresent()) {
            // OBJECT_BUSY 同帧通常带 owner 的 PROCESS_PROGRESS；按剩余读条退避，避免固定退避多罚站。
            return Math.max(1, ownerRemainingRound.getAsInt());
        }
        int processRound = 4;
        JSONArray nodes = inquireData.getJSONArray("nodes");
        if (nodes != null && hasText(nodeId)) {
            for (int i = 0; i < nodes.size(); i++) {
                JSONObject node = nodes.getJSONObject(i);
                if (node != null && nodeId.equals(node.getString("nodeId")) && node.getIntValue("processRound") > 0) {
                    processRound = node.getIntValue("processRound");
                    break;
                }
            }
        }
        // 第一次 ROUND_CONFLICT 只错开一个完整读条后多 1 拍；D07/D06 常见 7 拍重试，继续等到同拍会再次撞车。
        int shortYield = Math.max(3, processRound + 1);
        if (!roundConflict || conflictCount <= 1) {
            return shortYield;
        }
        // 第二次及之后的 ROUND_CONFLICT 改用非 7 拍周期，专门打破双方“同时等、同时再试”的镜像锁死。
        return Math.max(shortYield + 4, processRound + 7 + Math.floorMod(playerId, 3));
    }

    private OptionalInt ownerProcessRemainingRound(JSONObject inquireData, JSONObject busyPayload, String targetNodeId) {
        OptionalInt fromBusyPayload = processRemainingFromEntry(busyPayload, busyPayload, targetNodeId);
        if (fromBusyPayload.isPresent()) {
            return fromBusyPayload;
        }
        OptionalInt fromEvents = ownerProcessRemainingRound(inquireData.getJSONArray("events"), busyPayload,
                targetNodeId);
        if (fromEvents.isPresent()) {
            return fromEvents;
        }
        return ownerProcessRemainingRound(inquireData.getJSONArray("messages"), busyPayload, targetNodeId);
    }

    private OptionalInt ownerProcessRemainingRound(JSONArray entries, JSONObject busyPayload, String targetNodeId) {
        if (entries == null) {
            return OptionalInt.empty();
        }
        for (int i = 0; i < entries.size(); i++) {
            JSONObject entry = entries.getJSONObject(i);
            OptionalInt remainingRound = processRemainingFromEntry(entry, busyPayload, targetNodeId);
            if (remainingRound.isPresent()) {
                return remainingRound;
            }
        }
        return OptionalInt.empty();
    }

    private OptionalInt processRemainingFromEntry(JSONObject entry, JSONObject busyPayload, String targetNodeId) {
        JSONObject payload = payloadOrSelf(entry);
        if (payload == null || !payload.containsKey("remainingRound")) {
            return OptionalInt.empty();
        }
        int ownerPlayerId = busyPayload == null ? 0 : busyPayload.getIntValue("ownerPlayerId");
        if (ownerPlayerId > 0 && payload.getIntValue("playerId") != ownerPlayerId) {
            return OptionalInt.empty();
        }
        String ownerProcessType = busyPayload == null ? "" : busyPayload.getString("ownerProcessType");
        String processType = payload.getString("processType");
        if (hasText(ownerProcessType) && hasText(processType) && !ownerProcessType.equals(processType)) {
            return OptionalInt.empty();
        }
        String payloadTargetNodeId = firstText(payload, "targetNodeId", "nodeId");
        if (hasText(targetNodeId) && hasText(payloadTargetNodeId) && !targetNodeId.equals(payloadTargetNodeId)) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(Math.max(0, payload.getIntValue("remainingRound")));
    }

    private void markCompletedProcessStations(JSONArray events) {
        if (events == null) {
            return;
        }
        for (int i = 0; i < events.size(); i++) {
            JSONObject event = events.getJSONObject(i);
            if (event == null || !"PROCESS_COMPLETE".equals(event.getString("type"))) {
                continue;
            }
            JSONObject payload = event.getJSONObject("payload");
            if (payload == null || payload.getIntValue("playerId") != playerId) {
                continue;
            }
            String nodeId = firstText(payload, "targetNodeId", "nodeId");
            if (hasText(nodeId)) {
                processedStations.add(nodeId);
                processConflictCounts.remove(nodeId);
            }
        }
    }

    private JSONObject payloadOrSelf(JSONObject entry) {
        if (entry == null) {
            return null;
        }
        JSONObject payload = entry.getJSONObject("payload");
        return payload == null ? entry : payload;
    }

    private String resourceClaimKey(String nodeId, String resourceType) {
        if (!hasText(nodeId) || !hasText(resourceType)) {
            return "";
        }
        return nodeId + "#" + resourceType;
    }

    private String matchKey(JSONObject inquireData) {
        String matchId = safeText(inquireData.getString("matchId"));
        return matchId.isBlank() ? "default" : matchId;
    }

    private String squadOrderKey(String action, String targetNodeId) {
        return action + "#" + targetNodeId;
    }

    private boolean isBusy(String state) {
        return "VERIFYING".equals(state)
                || STATE_PROCESSING.equals(state)
                || STATE_CONTESTING.equals(state)
                || STATE_RESTING.equals(state)
                || "FORCED_PASSING".equals(state)
                || "RETIRED".equals(state);
    }

    private boolean isRushPhase(JSONObject inquireData, int round) {
        String phase = inquireData.getString("phase");
        if ("RUSH".equalsIgnoreCase(phase)) {
            return true;
        }
        int rushStartRound = inquireData.getIntValue("rushStartRound");
        return rushStartRound > 0 && round >= rushStartRound;
    }

    private String playerTeam(JSONObject playerState) {
        return firstText(playerState, "camp", "teamId", "campId");
    }

    private String firstText(JSONObject object, String... keys) {
        if (object == null || keys == null) {
            return "";
        }
        for (String key : keys) {
            String value = object.getString(key);
            if (hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private boolean arrayContains(JSONArray values, String value) {
        if (values == null || !hasText(value)) {
            return false;
        }
        for (int i = 0; i < values.size(); i++) {
            if (value.equals(values.getString(i))) {
                return true;
            }
        }
        return false;
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record MainActionContext(
            JSONObject playerState,
            JSONObject inquireData,
            int round,
            String state,
            String currentNodeId,
            String nextNodeId,
            Set<String> weatherPenalties,
            boolean verified) {

        private String targetNodeId() {
            return verified ? TERMINAL_NODE_ID : VERIFY_GATE_NODE_ID;
        }
    }

    private RoleActionCommand decision(int round, JSONObject playerState, RoleActionCommand action, String reason) {
        LOG.info(
                "YH 决策 round={}, state={}, currentNode={}, nextNode={}, verified={}, freshness={}, action={}, target={}, resource={}, task={}, contest={}, card={}, rush={}, goodFruit={}, badFruit={}, extraGoodFruit={}, reason={}",
                round,
                playerState.getString("state"),
                playerState.getString("currentNodeId"),
                playerState.getString("nextNodeId"),
                playerState.getBooleanValue("verified"),
                playerState.getDoubleValue("freshness"),
                action.getAction(),
                action.getTargetNodeId(),
                action.getResourceType(),
                action.getTaskId(),
                action.getContestId(),
                action.getCard(),
                action.getRushTactic(),
                action.getGoodFruit(),
                action.getBadFruit(),
                action.getExtraGoodFruit(),
                reason);
        return action;
    }
}
