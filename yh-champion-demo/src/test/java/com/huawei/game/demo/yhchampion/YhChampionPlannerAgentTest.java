package com.huawei.game.demo.yhchampion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.junit.jupiter.api.Test;

class YhChampionPlannerAgentTest {
    @Test
    void prioritizesCurrentHighValueTaskOverCurrentResource() {
        YhChampionPlannerAgent agent = new YhChampionPlannerAgent(6);
        agent.onStart(json("""
                {
                  "map": {
                    "edges": [
                      {"fromNode": "S09", "toNode": "S14", "distance": 20, "bidirectional": true}
                    ]
                  }
                }
                """));

        JSONObject root = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-task-before-resource",
                  "round": 90,
                  "nodes": [
                    {"nodeId": "S09", "resourceStock": {"FAST_HORSE": 1}}
                  ],
                  "tasks": [
                    {
                      "taskId": "T10_090_01",
                      "nodeId": "S09",
                      "active": true,
                      "completed": false,
                      "failed": false,
                      "processType": "DELIVER_FRESHNESS_CHECK",
                      "requiredFreshness": 80.0,
                      "score": 34,
                      "processRound": 2,
                      "expireRound": 150
                    }
                  ],
                  "players": [
                    {
                      "playerId": 6,
                      "camp": "RED",
                      "state": "IDLE",
                      "currentNodeId": "S09",
                      "freshness": 92.0,
                      "verified": false,
                      "resources": {}
                    }
                  ]
                }
                """)));

        JSONObject action = firstAction(root);
        assertThat(action.getString("action")).isEqualTo("CLAIM_TASK");
        assertThat(action.getString("taskId")).isEqualTo("T10_090_01");
    }

    @Test
    void claimsCurrentClaimTaskTypeAtS09BeforeContinuingToGate() {
        YhChampionPlannerAgent agent = new YhChampionPlannerAgent(6);
        agent.onStart(json("""
                {
                  "map": {
                    "edges": [
                      {"fromNode": "S09", "toNode": "S14", "distance": 22, "bidirectional": true}
                    ]
                  }
                }
                """));

        JSONObject root = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-claim-task-type",
                  "round": 226,
                  "tasks": [
                    {
                      "taskId": "T_004",
                      "nodeId": "S09",
                      "active": true,
                      "completed": false,
                      "failed": false,
                      "processType": "CLAIM_TASK",
                      "score": 30,
                      "processRound": 3,
                      "expireRound": 320
                    }
                  ],
                  "players": [
                    {
                      "playerId": 6,
                      "camp": "RED",
                      "state": "IDLE",
                      "currentNodeId": "S09",
                      "freshness": 86.0,
                      "verified": false,
                      "resources": {"ICE_BOX": 1}
                    }
                  ]
                }
                """)));

        JSONObject action = firstAction(root);
        assertThat(action.getString("action")).isEqualTo("CLAIM_TASK");
        assertThat(action.getString("taskId")).isEqualTo("T_004");
    }

    @Test
    void skipsLowValueIntelAtCurrentNodeAndKeepsMoving() {
        YhChampionPlannerAgent agent = new YhChampionPlannerAgent(6);
        agent.onStart(json("""
                {
                  "map": {
                    "edges": [
                      {"fromNode": "S01", "toNode": "S02", "distance": 6, "bidirectional": true},
                      {"fromNode": "S02", "toNode": "S14", "distance": 8, "bidirectional": true}
                    ]
                  }
                }
                """));

        JSONObject root = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-skip-intel",
                  "round": 35,
                  "nodes": [
                    {"nodeId": "S01", "resourceStock": {"INTEL": 1}}
                  ],
                  "players": [
                    {
                      "playerId": 6,
                      "camp": "BLUE",
                      "state": "IDLE",
                      "currentNodeId": "S01",
                      "freshness": 96.0,
                      "verified": false,
                      "resources": {}
                    }
                  ]
                }
                """)));

        JSONObject action = firstAction(root);
        assertThat(action.getString("action")).isEqualTo("MOVE");
        assertThat(action.getString("targetNodeId")).isEqualTo("S02");
    }

    @Test
    void retriesStationProcessWhenWaitingStateHasNoNextNode() {
        YhChampionPlannerAgent agent = new YhChampionPlannerAgent(1001);
        agent.onStart(json("""
                {
                  "map": {
                    "edges": [
                      {"fromNode": "S02", "toNode": "S03", "routeType": "ROAD", "distance": 25, "bidirectional": true}
                    ]
                  }
                }
                """));

        JSONObject root = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-waiting-station-retry",
                  "round": 46,
                  "nodes": [
                    {"nodeId": "S02", "resourceStock": {}}
                  ],
                  "tasks": [],
                  "players": [
                    {
                      "playerId": 1001,
                      "camp": "RED",
                      "state": "WAITING",
                      "currentNodeId": "S02",
                      "freshness": 97.5,
                      "verified": false,
                      "resources": {}
                    }
                  ]
                }
                """)));

        JSONObject action = firstAction(root);
        assertThat(action.getString("action")).isEqualTo("PROCESS");
        assertThat(action.getString("targetNodeId")).isEqualTo("S02");
    }

    @Test
    void resumesMoveWhenWaitingStateStillHasNextNode() {
        YhChampionPlannerAgent agent = new YhChampionPlannerAgent(1001);
        agent.onStart(json("""
                {
                  "map": {
                    "edges": [
                      {"fromNode": "S02", "toNode": "S03", "routeType": "ROAD", "distance": 25, "bidirectional": true}
                    ]
                  }
                }
                """));

        JSONObject root = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-waiting-move",
                  "round": 60,
                  "nodes": [
                    {"nodeId": "S02", "resourceStock": {}}
                  ],
                  "tasks": [],
                  "players": [
                    {
                      "playerId": 1001,
                      "camp": "RED",
                      "state": "WAITING",
                      "currentNodeId": "S02",
                      "nextNodeId": "S03",
                      "freshness": 97.0,
                      "verified": false,
                      "resources": {}
                    }
                  ]
                }
                """)));

        JSONObject action = firstAction(root);
        assertThat(action.getString("action")).isEqualTo("MOVE");
        assertThat(action.getString("targetNodeId")).isEqualTo("S03");
    }

    @Test
    void claimsPermitResourcesAsWindowFuelAtCurrentNodeWhenContestIsActive() {
        YhChampionPlannerAgent agent = new YhChampionPlannerAgent(6);
        agent.onStart(json("""
                {
                  "map": {
                    "edges": [
                      {"fromNode": "S03", "toNode": "S14", "distance": 12, "bidirectional": true}
                    ]
                  }
                }
                """));

        JSONObject root = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-permit-fuel",
                  "round": 48,
                  "activeContests": [
                    {
                      "contestId": "C_048_001",
                      "contestType": "GATE",
                      "redPlayerId": 6,
                      "bluePlayerId": 7,
                      "roundIndex": 1,
                      "deadlineRound": 50,
                      "resolved": false
                    }
                  ],
                  "nodes": [
                    {"nodeId": "S03", "resourceStock": {"OFFICIAL_PERMIT": 1}}
                  ],
                  "players": [
                    {
                      "playerId": 6,
                      "camp": "RED",
                      "state": "IDLE",
                      "currentNodeId": "S03",
                      "freshness": 94.0,
                      "verified": false,
                      "resources": {}
                    }
                  ]
                }
                """)));

        JSONObject action = firstAction(root);
        assertThat(action.getString("action")).isEqualTo("CLAIM_RESOURCE");
        assertThat(action.getString("resourceType")).isEqualTo("OFFICIAL_PERMIT");
    }

    @Test
    void skipsWindowFuelAtCurrentNodeWhenNoContestOrKnownGuardNeedsIt() {
        YhChampionPlannerAgent agent = new YhChampionPlannerAgent(6);
        agent.onStart(json("""
                {
                  "map": {
                    "edges": [
                      {"fromNode": "S03", "toNode": "S14", "distance": 12, "bidirectional": true}
                    ]
                  }
                }
                """));

        JSONObject root = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-skip-idle-window-fuel",
                  "round": 48,
                  "nodes": [
                    {"nodeId": "S03", "resourceStock": {"OFFICIAL_PERMIT": 1}}
                  ],
                  "players": [
                    {
                      "playerId": 6,
                      "camp": "RED",
                      "state": "IDLE",
                      "currentNodeId": "S03",
                      "freshness": 94.0,
                      "verified": false,
                      "resources": {}
                    }
                  ]
                }
                """)));

        JSONObject action = firstAction(root);
        assertThat(action.getString("action")).isEqualTo("MOVE");
        assertThat(action.getString("targetNodeId")).isEqualTo("S14");
    }

    @Test
    void abandonsRejectedResourceClaimInsteadOfLoopingOnSameStock() {
        YhChampionPlannerAgent agent = new YhChampionPlannerAgent(6);
        agent.onStart(json("""
                {
                  "map": {
                    "edges": [
                      {"fromNode": "S03", "toNode": "S14", "distance": 12, "bidirectional": true}
                    ]
                  }
                }
                """));

        JSONObject first = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-abandon-resource",
                  "round": 48,
                  "nodes": [
                    {"nodeId": "S03", "resourceStock": {"ICE_BOX": 1}}
                  ],
                  "players": [
                    {
                      "playerId": 6,
                      "camp": "RED",
                      "state": "IDLE",
                      "currentNodeId": "S03",
                      "freshness": 94.0,
                      "verified": false,
                      "resources": {}
                    }
                  ]
                }
                """)));

        assertThat(firstAction(first).getString("action")).isEqualTo("CLAIM_RESOURCE");

        JSONObject second = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-abandon-resource",
                  "round": 49,
                  "actionResults": [
                    {
                      "playerId": 6,
                      "action": "CLAIM_RESOURCE",
                      "accepted": false,
                      "errorCode": "OBJECT_BUSY",
                      "targetNodeId": "S03",
                      "resourceType": "ICE_BOX"
                    }
                  ],
                  "nodes": [
                    {"nodeId": "S03", "resourceStock": {"ICE_BOX": 1}}
                  ],
                  "players": [
                    {
                      "playerId": 6,
                      "camp": "RED",
                      "state": "IDLE",
                      "currentNodeId": "S03",
                      "freshness": 93.9,
                      "verified": false,
                      "resources": {}
                    }
                  ]
                }
                """)));

        JSONObject action = firstAction(second);
        assertThat(action.getString("action")).isEqualTo("MOVE");
        assertThat(action.getString("targetNodeId")).isEqualTo("S14");
    }

    @Test
    void claimsSecondIceBoxAsFreshnessInsuranceForStrongRushOpponents() {
        YhChampionPlannerAgent agent = new YhChampionPlannerAgent(6);
        agent.onStart(json("""
                {
                  "map": {
                    "edges": [
                      {"fromNode": "S07", "toNode": "S14", "distance": 12, "bidirectional": true}
                    ]
                  }
                }
                """));

        JSONObject root = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-skip-duplicate-icebox",
                  "round": 167,
                  "nodes": [
                    {"nodeId": "S07", "resourceStock": {"ICE_BOX": 1}}
                  ],
                  "players": [
                    {
                      "playerId": 6,
                      "camp": "RED",
                      "state": "IDLE",
                      "currentNodeId": "S07",
                      "freshness": 88.0,
                      "verified": false,
                      "resources": {"ICE_BOX": 1}
                    }
                  ]
                }
                """)));

        JSONObject action = firstAction(root);
        assertThat(action.getString("action")).isEqualTo("CLAIM_RESOURCE");
        assertThat(action.getString("resourceType")).isEqualTo("ICE_BOX");
    }

    @Test
    void abandonsRejectedTaskClaimInsteadOfRepeatingExpiredContest() {
        YhChampionPlannerAgent agent = new YhChampionPlannerAgent(6);
        agent.onStart(json("""
                {
                  "map": {
                    "edges": [
                      {"fromNode": "S09", "toNode": "S14", "distance": 20, "bidirectional": true}
                    ]
                  }
                }
                """));

        JSONObject first = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-abandon-task",
                  "round": 90,
                  "tasks": [
                    {
                      "taskId": "T01_090_01",
                      "nodeId": "S09",
                      "active": true,
                      "completed": false,
                      "failed": false,
                      "processType": "STATION_PROCESS",
                      "score": 20,
                      "processRound": 3,
                      "expireRound": 150
                    }
                  ],
                  "players": [
                    {
                      "playerId": 6,
                      "camp": "RED",
                      "state": "IDLE",
                      "currentNodeId": "S09",
                      "freshness": 92.0,
                      "verified": false,
                      "resources": {}
                    }
                  ]
                }
                """)));

        assertThat(firstAction(first).getString("action")).isEqualTo("CLAIM_TASK");

        JSONObject second = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-abandon-task",
                  "round": 91,
                  "events": [
                    {
                      "type": "ACTION_REJECTED",
                      "payload": {
                        "playerId": 6,
                        "action": "CLAIM_TASK",
                        "errorCode": "OBJECT_BUSY",
                        "taskId": "T01_090_01"
                      }
                    }
                  ],
                  "tasks": [
                    {
                      "taskId": "T01_090_01",
                      "nodeId": "S09",
                      "active": true,
                      "completed": false,
                      "failed": false,
                      "processType": "STATION_PROCESS",
                      "score": 20,
                      "processRound": 3,
                      "expireRound": 150
                    }
                  ],
                  "players": [
                    {
                      "playerId": 6,
                      "camp": "RED",
                      "state": "IDLE",
                      "currentNodeId": "S09",
                      "freshness": 91.9,
                      "verified": false,
                      "resources": {}
                    }
                  ]
                }
                """)));

        JSONObject action = firstAction(second);
        assertThat(action.getString("action")).isEqualTo("MOVE");
        assertThat(action.getString("targetNodeId")).isEqualTo("S14");
    }

    @Test
    void abandonsBusyTaskClaimWhenRejectedEventOmitsActionField() {
        YhChampionPlannerAgent agent = new YhChampionPlannerAgent(6);
        agent.onStart(json("""
                {
                  "map": {
                    "edges": [
                      {"fromNode": "S13", "toNode": "S14", "distance": 5, "bidirectional": true}
                    ]
                  }
                }
                """));

        JSONObject first = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-abandon-task-without-action",
                  "round": 409,
                  "nodes": [
                    {"nodeId": "S13", "processRound": 5, "processType": "PALACE_TRANSFER"}
                  ],
                  "tasks": [
                    {
                      "taskId": "T_007",
                      "nodeId": "S13",
                      "active": true,
                      "completed": false,
                      "failed": false,
                      "processType": "STATION_PROCESS",
                      "score": 25,
                      "processRound": 4,
                      "expireRound": 520
                    }
                  ],
                  "players": [
                    {
                      "playerId": 6,
                      "camp": "RED",
                      "state": "IDLE",
                      "currentNodeId": "S13",
                      "freshness": 82.0,
                      "verified": false,
                      "resources": {}
                    }
                  ]
                }
                """)));

        assertThat(firstAction(first).getString("action")).isEqualTo("CLAIM_TASK");

        JSONObject second = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-abandon-task-without-action",
                  "round": 410,
                  "events": [
                    {
                      "type": "ACTION_REJECTED",
                      "payload": {
                        "playerId": 6,
                        "errorCode": "OBJECT_BUSY",
                        "objectKey": "TASK:T_007",
                        "ownerPlayerId": 16,
                        "ownerProcessType": "CLAIM_TASK",
                        "targetNodeId": "S13",
                        "taskId": "T_007"
                      }
                    }
                  ],
                  "nodes": [
                    {"nodeId": "S13", "processRound": 5, "processType": "PALACE_TRANSFER"}
                  ],
                  "tasks": [
                    {
                      "taskId": "T_007",
                      "nodeId": "S13",
                      "active": true,
                      "completed": false,
                      "failed": false,
                      "processType": "STATION_PROCESS",
                      "score": 25,
                      "processRound": 4,
                      "expireRound": 520
                    }
                  ],
                  "players": [
                    {
                      "playerId": 6,
                      "camp": "RED",
                      "state": "IDLE",
                      "currentNodeId": "S13",
                      "freshness": 81.9,
                      "verified": false,
                      "resources": {}
                    }
                  ]
                }
                """)));

        JSONObject action = firstAction(second);
        assertThat(action.getString("action")).isEqualTo("PROCESS");
        assertThat(action.getString("targetNodeId")).isEqualTo("S13");
    }

    @Test
    void abandonsLastTaskClaimWhenResourceNotEnoughRejectionOmitsTaskIdentity() {
        YhChampionPlannerAgent agent = new YhChampionPlannerAgent(6);
        agent.onStart(json("""
                {
                  "map": {
                    "edges": [
                      {"fromNode": "S09", "toNode": "S14", "distance": 22, "bidirectional": true}
                    ]
                  }
                }
                """));

        JSONObject first = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-resource-not-enough-task",
                  "round": 226,
                  "tasks": [
                    {
                      "taskId": "T_004",
                      "nodeId": "S09",
                      "active": true,
                      "completed": false,
                      "failed": false,
                      "processType": "CLAIM_TASK",
                      "score": 30,
                      "processRound": 3,
                      "expireRound": 320
                    }
                  ],
                  "players": [
                    {
                      "playerId": 6,
                      "camp": "RED",
                      "state": "IDLE",
                      "currentNodeId": "S09",
                      "freshness": 86.0,
                      "verified": false,
                      "resources": {}
                    }
                  ]
                }
                """)));

        assertThat(firstAction(first).getString("action")).isEqualTo("CLAIM_TASK");

        JSONObject second = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-resource-not-enough-task",
                  "round": 227,
                  "events": [
                    {
                      "type": "ACTION_REJECTED",
                      "payload": {
                        "playerId": 6,
                        "errorCode": "RESOURCE_NOT_ENOUGH"
                      }
                    }
                  ],
                  "tasks": [
                    {
                      "taskId": "T_004",
                      "nodeId": "S09",
                      "active": true,
                      "completed": false,
                      "failed": false,
                      "processType": "CLAIM_TASK",
                      "score": 30,
                      "processRound": 3,
                      "expireRound": 320
                    }
                  ],
                  "players": [
                    {
                      "playerId": 6,
                      "camp": "RED",
                      "state": "IDLE",
                      "currentNodeId": "S09",
                      "freshness": 85.9,
                      "verified": false,
                      "resources": {}
                    }
                  ]
                }
                """)));

        JSONObject action = firstAction(second);
        assertThat(action.getString("action")).isEqualTo("MOVE");
        assertThat(action.getString("targetNodeId")).isEqualTo("S14");
    }

    @Test
    void claimsResourceGatedTaskBeforeUsingHorseBuffAtTaskNode() {
        YhChampionPlannerAgent agent = new YhChampionPlannerAgent(6);
        agent.onStart(json("""
                {
                  "map": {
                    "edges": [
                      {"fromNode": "S07", "toNode": "S14", "distance": 28, "bidirectional": true}
                    ]
                  }
                }
                """));

        JSONObject root = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-horse-task-before-buff",
                  "round": 176,
                  "tasks": [
                    {
                      "taskId": "T_004",
                      "nodeId": "S07",
                      "active": true,
                      "completed": false,
                      "failed": false,
                      "processType": "CLAIM_TASK",
                      "score": 30,
                      "processRound": 3,
                      "expireRound": 320
                    }
                  ],
                  "players": [
                    {
                      "playerId": 6,
                      "camp": "RED",
                      "state": "IDLE",
                      "currentNodeId": "S07",
                      "freshness": 86.0,
                      "verified": false,
                      "resources": {"FAST_HORSE": 1}
                    }
                  ]
                }
                """)));

        JSONObject action = firstAction(root);
        assertThat(action.getString("action")).isEqualTo("CLAIM_TASK");
        assertThat(action.getString("taskId")).isEqualTo("T_004");
    }

    @Test
    void preservesHorseFuelForReachableClaimTaskWithExplicitResourceRequirement() {
        YhChampionPlannerAgent agent = new YhChampionPlannerAgent(6);
        agent.onStart(json("""
                {
                  "map": {
                    "edges": [
                      {"fromNode": "S07", "toNode": "S09", "distance": 58, "bidirectional": true},
                      {"fromNode": "S09", "toNode": "S14", "distance": 76, "bidirectional": true}
                    ]
                  }
                }
                """));

        JSONObject root = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-preserve-horse-for-s09-task",
                  "round": 165,
                  "nodes": [
                    {"nodeId": "S07", "resourceStock": {}},
                    {"nodeId": "S09", "resourceStock": {}}
                  ],
                  "tasks": [
                    {
                      "taskId": "T_004",
                      "nodeId": "S09",
                      "active": true,
                      "completed": false,
                      "failed": false,
                      "processType": "CLAIM_TASK",
                      "requiredResourceTypes": ["FAST_HORSE", "SHORT_HORSE"],
                      "score": 30,
                      "processRound": 3,
                      "expireRound": 320
                    }
                  ],
                  "players": [
                    {
                      "playerId": 6,
                      "camp": "RED",
                      "state": "IDLE",
                      "currentNodeId": "S07",
                      "freshness": 89.7,
                      "verified": false,
                      "resources": {"FAST_HORSE": 1},
                      "scoreDetail": {"tasks": 50, "total": 0}
                    }
                  ]
                }
                """)));

        JSONObject action = firstAction(root);
        assertThat(action.getString("action")).isEqualTo("MOVE");
        assertThat(action.getString("targetNodeId")).isEqualTo("S09");
    }

    @Test
    void retriesRecoverableClaimTaskAfterAnonymousResourceNotEnoughAndLocalHorseClaim() {
        YhChampionPlannerAgent agent = new YhChampionPlannerAgent(6);
        agent.onStart(json("""
                {
                  "map": {
                    "edges": [
                      {"fromNode": "S07", "toNode": "S14", "distance": 28, "bidirectional": true}
                    ]
                  }
                }
                """));

        JSONObject first = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-recover-task-resource",
                  "round": 175,
                  "tasks": [
                    {
                      "taskId": "T_004",
                      "nodeId": "S07",
                      "active": true,
                      "completed": false,
                      "failed": false,
                      "processType": "CLAIM_TASK",
                      "score": 30,
                      "processRound": 3,
                      "expireRound": 320
                    }
                  ],
                  "players": [
                    {
                      "playerId": 6,
                      "camp": "RED",
                      "state": "IDLE",
                      "currentNodeId": "S07",
                      "freshness": 86.0,
                      "verified": false,
                      "resources": {}
                    }
                  ]
                }
                """)));
        assertThat(firstAction(first).getString("action")).isEqualTo("CLAIM_TASK");

        JSONObject second = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-recover-task-resource",
                  "round": 176,
                  "events": [
                    {
                      "type": "ACTION_REJECTED",
                      "payload": {
                        "playerId": 6,
                        "errorCode": "RESOURCE_NOT_ENOUGH"
                      }
                    }
                  ],
                  "nodes": [
                    {"nodeId": "S07", "resourceStock": {"FAST_HORSE": 1}}
                  ],
                  "tasks": [
                    {
                      "taskId": "T_004",
                      "nodeId": "S07",
                      "active": true,
                      "completed": false,
                      "failed": false,
                      "processType": "CLAIM_TASK",
                      "score": 30,
                      "processRound": 3,
                      "expireRound": 320
                    }
                  ],
                  "players": [
                    {
                      "playerId": 6,
                      "camp": "RED",
                      "state": "IDLE",
                      "currentNodeId": "S07",
                      "freshness": 85.9,
                      "verified": false,
                      "resources": {}
                    }
                  ]
                }
                """)));
        assertThat(firstAction(second).getString("action")).isEqualTo("CLAIM_RESOURCE");
        assertThat(firstAction(second).getString("resourceType")).isEqualTo("FAST_HORSE");

        JSONObject third = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-recover-task-resource",
                  "round": 179,
                  "tasks": [
                    {
                      "taskId": "T_004",
                      "nodeId": "S07",
                      "active": true,
                      "completed": false,
                      "failed": false,
                      "processType": "CLAIM_TASK",
                      "score": 30,
                      "processRound": 3,
                      "expireRound": 320
                    }
                  ],
                  "players": [
                    {
                      "playerId": 6,
                      "camp": "RED",
                      "state": "IDLE",
                      "currentNodeId": "S07",
                      "freshness": 85.6,
                      "verified": false,
                      "resources": {"FAST_HORSE": 1}
                    }
                  ]
                }
                """)));

        JSONObject action = firstAction(third);
        assertThat(action.getString("action")).isEqualTo("CLAIM_TASK");
        assertThat(action.getString("taskId")).isEqualTo("T_004");
    }

    @Test
    void treatsClaimTaskRequiredResourcesAsAlternatives() {
        YhChampionPlannerAgent agent = new YhChampionPlannerAgent(6);
        agent.onStart(json("""
                {
                  "map": {
                    "edges": [
                      {"fromNode": "S07", "toNode": "S14", "distance": 28, "bidirectional": true}
                    ]
                  }
                }
                """));

        JSONObject root = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-required-resource-alternative",
                  "round": 171,
                  "tasks": [
                    {
                      "taskId": "T_004",
                      "taskTemplateId": "T06",
                      "nodeId": "S07",
                      "active": true,
                      "completed": false,
                      "failed": false,
                      "processType": "CLAIM_TASK",
                      "requiredResourceTypes": ["FAST_HORSE", "SHORT_HORSE"],
                      "score": 30,
                      "processRound": 3,
                      "expireRound": 320
                    }
                  ],
                  "players": [
                    {
                      "playerId": 6,
                      "camp": "RED",
                      "state": "IDLE",
                      "currentNodeId": "S07",
                      "freshness": 85.6,
                      "verified": false,
                      "resources": {"FAST_HORSE": 1}
                    }
                  ]
                }
                """)));

        JSONObject action = firstAction(root);
        assertThat(action.getString("action")).isEqualTo("CLAIM_TASK");
        assertThat(action.getString("taskId")).isEqualTo("T_004");
    }

    @Test
    void doesNotSetLateGuardAtS13AfterRequiredProcessIsComplete() {
        YhChampionPlannerAgent agent = new YhChampionPlannerAgent(6);
        agent.onStart(json("""
                {
                  "map": {
                    "edges": [
                      {"fromNode": "S13", "toNode": "S14", "distance": 4, "bidirectional": true}
                    ]
                  }
                }
                """));

        JSONObject root = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-s13-tempo",
                  "round": 210,
                  "events": [
                    {
                      "type": "PROCESS_COMPLETE",
                      "payload": {
                        "playerId": 6,
                        "targetNodeId": "S13"
                      }
                    }
                  ],
                  "nodes": [
                    {"nodeId": "S13"}
                  ],
                  "players": [
                    {
                      "playerId": 6,
                      "camp": "RED",
                      "state": "IDLE",
                      "currentNodeId": "S13",
                      "freshness": 88.0,
                      "verified": false,
                      "goodFruit": 4,
                      "guardActionPoint": 1,
                      "resources": {}
                    }
                  ]
                }
                """)));

        JSONObject action = firstAction(root);
        assertThat(action.getString("action")).isEqualTo("MOVE");
        assertThat(action.getString("targetNodeId")).isEqualTo("S14");
    }

    @Test
    void doesNotSetS11GuardAfterOpponentHasAlreadyPassedIt() {
        YhChampionPlannerAgent agent = new YhChampionPlannerAgent(6);
        agent.onStart(json("""
                {
                  "map": {
                    "edges": [
                      {"fromNode": "S11", "toNode": "S12", "distance": 8, "bidirectional": true},
                      {"fromNode": "S12", "toNode": "S13", "distance": 8, "bidirectional": true},
                      {"fromNode": "S13", "toNode": "S14", "distance": 8, "bidirectional": true}
                    ]
                  }
                }
                """));

        JSONObject root = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-s11-late-guard",
                  "round": 355,
                  "events": [
                    {
                      "type": "PROCESS_COMPLETE",
                      "payload": {
                        "playerId": 6,
                        "targetNodeId": "S11"
                      }
                    }
                  ],
                  "nodes": [
                    {"nodeId": "S11"}
                  ],
                  "players": [
                    {
                      "playerId": 6,
                      "camp": "RED",
                      "state": "IDLE",
                      "currentNodeId": "S11",
                      "freshness": 78.0,
                      "verified": false,
                      "goodFruit": 4,
                      "guardActionPoint": 1,
                      "resources": {}
                    },
                    {
                      "playerId": 20,
                      "camp": "BLUE",
                      "state": "MOVING",
                      "currentNodeId": "S12",
                      "nextNodeId": "S13",
                      "freshness": 74.0,
                      "verified": false,
                      "resources": {}
                    }
                  ]
                }
                """)));

        JSONObject action = firstAction(root);
        assertThat(action.getString("action")).isEqualTo("MOVE");
        assertThat(action.getString("targetNodeId")).isEqualTo("S12");
    }

    @Test
    void skipsS11GuardWhenS10GuardAlreadyBlocksTrailingOpponent() {
        YhChampionPlannerAgent agent = new YhChampionPlannerAgent(6);
        agent.onStart(json("""
                {
                  "map": {
                    "edges": [
                      {"fromNode": "S09", "toNode": "S10", "distance": 8, "bidirectional": true},
                      {"fromNode": "S10", "toNode": "S11", "distance": 8, "bidirectional": true},
                      {"fromNode": "S11", "toNode": "S12", "distance": 8, "bidirectional": true},
                      {"fromNode": "S12", "toNode": "S13", "distance": 8, "bidirectional": true},
                      {"fromNode": "S13", "toNode": "S14", "distance": 8, "bidirectional": true},
                      {"fromNode": "S14", "toNode": "S15", "distance": 8, "bidirectional": true}
                    ]
                  }
                }
                """));

        JSONObject root = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-s11-redundant-upstream-guard",
                  "round": 340,
                  "events": [
                    {
                      "type": "PROCESS_COMPLETE",
                      "payload": {
                        "playerId": 6,
                        "targetNodeId": "S11"
                      }
                    }
                  ],
                  "nodes": [
                    {
                      "nodeId": "S10",
                      "guard": {
                        "active": true,
                        "ownerTeamId": "RED",
                        "defense": 3,
                        "maxDefense": 7,
                        "ageRound": 40
                      }
                    },
                    {"nodeId": "S11"}
                  ],
                  "players": [
                    {
                      "playerId": 6,
                      "camp": "RED",
                      "state": "IDLE",
                      "currentNodeId": "S11",
                      "freshness": 78.0,
                      "verified": false,
                      "goodFruit": 4,
                      "taskScore": 120,
                      "guardActionPoint": 4,
                      "resources": {}
                    },
                    {
                      "playerId": 20,
                      "camp": "BLUE",
                      "state": "MOVING",
                      "currentNodeId": "S09",
                      "nextNodeId": "S10",
                      "freshness": 74.0,
                      "verified": false,
                      "taskScore": 30,
                      "resources": {}
                    }
                  ]
                }
                """)));

        JSONObject action = firstAction(root);
        assertThat(action.getString("action")).isEqualTo("MOVE");
        assertThat(action.getString("targetNodeId")).isEqualTo("S12");
    }

    @Test
    void stillSetsS11GuardWhenOpponentIsAlreadyCrossingFromS10() {
        YhChampionPlannerAgent agent = new YhChampionPlannerAgent(6);
        agent.onStart(json("""
                {
                  "map": {
                    "edges": [
                      {"fromNode": "S10", "toNode": "S11", "distance": 8, "bidirectional": true},
                      {"fromNode": "S11", "toNode": "S12", "distance": 8, "bidirectional": true},
                      {"fromNode": "S12", "toNode": "S13", "distance": 8, "bidirectional": true},
                      {"fromNode": "S13", "toNode": "S14", "distance": 8, "bidirectional": true}
                    ]
                  }
                }
                """));

        JSONObject root = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-s11-close-opponent-guard",
                  "round": 340,
                  "events": [
                    {
                      "type": "PROCESS_COMPLETE",
                      "payload": {
                        "playerId": 6,
                        "targetNodeId": "S11"
                      }
                    }
                  ],
                  "nodes": [
                    {
                      "nodeId": "S10",
                      "guard": {
                        "active": true,
                        "ownerTeamId": "RED",
                        "defense": 3,
                        "maxDefense": 7,
                        "ageRound": 40
                      }
                    },
                    {"nodeId": "S11"}
                  ],
                  "players": [
                    {
                      "playerId": 6,
                      "camp": "RED",
                      "state": "IDLE",
                      "currentNodeId": "S11",
                      "freshness": 78.0,
                      "verified": false,
                      "goodFruit": 4,
                      "taskScore": 120,
                      "guardActionPoint": 4,
                      "resources": {}
                    },
                    {
                      "playerId": 20,
                      "camp": "BLUE",
                      "state": "MOVING",
                      "currentNodeId": "S10",
                      "nextNodeId": "S11",
                      "freshness": 74.0,
                      "verified": false,
                      "taskScore": 90,
                      "resources": {}
                    }
                  ]
                }
                """)));

        JSONObject action = firstAction(root);
        assertThat(action.getString("action")).isEqualTo("SET_GUARD");
        assertThat(action.getString("targetNodeId")).isEqualTo("S11");
    }

    @Test
    void usesRushSpeedAtChampionPregateThreshold() {
        YhChampionPlannerAgent agent = new YhChampionPlannerAgent(6);
        agent.onStart(json("""
                {
                  "map": {
                    "edges": [
                      {"fromNode": "S09", "toNode": "S14", "distance": 24, "bidirectional": true}
                    ]
                  }
                }
                """));

        JSONObject root = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-pregate-rush",
                  "round": 185,
                  "phase": "RUSH",
                  "players": [
                    {
                      "playerId": 6,
                      "camp": "BLUE",
                      "state": "IDLE",
                      "currentNodeId": "S09",
                      "freshness": 85.0,
                      "verified": false,
                      "goodFruit": 3,
                      "rushTacticUsedCount": 0,
                      "resources": {}
                    }
                  ]
                }
                """)));

        assertThat(firstAction(root).getString("action")).isEqualTo("RUSH_SPEED");
    }

    @Test
    void usesIceBoxBeforeFreshnessDropsIntoDangerZone() {
        YhChampionPlannerAgent agent = new YhChampionPlannerAgent(6);
        agent.onStart(json("""
                {
                  "map": {
                    "edges": [
                      {"fromNode": "S09", "toNode": "S14", "distance": 20, "bidirectional": true}
                    ]
                  }
                }
                """));

        JSONObject root = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-early-icebox",
                  "round": 160,
                  "players": [
                    {
                      "playerId": 6,
                      "camp": "RED",
                      "state": "IDLE",
                      "currentNodeId": "S09",
                      "freshness": 79.0,
                      "verified": false,
                      "resources": {"ICE_BOX": 1}
                    }
                  ]
                }
                """)));

        JSONObject action = firstAction(root);
        assertThat(action.getString("action")).isEqualTo("USE_RESOURCE");
        assertThat(action.getString("resourceType")).isEqualTo("ICE_BOX");
    }

    @Test
    void usesIceBoxAtGateBeforeFinalLegWhenFreshnessIsNearDangerZone() {
        YhChampionPlannerAgent agent = new YhChampionPlannerAgent(6);
        agent.onStart(json("""
                {
                  "map": {
                    "edges": [
                      {"fromNode": "S14", "toNode": "S15", "distance": 10, "bidirectional": true}
                    ]
                  }
                }
                """));

        JSONObject root = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-gate-icebox",
                  "round": 455,
                  "players": [
                    {
                      "playerId": 6,
                      "camp": "BLUE",
                      "state": "IDLE",
                      "currentNodeId": "S14",
                      "freshness": 80.2,
                      "verified": true,
                      "resources": {"ICE_BOX": 1},
                      "rushTacticUsedCount": 1
                    }
                  ]
                }
                """)));

        JSONObject action = firstAction(root);
        assertThat(action.getString("action")).isEqualTo("USE_RESOURCE");
        assertThat(action.getString("resourceType")).isEqualTo("ICE_BOX");
    }

    @Test
    void usesIceBoxAtVerifiedGateWhenFinalLegWouldDropFreshnessBelowScoreBand() {
        YhChampionPlannerAgent agent = new YhChampionPlannerAgent(6);
        agent.onStart(json("""
                {
                  "map": {
                    "edges": [
                      {"fromNode": "S14", "toNode": "S15", "distance": 10, "bidirectional": true}
                    ]
                  }
                }
                """));

        JSONObject root = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-gate-icebox-scoreband",
                  "round": 454,
                  "players": [
                    {
                      "playerId": 6,
                      "camp": "RED",
                      "state": "IDLE",
                      "currentNodeId": "S14",
                      "freshness": 80.64,
                      "verified": true,
                      "resources": {"ICE_BOX": 1},
                      "rushTacticUsedCount": 1
                    }
                  ]
                }
                """)));

        JSONObject action = firstAction(root);
        assertThat(action.getString("action")).isEqualTo("USE_RESOURCE");
        assertThat(action.getString("resourceType")).isEqualTo("ICE_BOX");
    }

    @Test
    void processesRequiredStationBeforeLeavingAndMovesAfterCompletion() {
        YhChampionPlannerAgent agent = new YhChampionPlannerAgent(6);
        agent.onStart(json("""
                {
                  "map": {
                    "edges": [
                      {"fromNode": "S02", "toNode": "S03", "distance": 5, "bidirectional": true},
                      {"fromNode": "S03", "toNode": "S14", "distance": 8, "bidirectional": true}
                    ]
                  }
                }
                """));

        JSONObject first = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-process-required",
                  "round": 20,
                  "players": [
                    {
                      "playerId": 6,
                      "camp": "RED",
                      "state": "IDLE",
                      "currentNodeId": "S02",
                      "freshness": 97.0,
                      "verified": false,
                      "resources": {}
                    }
                  ]
                }
                """)));

        assertThat(firstAction(first).getString("action")).isEqualTo("PROCESS");
        assertThat(firstAction(first).getString("targetNodeId")).isEqualTo("S02");

        JSONObject second = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-process-required",
                  "round": 26,
                  "events": [
                    {
                      "type": "PROCESS_COMPLETE",
                      "payload": {
                        "playerId": 6,
                        "targetNodeId": "S02"
                      }
                    }
                  ],
                  "players": [
                    {
                      "playerId": 6,
                      "camp": "RED",
                      "state": "IDLE",
                      "currentNodeId": "S02",
                      "freshness": 96.7,
                      "verified": false,
                      "resources": {}
                    }
                  ]
                }
                """)));

        assertThat(firstAction(second).getString("action")).isEqualTo("MOVE");
        assertThat(firstAction(second).getString("targetNodeId")).isEqualTo("S03");
    }

    @Test
    void claimsCurrentStationTaskBeforeRequiredProcessAtSameNode() {
        YhChampionPlannerAgent agent = new YhChampionPlannerAgent(6);
        agent.onStart(json("""
                {
                  "map": {
                    "edges": [
                      {"fromNode": "S11", "toNode": "S14", "distance": 8, "bidirectional": true}
                    ]
                  }
                }
                """));

        JSONObject root = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-station-task-before-process",
                  "round": 360,
                  "tasks": [
                    {
                      "taskId": "T_006",
                      "nodeId": "S11",
                      "active": true,
                      "completed": false,
                      "failed": false,
                      "processType": "PASS_NODE",
                      "score": 25,
                      "processRound": 2,
                      "expireRound": 420
                    }
                  ],
                  "players": [
                    {
                      "playerId": 6,
                      "camp": "BLUE",
                      "state": "IDLE",
                      "currentNodeId": "S11",
                      "freshness": 84.0,
                      "verified": false,
                      "resources": {}
                    }
                  ]
                }
                """)));

        JSONObject action = firstAction(root);
        assertThat(action.getString("action")).isEqualTo("CLAIM_TASK");
        assertThat(action.getString("taskId")).isEqualTo("T_006");
    }

    @Test
    void retriesProcessAsSoonAsBusyOwnerRemainingWindowEnds() {
        YhChampionPlannerAgent agent = new YhChampionPlannerAgent(201000);
        agent.onStart(json("""
                {
                  "map": {
                    "edges": [
                      {"fromNode": "S02", "toNode": "S03", "distance": 5, "bidirectional": true},
                      {"fromNode": "S03", "toNode": "S14", "distance": 8, "bidirectional": true}
                    ]
                  }
                }
                """));

        JSONObject busy = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-short-process-backoff",
                  "round": 44,
                  "actionResults": [
                    {
                      "playerId": 201000,
                      "accepted": false,
                      "errorCode": "OBJECT_BUSY",
                      "objectKey": "PROCESS:S02:TRANSFER",
                      "ownerPlayerId": 100420,
                      "ownerProcessType": "TRANSFER",
                      "targetNodeId": "S02"
                    }
                  ],
                  "events": [
                    {
                      "type": "PROCESS_PROGRESS",
                      "payload": {
                        "playerId": 100420,
                        "processType": "TRANSFER",
                        "targetNodeId": "S02",
                        "remainingRound": 3
                      }
                    }
                  ],
                  "nodes": [
                    {"nodeId": "S02", "processRound": 4, "processType": "TRANSFER"}
                  ],
                  "players": [
                    {
                      "playerId": 100420,
                      "camp": "RED",
                      "state": "PROCESSING",
                      "currentNodeId": "S02",
                      "freshness": 98.0,
                      "verified": false,
                      "resources": {}
                    },
                    {
                      "playerId": 201000,
                      "camp": "BLUE",
                      "state": "IDLE",
                      "currentNodeId": "S02",
                      "freshness": 98.0,
                      "verified": false,
                      "resources": {}
                    }
                  ]
                }
                """)));
        assertThat(firstAction(busy).getString("action")).isEqualTo("WAIT");

        JSONObject retry = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-short-process-backoff",
                  "round": 47,
                  "nodes": [
                    {"nodeId": "S02", "processRound": 4, "processType": "TRANSFER"}
                  ],
                  "players": [
                    {
                      "playerId": 100420,
                      "camp": "RED",
                      "state": "IDLE",
                      "currentNodeId": "S02",
                      "freshness": 97.7,
                      "verified": false,
                      "resources": {}
                    },
                    {
                      "playerId": 201000,
                      "camp": "BLUE",
                      "state": "IDLE",
                      "currentNodeId": "S02",
                      "freshness": 97.7,
                      "verified": false,
                      "resources": {}
                    }
                  ]
                }
                """)));

        JSONObject action = firstAction(retry);
        assertThat(action.getString("action")).isEqualTo("PROCESS");
        assertThat(action.getString("targetNodeId")).isEqualTo("S02");
    }

    @Test
    void skipsLateS13PermitPickupToProtectGateTempo() {
        YhChampionPlannerAgent agent = new YhChampionPlannerAgent(6);
        agent.onStart(json("""
                {
                  "map": {
                    "edges": [
                      {"fromNode": "S13", "toNode": "S14", "distance": 4, "bidirectional": true}
                    ]
                  }
                }
                """));

        JSONObject root = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-late-permit-skip",
                  "round": 430,
                  "events": [
                    {
                      "type": "PROCESS_COMPLETE",
                      "payload": {
                        "playerId": 6,
                        "targetNodeId": "S13"
                      }
                    }
                  ],
                  "nodes": [
                    {"nodeId": "S13", "resourceStock": {"PASS_TOKEN": 1, "OFFICIAL_PERMIT": 1}}
                  ],
                  "players": [
                    {
                      "playerId": 6,
                      "camp": "BLUE",
                      "state": "IDLE",
                      "currentNodeId": "S13",
                      "freshness": 79.0,
                      "verified": false,
                      "goodFruit": 0,
                      "resources": {}
                    }
                  ]
                }
                """)));

        JSONObject action = firstAction(root);
        assertThat(action.getString("action")).isEqualTo("MOVE");
        assertThat(action.getString("targetNodeId")).isEqualTo("S14");
    }

    @Test
    void retriesProcessWhenNoLowerPriorityOpponentIsVisible() {
        YhChampionPlannerAgent agent = new YhChampionPlannerAgent(7);
        agent.onStart(json("""
                {
                  "map": {
                    "edges": [
                      {"fromNode": "S02", "toNode": "S03", "distance": 5, "bidirectional": true},
                      {"fromNode": "S03", "toNode": "S14", "distance": 8, "bidirectional": true}
                    ]
                  }
                }
                """));

        JSONObject first = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-process-backoff",
                  "round": 30,
                  "messages": [
                    {
                      "type": "ACTION_REJECTED",
                      "payload": {
                        "playerId": 7,
                        "action": "PROCESS",
                        "errorCode": "OBJECT_BUSY",
                        "targetNodeId": "S02"
                      }
                    }
                  ],
                  "players": [
                    {
                      "playerId": 7,
                      "camp": "RED",
                      "state": "IDLE",
                      "currentNodeId": "S02",
                      "freshness": 96.0,
                      "verified": false,
                      "resources": {}
                    }
                  ]
                }
                """)));

        assertThat(firstAction(first).getString("action")).isEqualTo("PROCESS");

        JSONObject second = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-process-backoff",
                  "round": 32,
                  "players": [
                    {
                      "playerId": 7,
                      "camp": "RED",
                      "state": "IDLE",
                      "currentNodeId": "S02",
                      "freshness": 95.8,
                      "verified": false,
                      "resources": {}
                    }
                  ]
                }
                """)));

        assertThat(firstAction(second).getString("action")).isEqualTo("PROCESS");
    }

    @Test
    void ignoresBusyEventsWithoutTargetNodeWhenUpdatingProcessBackoff() {
        YhChampionPlannerAgent agent = new YhChampionPlannerAgent(6);
        agent.onStart(json("""
                {
                  "map": {
                    "edges": [
                      {"fromNode": "S02", "toNode": "S03", "distance": 5, "bidirectional": true}
                    ]
                  }
                }
                """));

        assertThatCode(() -> agent.onInquire(json("""
                {
                  "matchId": "m-yh-busy-missing-target",
                  "round": 370,
                  "messages": [
                    {
                      "type": "ACTION_REJECTED",
                      "payload": {
                        "playerId": 6,
                        "action": "CLAIM_TASK",
                        "errorCode": "OBJECT_BUSY"
                      }
                    }
                  ],
                  "players": [
                    {
                      "playerId": 6,
                      "camp": "RED",
                      "state": "IDLE",
                      "currentNodeId": "S02",
                      "freshness": 94.0,
                      "verified": false,
                      "resources": {}
                    }
                  ]
                }
                """))).doesNotThrowAnyException();
    }

    @Test
    void lowerPlayerIdRetriesProcessImmediatelyAfterRoundConflict() {
        YhChampionPlannerAgent agent = new YhChampionPlannerAgent(10);
        agent.onStart(json("""
                {
                  "map": {
                    "edges": [
                      {"fromNode": "S02", "toNode": "S03", "distance": 5, "bidirectional": true}
                    ]
                  }
                }
                """));

        JSONObject root = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-process-low-id-breaks-lockstep",
                  "round": 11,
                  "actionResults": [
                    {
                      "playerId": 10,
                      "action": "PROCESS",
                      "errorCode": "OBJECT_BUSY",
                      "targetNodeId": "S02"
                    }
                  ],
                  "nodes": [
                    {"nodeId": "S02", "processRound": 4}
                  ],
                  "players": [
                    {
                      "playerId": 10,
                      "camp": "RED",
                      "state": "WAITING",
                      "currentNodeId": "S02",
                      "freshness": 96.0,
                      "verified": false,
                      "resources": {}
                    },
                    {
                      "playerId": 20,
                      "camp": "BLUE",
                      "state": "WAITING",
                      "currentNodeId": "S02",
                      "freshness": 96.0,
                      "verified": false,
                      "resources": {}
                    }
                  ]
                }
                """)));

        assertThat(firstAction(root).getString("action")).isEqualTo("PROCESS");
    }

    @Test
    void higherPlayerIdYieldsFullProcessWindowAfterRoundConflict() {
        YhChampionPlannerAgent agent = new YhChampionPlannerAgent(20);
        agent.onStart(json("""
                {
                  "map": {
                    "edges": [
                      {"fromNode": "S02", "toNode": "S03", "distance": 5, "bidirectional": true}
                    ]
                  }
                }
                """));

        JSONObject first = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-process-high-id-yields",
                  "round": 11,
                  "actionResults": [
                    {
                      "playerId": 20,
                      "action": "PROCESS",
                      "errorCode": "OBJECT_BUSY",
                      "targetNodeId": "S02"
                    }
                  ],
                  "nodes": [
                    {"nodeId": "S02", "processRound": 4}
                  ],
                  "players": [
                    {
                      "playerId": 10,
                      "camp": "RED",
                      "state": "WAITING",
                      "currentNodeId": "S02",
                      "freshness": 96.0,
                      "verified": false,
                      "resources": {}
                    },
                    {
                      "playerId": 20,
                      "camp": "BLUE",
                      "state": "WAITING",
                      "currentNodeId": "S02",
                      "freshness": 96.0,
                      "verified": false,
                      "resources": {}
                    }
                  ]
                }
                """)));

        assertThat(firstAction(first).getString("action")).isEqualTo("WAIT");

        JSONObject second = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-process-high-id-yields",
                  "round": 13,
                  "nodes": [
                    {"nodeId": "S02", "processRound": 4}
                  ],
                  "players": [
                    {
                      "playerId": 10,
                      "camp": "RED",
                      "state": "PROCESSING",
                      "currentNodeId": "S02",
                      "freshness": 95.9,
                      "verified": false,
                      "resources": {}
                    },
                    {
                      "playerId": 20,
                      "camp": "BLUE",
                      "state": "WAITING",
                      "currentNodeId": "S02",
                      "freshness": 95.9,
                      "verified": false,
                      "resources": {}
                    }
                  ]
                }
                """)));

        assertThat(firstAction(second).getString("action")).isEqualTo("WAIT");
    }

    @Test
    void higherPlayerIdRetriesOneTickBeforeCommonSevenBeatProcessMirror() {
        YhChampionPlannerAgent agent = new YhChampionPlannerAgent(600120);
        agent.onStart(json("""
                {
                  "map": {
                    "edges": [
                      {"fromNode": "S02", "toNode": "S03", "distance": 5, "bidirectional": true}
                    ]
                  }
                }
                """));

        json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-process-short-offset",
                  "round": 43,
                  "actionResults": [
                    {
                      "playerId": 600120,
                      "action": "PROCESS",
                      "errorCode": "OBJECT_BUSY",
                      "ownerProcessType": "ROUND_CONFLICT",
                      "targetNodeId": "S02"
                    }
                  ],
                  "nodes": [
                    {"nodeId": "S02", "processRound": 4}
                  ],
                  "players": [
                    {
                      "playerId": 100077,
                      "camp": "RED",
                      "state": "WAITING",
                      "currentNodeId": "S02",
                      "freshness": 97.0,
                      "verified": false,
                      "resources": {}
                    },
                    {
                      "playerId": 600120,
                      "camp": "BLUE",
                      "state": "WAITING",
                      "currentNodeId": "S02",
                      "freshness": 97.0,
                      "verified": false,
                      "resources": {}
                    }
                  ]
                }
                """)));

        JSONObject stillYielding = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-process-short-offset",
                  "round": 47,
                  "nodes": [
                    {"nodeId": "S02", "processRound": 4}
                  ],
                  "players": [
                    {
                      "playerId": 100077,
                      "camp": "RED",
                      "state": "WAITING",
                      "currentNodeId": "S02",
                      "freshness": 96.8,
                      "verified": false,
                      "resources": {}
                    },
                    {
                      "playerId": 600120,
                      "camp": "BLUE",
                      "state": "WAITING",
                      "currentNodeId": "S02",
                      "freshness": 96.8,
                      "verified": false,
                      "resources": {}
                    }
                  ]
                }
                """)));

        assertThat(firstAction(stillYielding).getString("action")).isEqualTo("WAIT");

        JSONObject offsetAttempt = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-process-short-offset",
                  "round": 48,
                  "nodes": [
                    {"nodeId": "S02", "processRound": 4}
                  ],
                  "players": [
                    {
                      "playerId": 100077,
                      "camp": "RED",
                      "state": "WAITING",
                      "currentNodeId": "S02",
                      "freshness": 96.7,
                      "verified": false,
                      "resources": {}
                    },
                    {
                      "playerId": 600120,
                      "camp": "BLUE",
                      "state": "WAITING",
                      "currentNodeId": "S02",
                      "freshness": 96.7,
                      "verified": false,
                      "resources": {}
                    }
                  ]
                }
                """)));

        assertThat(firstAction(offsetAttempt).getString("action")).isEqualTo("PROCESS");
    }

    @Test
    void repeatedRoundConflictUsesLongerOffsetToBreakMirrorProcessLoop() {
        YhChampionPlannerAgent agent = new YhChampionPlannerAgent(600120);
        agent.onStart(json("""
                {
                  "map": {
                    "edges": [
                      {"fromNode": "S02", "toNode": "S04", "distance": 5, "bidirectional": true}
                    ]
                  }
                }
                """));

        json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-process-mirror-break",
                  "round": 43,
                  "actionResults": [
                    {
                      "playerId": 600120,
                      "action": "PROCESS",
                      "errorCode": "OBJECT_BUSY",
                      "ownerProcessType": "ROUND_CONFLICT",
                      "targetNodeId": "S02"
                    }
                  ],
                  "nodes": [
                    {"nodeId": "S02", "processRound": 4}
                  ],
                  "players": [
                    {
                      "playerId": 100077,
                      "camp": "RED",
                      "state": "WAITING",
                      "currentNodeId": "S02",
                      "freshness": 97.0,
                      "verified": false,
                      "resources": {}
                    },
                    {
                      "playerId": 600120,
                      "camp": "BLUE",
                      "state": "WAITING",
                      "currentNodeId": "S02",
                      "freshness": 97.0,
                      "verified": false,
                      "resources": {}
                    }
                  ]
                }
                """)));

        json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-process-mirror-break",
                  "round": 50,
                  "actionResults": [
                    {
                      "playerId": 600120,
                      "action": "PROCESS",
                      "errorCode": "OBJECT_BUSY",
                      "ownerProcessType": "ROUND_CONFLICT",
                      "targetNodeId": "S02"
                    }
                  ],
                  "nodes": [
                    {"nodeId": "S02", "processRound": 4}
                  ],
                  "players": [
                    {
                      "playerId": 100077,
                      "camp": "RED",
                      "state": "WAITING",
                      "currentNodeId": "S02",
                      "freshness": 96.6,
                      "verified": false,
                      "resources": {}
                    },
                    {
                      "playerId": 600120,
                      "camp": "BLUE",
                      "state": "WAITING",
                      "currentNodeId": "S02",
                      "freshness": 96.6,
                      "verified": false,
                      "resources": {}
                    }
                  ]
                }
                """)));

        JSONObject stillYielding = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-process-mirror-break",
                  "round": 56,
                  "nodes": [
                    {"nodeId": "S02", "processRound": 4}
                  ],
                  "players": [
                    {
                      "playerId": 100077,
                      "camp": "RED",
                      "state": "WAITING",
                      "currentNodeId": "S02",
                      "freshness": 96.0,
                      "verified": false,
                      "resources": {}
                    },
                    {
                      "playerId": 600120,
                      "camp": "BLUE",
                      "state": "WAITING",
                      "currentNodeId": "S02",
                      "freshness": 96.0,
                      "verified": false,
                      "resources": {}
                    }
                  ]
                }
                """)));

        assertThat(firstAction(stillYielding).getString("action")).isEqualTo("WAIT");

        JSONObject offsetAttempt = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-process-mirror-break",
                  "round": 61,
                  "nodes": [
                    {"nodeId": "S02", "processRound": 4}
                  ],
                  "players": [
                    {
                      "playerId": 100077,
                      "camp": "RED",
                      "state": "WAITING",
                      "currentNodeId": "S02",
                      "freshness": 95.7,
                      "verified": false,
                      "resources": {}
                    },
                    {
                      "playerId": 600120,
                      "camp": "BLUE",
                      "state": "WAITING",
                      "currentNodeId": "S02",
                      "freshness": 95.7,
                      "verified": false,
                      "resources": {}
                    }
                  ]
                }
                """)));

        assertThat(firstAction(offsetAttempt).getString("action")).isEqualTo("PROCESS");
    }

    @Test
    void claimsDynamicFreshnessTaskTemplateWhenCurrentNodeIsEligible() {
        YhChampionPlannerAgent agent = new YhChampionPlannerAgent(5);
        agent.onStart(json("""
                {
                  "map": {
                    "edges": [
                      {"fromNode": "S09", "toNode": "S14", "distance": 20, "bidirectional": true}
                    ]
                  },
                  "taskTemplates": [
                    {
                      "taskTemplateId": "T03",
                      "processType": "FRESHNESS_CHECK",
                      "candidateNodeIds": ["S09", "S14"],
                      "requiredFreshness": 80.0,
                      "score": 15
                    }
                  ]
                }
                """));

        JSONObject root = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-dynamic-task",
                  "round": 95,
                  "tasks": [
                    {
                      "taskId": "T03_095_01",
                      "taskTemplateId": "T03",
                      "nodeId": "S09",
                      "active": true,
                      "completed": false,
                      "failed": false,
                      "processType": "FRESHNESS_CHECK",
                      "requiredFreshness": 80.0,
                      "score": 15,
                      "processRound": 4,
                      "expireRound": 180
                    }
                  ],
                  "players": [
                    {
                      "playerId": 5,
                      "camp": "BLUE",
                      "state": "IDLE",
                      "currentNodeId": "S09",
                      "freshness": 86.0,
                      "verified": false,
                      "resources": {}
                    }
                  ]
                }
                """)));

        JSONObject action = firstAction(root);
        assertThat(action.getString("action")).isEqualTo("CLAIM_TASK");
        assertThat(action.getString("taskId")).isEqualTo("T03_095_01");
    }

    @Test
    void movesTowardHighValueDynamicTaskWhenDetourIsAffordable() {
        YhChampionPlannerAgent agent = new YhChampionPlannerAgent(5);
        agent.onStart(json("""
                {
                  "map": {
                    "edges": [
                      {"fromNode": "S01", "toNode": "S02", "distance": 8, "bidirectional": true},
                      {"fromNode": "S02", "toNode": "S14", "distance": 8, "bidirectional": true},
                      {"fromNode": "S01", "toNode": "S07", "distance": 9, "bidirectional": true},
                      {"fromNode": "S07", "toNode": "S14", "distance": 9, "bidirectional": true}
                    ]
                  }
                }
                """));

        JSONObject root = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-task-detour",
                  "round": 50,
                  "tasks": [
                    {
                      "taskId": "T10_050_01",
                      "taskTemplateId": "T10",
                      "nodeId": "S07",
                      "active": true,
                      "completed": false,
                      "failed": false,
                      "processType": "DELIVER_FRESHNESS_CHECK",
                      "requiredFreshness": 80.0,
                      "score": 30,
                      "processRound": 2,
                      "expireRound": 120
                    }
                  ],
                  "players": [
                    {
                      "playerId": 5,
                      "camp": "BLUE",
                      "state": "IDLE",
                      "currentNodeId": "S01",
                      "freshness": 92.0,
                      "verified": false,
                      "resources": {}
                    }
                  ]
                }
                """)));

        JSONObject action = firstAction(root);
        assertThat(action.getString("action")).isEqualTo("MOVE");
        assertThat(action.getString("targetNodeId")).isEqualTo("S07");
    }

    @Test
    void pressesMediumTaskDetourWhenTaskScoreIsStillBelowDeliveryDiscountThreshold() {
        YhChampionPlannerAgent agent = new YhChampionPlannerAgent(5);
        agent.onStart(json("""
                {
                  "map": {
                    "edges": [
                      {"fromNode": "S01", "toNode": "S02", "distance": 8, "bidirectional": true},
                      {"fromNode": "S02", "toNode": "S14", "distance": 8, "bidirectional": true},
                      {"fromNode": "S01", "toNode": "S07", "distance": 10, "bidirectional": true},
                      {"fromNode": "S07", "toNode": "S14", "distance": 10, "bidirectional": true}
                    ]
                  }
                }
                """));

        JSONObject root = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-medium-task-detour",
                  "round": 70,
                  "tasks": [
                    {
                      "taskId": "T02_070_01",
                      "nodeId": "S07",
                      "active": true,
                      "completed": false,
                      "failed": false,
                      "processType": "STATION_PROCESS",
                      "score": 20,
                      "processRound": 4,
                      "expireRound": 180
                    }
                  ],
                  "players": [
                    {
                      "playerId": 5,
                      "camp": "BLUE",
                      "state": "IDLE",
                      "currentNodeId": "S01",
                      "freshness": 94.0,
                      "verified": false,
                      "score": 0,
                      "resources": {}
                    }
                  ]
                }
                """)));

        JSONObject action = firstAction(root);
        assertThat(action.getString("action")).isEqualTo("MOVE");
        assertThat(action.getString("targetNodeId")).isEqualTo("S07");
    }

    @Test
    void chasesTaskDetourWhenItCrossesSixtyTaskScoreMilestone() {
        YhChampionPlannerAgent agent = new YhChampionPlannerAgent(5);
        agent.onStart(json("""
                {
                  "map": {
                    "edges": [
                      {"fromNode": "S01", "toNode": "S14", "distance": 32, "bidirectional": true},
                      {"fromNode": "S01", "toNode": "S07", "distance": 12, "bidirectional": true},
                      {"fromNode": "S07", "toNode": "S14", "distance": 27, "bidirectional": true}
                    ]
                  }
                }
                """));

        JSONObject root = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-milestone-task-detour",
                  "round": 150,
                  "tasks": [
                    {
                      "taskId": "T_MILESTONE_060",
                      "nodeId": "S07",
                      "active": true,
                      "completed": false,
                      "failed": false,
                      "processType": "STATION_PROCESS",
                      "score": 15,
                      "processRound": 3,
                      "expireRound": 260
                    }
                  ],
                  "players": [
                    {
                      "playerId": 5,
                      "camp": "BLUE",
                      "state": "IDLE",
                      "currentNodeId": "S01",
                      "freshness": 94.0,
                      "verified": false,
                      "taskScore": 50,
                      "score": 0,
                      "resources": {}
                    }
                  ]
                }
                """)));

        JSONObject action = firstAction(root);
        assertThat(action.getString("action")).isEqualTo("MOVE");
        assertThat(action.getString("targetNodeId")).isEqualTo("S07");
    }

    @Test
    void processesS02BeforeOpeningTaskBecauseTransferIsRequired() {
        YhChampionPlannerAgent agent = new YhChampionPlannerAgent(5);
        agent.onStart(json("""
                {
                  "map": {
                    "edges": [
                      {"fromNode": "S02", "toNode": "S14", "distance": 14, "bidirectional": true},
                      {"fromNode": "S02", "toNode": "S03", "distance": 4, "bidirectional": true},
                      {"fromNode": "S03", "toNode": "S14", "distance": 8, "bidirectional": true}
                    ]
                  }
                }
                """));

        JSONObject root = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-s02-process-required-before-task",
                  "round": 43,
                  "tasks": [
                    {
                      "taskId": "T_EARLY_S03",
                      "nodeId": "S03",
                      "active": true,
                      "completed": false,
                      "failed": false,
                      "processType": "PASS_NODE",
                      "score": 30,
                      "processRound": 3,
                      "expireRound": 221
                    }
                  ],
                  "players": [
                    {
                      "playerId": 5,
                      "camp": "RED",
                      "state": "IDLE",
                      "currentNodeId": "S02",
                      "freshness": 96.0,
                      "verified": false,
                      "taskScore": 0,
                      "score": 0,
                      "resources": {}
                    }
                  ]
                }
                """)));

        JSONObject action = firstAction(root);
        assertThat(action.getString("action")).isEqualTo("PROCESS");
        assertThat(action.getString("targetNodeId")).isEqualTo("S02");
    }

    @Test
    void rushesOpeningS03FoundationTaskEvenWhenNormalDetourBudgetWouldRejectIt() {
        YhChampionPlannerAgent agent = new YhChampionPlannerAgent(5);
        agent.onStart(json("""
                {
                  "map": {
                    "edges": [
                      {"fromNode": "S02", "toNode": "S03", "distance": 42, "bidirectional": true},
                      {"fromNode": "S02", "toNode": "S14", "distance": 96, "bidirectional": true},
                      {"fromNode": "S03", "toNode": "S14", "distance": 84, "bidirectional": true}
                    ]
                  }
                }
                """));

        JSONObject root = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-opening-s03-real-cost",
                  "round": 51,
                  "events": [
                    {
                      "type": "PROCESS_COMPLETE",
                      "payload": {
                        "playerId": 5,
                        "targetNodeId": "S02"
                      }
                    }
                  ],
                  "tasks": [
                    {
                      "taskId": "T_OPENING_S03",
                      "nodeId": "S03",
                      "active": true,
                      "completed": false,
                      "failed": false,
                      "processType": "PASS_NODE",
                      "score": 30,
                      "processRound": 3,
                      "expireRound": 221
                    }
                  ],
                  "players": [
                    {
                      "playerId": 5,
                      "camp": "RED",
                      "state": "IDLE",
                      "currentNodeId": "S02",
                      "freshness": 97.6,
                      "verified": false,
                      "taskScore": 0,
                      "score": 0,
                      "resources": {}
                    }
                  ]
                }
                """)));

        JSONObject action = firstAction(root);
        assertThat(action.getString("action")).isEqualTo("MOVE");
        assertThat(action.getString("targetNodeId")).isEqualTo("S03");
    }

    @Test
    void stopsChasingRemoteTaskWhenOpponentAlreadyControlsS09Window() {
        YhChampionPlannerAgent agent = new YhChampionPlannerAgent(5);
        agent.onStart(json("""
                {
                  "map": {
                    "edges": [
                      {"fromNode": "S07", "toNode": "S14", "distance": 28, "bidirectional": true},
                      {"fromNode": "S07", "toNode": "S09", "distance": 12, "bidirectional": true},
                      {"fromNode": "S09", "toNode": "S14", "distance": 18, "bidirectional": true}
                    ]
                  }
                }
                """));

        JSONObject root = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-stop-chasing-s09",
                  "round": 226,
                  "tasks": [
                    {
                      "taskId": "T_005",
                      "nodeId": "S09",
                      "active": true,
                      "completed": false,
                      "failed": false,
                      "processType": "CLAIM_TASK",
                      "requiredResourceTypes": ["FAST_HORSE", "SHORT_HORSE"],
                      "score": 30,
                      "processRound": 3,
                      "expireRound": 320
                    }
                  ],
                  "players": [
                    {
                      "playerId": 5,
                      "camp": "RED",
                      "state": "IDLE",
                      "currentNodeId": "S07",
                      "freshness": 88.0,
                      "verified": false,
                      "taskScore": 30,
                      "resources": {"SHORT_HORSE": 1}
                    },
                    {
                      "playerId": 15,
                      "camp": "BLUE",
                      "state": "IDLE",
                      "currentNodeId": "S09",
                      "freshness": 86.0,
                      "verified": false,
                      "taskScore": 60,
                      "resources": {"FAST_HORSE": 1}
                    }
                  ]
                }
                """)));

        JSONObject action = firstAction(root);
        assertThat(action.getString("action")).isEqualTo("USE_RESOURCE");
        assertThat(action.getString("resourceType")).isEqualTo("SHORT_HORSE");

        JSONObject next = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-stop-chasing-s09",
                  "round": 227,
                  "tasks": [
                    {
                      "taskId": "T_005",
                      "nodeId": "S09",
                      "active": true,
                      "completed": false,
                      "failed": false,
                      "processType": "CLAIM_TASK",
                      "requiredResourceTypes": ["FAST_HORSE", "SHORT_HORSE"],
                      "score": 30,
                      "processRound": 3,
                      "expireRound": 320
                    }
                  ],
                  "players": [
                    {
                      "playerId": 5,
                      "camp": "RED",
                      "state": "IDLE",
                      "currentNodeId": "S07",
                      "freshness": 87.9,
                      "verified": false,
                      "taskScore": 30,
                      "resources": {}
                    },
                    {
                      "playerId": 15,
                      "camp": "BLUE",
                      "state": "IDLE",
                      "currentNodeId": "S09",
                      "freshness": 85.9,
                      "verified": false,
                      "taskScore": 60,
                      "resources": {"FAST_HORSE": 1}
                    }
                  ]
                }
                """)));

        JSONObject nextAction = firstAction(next);
        assertThat(nextAction.getString("action")).isEqualTo("MOVE");
        assertThat(nextAction.getString("targetNodeId")).isEqualTo("S14");
    }

    @Test
    void skipsS07IceBoxWhenMidgameS09TaskWindowIsOpen() {
        YhChampionPlannerAgent agent = new YhChampionPlannerAgent(5);
        agent.onStart(json("""
                {
                  "map": {
                    "edges": [
                      {"fromNode": "S07", "toNode": "S14", "distance": 28, "bidirectional": true},
                      {"fromNode": "S07", "toNode": "S09", "distance": 12, "bidirectional": true},
                      {"fromNode": "S09", "toNode": "S14", "distance": 18, "bidirectional": true}
                    ]
                  }
                }
                """));

        JSONObject root = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-skip-icebox-for-s09-task",
                  "round": 165,
                  "nodes": [
                    {"nodeId": "S07", "resourceStock": {"ICE_BOX": 1}},
                    {"nodeId": "S09", "resourceStock": {}}
                  ],
                  "tasks": [
                    {
                      "taskId": "T_005",
                      "nodeId": "S09",
                      "active": true,
                      "completed": false,
                      "failed": false,
                      "processType": "CLAIM_TASK",
                      "score": 30,
                      "processRound": 3,
                      "expireRound": 320
                    }
                  ],
                  "players": [
                    {
                      "playerId": 5,
                      "camp": "RED",
                      "state": "IDLE",
                      "currentNodeId": "S07",
                      "freshness": 89.5,
                      "verified": false,
                      "taskScore": 30,
                      "resources": {}
                    }
                  ]
                }
                """)));

        JSONObject action = firstAction(root);
        assertThat(action.getString("action")).isEqualTo("MOVE");
        assertThat(action.getString("targetNodeId")).isEqualTo("S09");
    }

    @Test
    void usesS04ShortHorseBeforeS05TaskWhenTaskDoesNotRequireFuel() {
        YhChampionPlannerAgent agent = new YhChampionPlannerAgent(5);
        agent.onStart(json("""
                {
                  "map": {
                    "edges": [
                      {"fromNode": "S04", "toNode": "S05", "distance": 55, "bidirectional": true}
                    ]
                  }
                }
                """));

        JSONObject root = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-s04-short-horse-tempo",
                  "round": 88,
                  "events": [
                    {
                      "type": "PROCESS_COMPLETE",
                      "payload": {
                        "playerId": 5,
                        "targetNodeId": "S04"
                      }
                    }
                  ],
                  "tasks": [
                    {
                      "taskId": "T_002",
                      "nodeId": "S05",
                      "active": true,
                      "completed": false,
                      "failed": false,
                      "processType": "CLAIM_TASK",
                      "score": 30,
                      "processRound": 4,
                      "expireRound": 221
                    }
                  ],
                  "players": [
                    {
                      "playerId": 5,
                      "camp": "RED",
                      "state": "IDLE",
                      "currentNodeId": "S04",
                      "freshness": 94.8,
                      "verified": false,
                      "taskScore": 0,
                      "resources": {"SHORT_HORSE": 1}
                    },
                    {
                      "playerId": 15,
                      "camp": "BLUE",
                      "state": "MOVING",
                      "currentNodeId": "S03",
                      "nextNodeId": "S07",
                      "edgeProgressMs": 6000,
                      "edgeTotalMs": 74000,
                      "freshness": 94.5,
                      "verified": false,
                      "taskScore": 30,
                      "resources": {}
                    }
                  ]
                }
                """)));

        JSONObject action = firstAction(root);
        assertThat(action.getString("action")).isEqualTo("USE_RESOURCE");
        assertThat(action.getString("resourceType")).isEqualTo("SHORT_HORSE");
    }

    @Test
    void skipsS09ResourceAndTaskWhenOpponentIsClearlyArrivingFirst() {
        YhChampionPlannerAgent agent = new YhChampionPlannerAgent(5);
        agent.onStart(json("""
                {
                  "map": {
                    "edges": [
                      {"fromNode": "S05", "toNode": "S14", "distance": 12, "bidirectional": true},
                      {"fromNode": "S05", "toNode": "S09", "distance": 6, "bidirectional": true},
                      {"fromNode": "S09", "toNode": "S14", "distance": 6, "bidirectional": true},
                      {"fromNode": "S07", "toNode": "S09", "distance": 12, "bidirectional": true}
                    ]
                  }
                }
                """));

        JSONObject root = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-stop-late-s09-resource",
                  "round": 166,
                  "events": [
                    {
                      "type": "PROCESS_COMPLETE",
                      "payload": {
                        "playerId": 5,
                        "targetNodeId": "S05"
                      }
                    }
                  ],
                  "nodes": [
                    {"nodeId": "S05", "resourceStock": {}},
                    {"nodeId": "S09", "resourceStock": {"FAST_HORSE": 1}},
                    {"nodeId": "S14", "resourceStock": {}}
                  ],
                  "tasks": [
                    {
                      "taskId": "T_005",
                      "nodeId": "S09",
                      "active": true,
                      "completed": false,
                      "failed": false,
                      "processType": "CLAIM_TASK",
                      "requiredResourceTypes": ["FAST_HORSE", "SHORT_HORSE"],
                      "score": 30,
                      "processRound": 3,
                      "expireRound": 320
                    }
                  ],
                  "players": [
                    {
                      "playerId": 5,
                      "camp": "RED",
                      "state": "IDLE",
                      "currentNodeId": "S05",
                      "freshness": 91.2,
                      "verified": false,
                      "taskScore": 30,
                      "resources": {"SHORT_HORSE": 1}
                    },
                    {
                      "playerId": 15,
                      "camp": "BLUE",
                      "state": "MOVING",
                      "currentNodeId": "S07",
                      "nextNodeId": "S09",
                      "edgeProgressMs": 1000,
                      "edgeTotalMs": 2000,
                      "freshness": 88.0,
                      "verified": false,
                      "taskScore": 60,
                      "resources": {}
                    }
                  ]
                }
                """)));

        JSONObject action = firstAction(root);
        assertThat(action.getString("action")).isEqualTo("USE_RESOURCE");
        assertThat(action.getString("resourceType")).isEqualTo("SHORT_HORSE");

        JSONObject next = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-stop-late-s09-resource",
                  "round": 167,
                  "nodes": [
                    {"nodeId": "S05", "resourceStock": {}},
                    {"nodeId": "S09", "resourceStock": {"FAST_HORSE": 1}},
                    {"nodeId": "S14", "resourceStock": {}}
                  ],
                  "tasks": [
                    {
                      "taskId": "T_005",
                      "nodeId": "S09",
                      "active": true,
                      "completed": false,
                      "failed": false,
                      "processType": "CLAIM_TASK",
                      "score": 30,
                      "processRound": 3,
                      "expireRound": 320
                    }
                  ],
                  "players": [
                    {
                      "playerId": 5,
                      "camp": "RED",
                      "state": "IDLE",
                      "currentNodeId": "S05",
                      "freshness": 91.1,
                      "verified": false,
                      "taskScore": 30,
                      "resources": {}
                    },
                    {
                      "playerId": 15,
                      "camp": "BLUE",
                      "state": "MOVING",
                      "currentNodeId": "S07",
                      "nextNodeId": "S09",
                      "edgeProgressMs": 2000,
                      "edgeTotalMs": 3000,
                      "freshness": 87.9,
                      "verified": false,
                      "taskScore": 60,
                      "resources": {}
                    }
                  ]
                }
                """)));

        JSONObject nextAction = firstAction(next);
        assertThat(nextAction.getString("action")).isEqualTo("MOVE");
        assertThat(nextAction.getString("targetNodeId")).isEqualTo("S14");
    }

    @Test
    void contestsOpeningS03TaskWhenOpponentIsAlreadyMovingThere() {
        YhChampionPlannerAgent agent = new YhChampionPlannerAgent(6);
        agent.onStart(json("""
                {
                  "map": {
                    "edges": [
                      {"fromNode": "S02", "toNode": "S03", "routeType": "ROAD", "distance": 25, "bidirectional": true},
                      {"fromNode": "S03", "toNode": "S07", "routeType": "ROAD", "distance": 54, "bidirectional": true},
                      {"fromNode": "S07", "toNode": "S09", "routeType": "ROAD", "distance": 46, "bidirectional": true},
                      {"fromNode": "S09", "toNode": "S14", "routeType": "ROAD", "distance": 40, "bidirectional": true},
                      {"fromNode": "S02", "toNode": "S04", "routeType": "WATER", "distance": 20, "bidirectional": true},
                      {"fromNode": "S04", "toNode": "S05", "routeType": "WATER", "distance": 44, "bidirectional": true},
                      {"fromNode": "S05", "toNode": "S09", "routeType": "WATER", "distance": 48, "bidirectional": true}
                    ]
                  }
                }
                """));

        JSONObject root = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-s03-before-water",
                  "round": 51,
                  "events": [
                    {
                      "type": "PROCESS_COMPLETE",
                      "payload": {
                        "playerId": 6,
                        "targetNodeId": "S02"
                      }
                    }
                  ],
                  "nodes": [
                    {"nodeId": "S03", "resourceStock": {"ICE_BOX": 1, "PASS_TOKEN": 1}},
                    {"nodeId": "S04", "resourceStock": {"SHORT_HORSE": 1, "BOAT_RIGHT": 1}}
                  ],
                  "tasks": [
                    {
                      "taskId": "T_001",
                      "nodeId": "S03",
                      "active": true,
                      "completed": false,
                      "failed": false,
                      "processType": "PASS_NODE",
                      "score": 30,
                      "processRound": 3,
                      "expireRound": 221
                    },
                    {
                      "taskId": "T_002",
                      "nodeId": "S05",
                      "active": true,
                      "completed": false,
                      "failed": false,
                      "processType": "CLAIM_TASK",
                      "score": 30,
                      "processRound": 4,
                      "expireRound": 221
                    }
                  ],
                  "players": [
                    {
                      "playerId": 6,
                      "camp": "RED",
                      "state": "IDLE",
                      "currentNodeId": "S02",
                      "freshness": 97.68,
                      "verified": false,
                      "score": 0,
                      "resources": {}
                    },
                    {
                      "playerId": 16,
                      "camp": "BLUE",
                      "state": "MOVING",
                      "currentNodeId": "S02",
                      "nextNodeId": "S03",
                      "freshness": 96.0,
                      "verified": false,
                      "taskScore": 0,
                      "resources": {}
                    }
                  ]
                }
                """)));

        JSONObject action = firstAction(root);
        assertThat(action.getString("action")).isEqualTo("MOVE");
        assertThat(action.getString("targetNodeId")).isEqualTo("S03");
    }

    @Test
    void treatsEarlyS07TaskAsBreakthroughInsteadOfFollowingWaterResourceLine() {
        YhChampionPlannerAgent agent = new YhChampionPlannerAgent(6);
        agent.onStart(json("""
                {
                  "map": {
                    "edges": [
                      {"fromNode": "S02", "toNode": "S03", "distance": 25, "bidirectional": true},
                      {"fromNode": "S03", "toNode": "S07", "distance": 54, "bidirectional": true},
                      {"fromNode": "S07", "toNode": "S09", "distance": 46, "bidirectional": true},
                      {"fromNode": "S09", "toNode": "S14", "distance": 40, "bidirectional": true},
                      {"fromNode": "S02", "toNode": "S04", "distance": 20, "bidirectional": true},
                      {"fromNode": "S04", "toNode": "S05", "distance": 44, "bidirectional": true},
                      {"fromNode": "S05", "toNode": "S07", "distance": 46, "bidirectional": true},
                      {"fromNode": "S05", "toNode": "S09", "distance": 48, "bidirectional": true}
                    ]
                  }
                }
                """));

        JSONObject root = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-early-s07-breakthrough",
                  "round": 60,
                  "events": [
                    {
                      "type": "PROCESS_COMPLETE",
                      "payload": {
                        "playerId": 6,
                        "targetNodeId": "S02"
                      }
                    }
                  ],
                  "nodes": [
                    {"nodeId": "S04", "resourceStock": {"SHORT_HORSE": 1}}
                  ],
                  "tasks": [
                    {
                      "taskId": "T_002",
                      "nodeId": "S07",
                      "active": true,
                      "completed": false,
                      "failed": false,
                      "processType": "PASS_NODE",
                      "score": 25,
                      "processRound": 2,
                      "expireRound": 221
                    }
                  ],
                  "players": [
                    {
                      "playerId": 6,
                      "camp": "BLUE",
                      "state": "IDLE",
                      "currentNodeId": "S02",
                      "freshness": 97.0,
                      "verified": false,
                      "score": 0,
                      "resources": {}
                    }
                  ]
                }
                """)));

        JSONObject action = firstAction(root);
        assertThat(action.getString("action")).isEqualTo("MOVE");
        assertThat(action.getString("targetNodeId")).isEqualTo("S03");
    }

    @Test
    void switchesToS03RoadTaskWhenOpponentWinsEarlyS04WaterLead() {
        YhChampionPlannerAgent agent = new YhChampionPlannerAgent(1001);
        agent.onStart(json("""
                {
                  "map": {
                    "edges": [
                      {"fromNode": "S02", "toNode": "S03", "distance": 25, "bidirectional": true},
                      {"fromNode": "S03", "toNode": "S07", "distance": 54, "bidirectional": true},
                      {"fromNode": "S07", "toNode": "S09", "distance": 46, "bidirectional": true},
                      {"fromNode": "S09", "toNode": "S14", "distance": 40, "bidirectional": true},
                      {"fromNode": "S02", "toNode": "S04", "distance": 20, "bidirectional": true},
                      {"fromNode": "S04", "toNode": "S05", "distance": 44, "bidirectional": true},
                      {"fromNode": "S05", "toNode": "S09", "distance": 48, "bidirectional": true}
                    ]
                  }
                }
                """));

        JSONObject root = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-lost-s04-lead",
                  "round": 51,
                  "events": [
                    {
                      "type": "PROCESS_COMPLETE",
                      "payload": {
                        "playerId": 1001,
                        "targetNodeId": "S02"
                      }
                    }
                  ],
                  "nodes": [
                    {"nodeId": "S03", "resourceStock": {"ICE_BOX": 1, "PASS_TOKEN": 1}},
                    {"nodeId": "S04", "resourceStock": {"SHORT_HORSE": 1, "BOAT_RIGHT": 1}},
                    {"nodeId": "S05", "resourceStock": {}}
                  ],
                  "tasks": [
                    {
                      "taskId": "T_001",
                      "nodeId": "S03",
                      "active": true,
                      "completed": false,
                      "failed": false,
                      "processType": "PASS_NODE",
                      "score": 30,
                      "processRound": 3,
                      "expireRound": 221
                    },
                    {
                      "taskId": "T_002",
                      "nodeId": "S05",
                      "active": true,
                      "completed": false,
                      "failed": false,
                      "processType": "CLAIM_TASK",
                      "score": 30,
                      "processRound": 4,
                      "expireRound": 221
                    }
                  ],
                  "players": [
                    {
                      "playerId": 1001,
                      "camp": "RED",
                      "state": "IDLE",
                      "currentNodeId": "S02",
                      "freshness": 97.28,
                      "verified": false,
                      "score": 0,
                      "taskScore": 0,
                      "resources": {}
                    },
                    {
                      "playerId": 2002,
                      "camp": "BLUE",
                      "state": "MOVING",
                      "currentNodeId": "S02",
                      "nextNodeId": "S04",
                      "edgeProgressMs": 5000,
                      "edgeTotalMs": 20000,
                      "freshness": 97.26,
                      "verified": false,
                      "score": 0,
                      "taskScore": 0,
                      "resources": {}
                    }
                  ]
                }
                """)));

        JSONObject action = firstAction(root);
        assertThat(action.getString("action")).isEqualTo("MOVE");
        assertThat(action.getString("targetNodeId")).isEqualTo("S03");
    }

    @Test
    void contestsOpeningS04WaterTaskWhenOpponentLeadIsStillSmall() {
        YhChampionPlannerAgent agent = new YhChampionPlannerAgent(1001);
        agent.onStart(json("""
                {
                  "map": {
                    "edges": [
                      {"fromNode": "S02", "toNode": "S03", "distance": 25, "bidirectional": true},
                      {"fromNode": "S03", "toNode": "S07", "distance": 54, "bidirectional": true},
                      {"fromNode": "S07", "toNode": "S09", "distance": 46, "bidirectional": true},
                      {"fromNode": "S09", "toNode": "S14", "distance": 40, "bidirectional": true},
                      {"fromNode": "S02", "toNode": "S04", "distance": 20, "bidirectional": true},
                      {"fromNode": "S04", "toNode": "S05", "distance": 44, "bidirectional": true},
                      {"fromNode": "S05", "toNode": "S09", "distance": 48, "bidirectional": true}
                    ]
                  }
                }
                """));

        JSONObject root = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-contest-s04-water",
                  "round": 51,
                  "events": [
                    {
                      "type": "PROCESS_COMPLETE",
                      "payload": {
                        "playerId": 1001,
                        "targetNodeId": "S02"
                      }
                    }
                  ],
                  "nodes": [
                    {"nodeId": "S03", "resourceStock": {"ICE_BOX": 1, "PASS_TOKEN": 1}},
                    {"nodeId": "S04", "resourceStock": {"SHORT_HORSE": 1, "BOAT_RIGHT": 1}},
                    {"nodeId": "S05", "resourceStock": {}}
                  ],
                  "tasks": [
                    {
                      "taskId": "T_001",
                      "nodeId": "S03",
                      "active": true,
                      "completed": false,
                      "failed": false,
                      "processType": "PASS_NODE",
                      "score": 30,
                      "processRound": 3,
                      "expireRound": 221
                    },
                    {
                      "taskId": "T_002",
                      "nodeId": "S04",
                      "active": true,
                      "completed": false,
                      "failed": false,
                      "processType": "CLAIM_TASK",
                      "routeBucket": "WATER",
                      "score": 30,
                      "processRound": 4,
                      "expireRound": 221
                    }
                  ],
                  "players": [
                    {
                      "playerId": 1001,
                      "camp": "RED",
                      "state": "IDLE",
                      "currentNodeId": "S02",
                      "freshness": 97.28,
                      "verified": false,
                      "score": 0,
                      "taskScore": 0,
                      "resources": {}
                    },
                    {
                      "playerId": 2002,
                      "camp": "BLUE",
                      "state": "MOVING",
                      "currentNodeId": "S02",
                      "nextNodeId": "S04",
                      "edgeProgressMs": 2000,
                      "edgeTotalMs": 20000,
                      "freshness": 97.26,
                      "verified": false,
                      "score": 0,
                      "taskScore": 0,
                      "resources": {}
                    }
                  ]
                }
                """)));

        JSONObject action = firstAction(root);
        assertThat(action.getString("action")).isEqualTo("MOVE");
        assertThat(action.getString("targetNodeId")).isEqualTo("S04");
    }

    @Test
    void keepsXianGongOnThirdDockWindowWhenFuelCardsAreUnavailable() {
        YhChampionPlannerAgent agent = new YhChampionPlannerAgent(1001);
        agent.onStart(json("{\"map\":{\"edges\":[]}}"));

        JSONObject root = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-dock-window-third-card",
                  "round": 82,
                  "activeContests": [
                    {
                      "contestId": "C_079_001",
                      "contestType": "DOCK",
                      "redPlayerId": 1001,
                      "bluePlayerId": 2002,
                      "roundIndex": 3,
                      "deadlineRound": 82,
                      "resolved": false
                    }
                  ],
                  "players": [
                    {
                      "playerId": 1001,
                      "camp": "RED",
                      "state": "CONTESTING",
                      "currentNodeId": "S04",
                      "freshness": 95.58,
                      "verified": false,
                      "goodFruit": 98,
                      "guardActionPoint": 3,
                      "resources": {}
                    }
                  ]
                }
                """)));

        JSONObject windowAction = root.getJSONObject("msg_data").getJSONArray("actions").getJSONObject(1);
        assertThat(windowAction.getString("action")).isEqualTo("WINDOW_CARD");
        assertThat(windowAction.getString("contestId")).isEqualTo("C_079_001");
        assertThat(windowAction.getString("card")).isEqualTo("XIAN_GONG");
    }

    @Test
    void doesNotDispatchDuplicateSquadClearToSameObstacle() {
        YhChampionPlannerAgent agent = new YhChampionPlannerAgent(5);
        agent.onStart(json("""
                {
                  "map": {
                    "edges": [
                      {"fromNode": "S01", "toNode": "S02", "distance": 4, "bidirectional": true},
                      {"fromNode": "S02", "toNode": "S11", "distance": 8, "bidirectional": true},
                      {"fromNode": "S11", "toNode": "S14", "distance": 8, "bidirectional": true}
                    ]
                  }
                }
                """));

        JSONObject first = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-squad-dedup",
                  "round": 1,
                  "nodes": [
                    {"nodeId": "S11", "hasObstacle": true}
                  ],
                  "players": [
                    {
                      "playerId": 5,
                      "camp": "BLUE",
                      "state": "IDLE",
                      "currentNodeId": "S01",
                      "freshness": 100.0,
                      "verified": false,
                      "squadAvailable": 8,
                      "resources": {}
                    }
                  ]
                }
                """)));

        assertThat(first.getJSONObject("msg_data").getJSONArray("actions")).hasSize(2);
        assertThat(first.getJSONObject("msg_data").getJSONArray("actions").getJSONObject(1).getString("action"))
                .isEqualTo("SQUAD_CLEAR");

        JSONObject second = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-squad-dedup",
                  "round": 2,
                  "nodes": [
                    {"nodeId": "S11", "hasObstacle": true}
                  ],
                  "players": [
                    {
                      "playerId": 5,
                      "camp": "BLUE",
                      "state": "MOVING",
                      "currentNodeId": "S01",
                      "nextNodeId": "S02",
                      "freshness": 99.9,
                      "verified": false,
                      "squadAvailable": 6,
                      "resources": {}
                    }
                  ]
                }
                """)));

        assertThat(second.getJSONObject("msg_data").getJSONArray("actions")).hasSize(1);
        assertThat(firstAction(second).getString("action")).isEqualTo("MOVE");
    }

    @Test
    void forcedPassesAfterMoveBlockedByGuardEvenWhenGuardViewIsMissing() {
        YhChampionPlannerAgent agent = new YhChampionPlannerAgent(5);
        agent.onStart(json("""
                {
                  "map": {
                    "edges": [
                      {"fromNode": "S10", "toNode": "S11", "distance": 8, "bidirectional": true},
                      {"fromNode": "S11", "toNode": "S14", "distance": 8, "bidirectional": true}
                    ]
                  }
                }
                """));

        JSONObject root = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-guard-memory",
                  "round": 166,
                  "events": [
                    {
                      "type": "ACTION_REJECTED",
                      "payload": {
                        "playerId": 5,
                        "action": "MOVE",
                        "errorCode": "MOVE_BLOCKED_BY_GUARD",
                        "targetNodeId": "S11"
                      }
                    }
                  ],
                  "nodes": [
                    {"nodeId": "S11"}
                  ],
                  "players": [
                    {
                      "playerId": 5,
                      "camp": "BLUE",
                      "state": "IDLE",
                      "currentNodeId": "S10",
                      "freshness": 82.0,
                      "verified": false,
                      "resources": {}
                    }
                  ]
                }
                """)));

        JSONObject action = firstAction(root);
        assertThat(action.getString("action")).isEqualTo("FORCED_PASS");
        assertThat(action.getString("targetNodeId")).isEqualTo("S11");
    }

    @Test
    void forcedPassesGuardBlockedMoveEvenWhenServerStillReportsMoving() {
        YhChampionPlannerAgent agent = new YhChampionPlannerAgent(5);
        agent.onStart(json("""
                {
                  "map": {
                    "edges": [
                      {"fromNode": "S09", "toNode": "S11", "distance": 12, "bidirectional": true},
                      {"fromNode": "S11", "toNode": "S14", "distance": 8, "bidirectional": true}
                    ]
                  }
                }
                """));

        JSONObject root = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-moving-guard-memory",
                  "round": 432,
                  "events": [
                    {
                      "type": "ACTION_REJECTED",
                      "payload": {
                        "playerId": 5,
                        "action": "MOVE",
                        "errorCode": "MOVE_BLOCKED_BY_GUARD",
                        "targetNodeId": "S11"
                      }
                    }
                  ],
                  "nodes": [
                    {"nodeId": "S11"}
                  ],
                  "players": [
                    {
                      "playerId": 5,
                      "camp": "BLUE",
                      "state": "MOVING",
                      "currentNodeId": "S09",
                      "nextNodeId": "S11",
                      "freshness": 74.0,
                      "verified": false,
                      "resources": {}
                    }
                  ]
                }
                """)));

        JSONObject action = firstAction(root);
        assertThat(action.getString("action")).isEqualTo("FORCED_PASS");
        assertThat(action.getString("targetNodeId")).isEqualTo("S11");
    }

    @Test
    void infersGuardBlockedNodeFromNextNodeWhenServerOmitsRejectedTarget() {
        YhChampionPlannerAgent agent = new YhChampionPlannerAgent(5);
        agent.onStart(json("""
                {
                  "map": {
                    "edges": [
                      {"fromNode": "S09", "toNode": "S11", "distance": 12, "bidirectional": true},
                      {"fromNode": "S11", "toNode": "S14", "distance": 8, "bidirectional": true}
                    ]
                  }
                }
                """));

        JSONObject root = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-moving-guard-missing-target",
                  "round": 432,
                  "events": [
                    {
                      "type": "ACTION_REJECTED",
                      "payload": {
                        "playerId": 5,
                        "errorCode": "MOVE_BLOCKED_BY_GUARD"
                      }
                    }
                  ],
                  "nodes": [
                    {"nodeId": "S11", "guard": {"active": true, "defense": 2, "ownerTeamId": "RED"}}
                  ],
                  "players": [
                    {
                      "playerId": 5,
                      "camp": "BLUE",
                      "state": "MOVING",
                      "currentNodeId": "S09",
                      "nextNodeId": "S11",
                      "freshness": 74.0,
                      "goodFruit": 1,
                      "badFruit": 0,
                      "verified": false,
                      "resources": {}
                    }
                  ]
                }
                """)));

        JSONObject action = firstAction(root);
        assertThat(action.getString("action")).isEqualTo("BREAK_GUARD");
        assertThat(action.getString("targetNodeId")).isEqualTo("S11");
    }

    @Test
    void resumesMoveAfterSuccessfulGuardBreakClearsBlockedMemory() {
        YhChampionPlannerAgent agent = new YhChampionPlannerAgent(5);
        agent.onStart(json("""
                {
                  "map": {
                    "edges": [
                      {"fromNode": "S09", "toNode": "S11", "distance": 12, "bidirectional": true},
                      {"fromNode": "S11", "toNode": "S14", "distance": 8, "bidirectional": true}
                    ]
                  }
                }
                """));

        JSONObject first = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-clear-guard-memory",
                  "round": 339,
                  "events": [
                    {
                      "type": "ACTION_REJECTED",
                      "payload": {
                        "playerId": 5,
                        "errorCode": "MOVE_BLOCKED_BY_GUARD"
                      }
                    }
                  ],
                  "nodes": [
                    {"nodeId": "S11", "guard": {"active": true, "defense": 6, "ownerTeamId": "RED"}}
                  ],
                  "players": [
                    {
                      "playerId": 5,
                      "camp": "BLUE",
                      "state": "MOVING",
                      "currentNodeId": "S09",
                      "nextNodeId": "S11",
                      "freshness": 77.0,
                      "goodFruit": 2,
                      "badFruit": 1,
                      "verified": false,
                      "resources": {}
                    }
                  ]
                }
                """)));

        assertThat(firstAction(first).getString("action")).isEqualTo("BREAK_GUARD");

        JSONObject second = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-clear-guard-memory",
                  "round": 340,
                  "events": [
                    {
                      "type": "GUARD_BREAK",
                      "payload": {
                        "playerId": 5,
                        "targetNodeId": "S11",
                        "result": "SUCCESS"
                      }
                    }
                  ],
                  "nodes": [
                    {"nodeId": "S11", "guard": {"active": false, "defense": 0, "ownerTeamId": "RED"}}
                  ],
                  "players": [
                    {
                      "playerId": 5,
                      "camp": "BLUE",
                      "state": "MOVING",
                      "currentNodeId": "S09",
                      "nextNodeId": "S11",
                      "freshness": 76.9,
                      "goodFruit": 0,
                      "badFruit": 0,
                      "verified": false,
                      "resources": {}
                    }
                  ]
                }
                """)));

        JSONObject action = firstAction(second);
        assertThat(action.getString("action")).isEqualTo("MOVE");
        assertThat(action.getString("targetNodeId")).isEqualTo("S11");
    }

    @Test
    void chipsTerminalApproachGuardBeforeForcedPassWhenOneRestCycleCanClearIt() {
        YhChampionPlannerAgent agent = new YhChampionPlannerAgent(5);
        agent.onStart(json("""
                {
                  "map": {
                    "edges": [
                      {"fromNode": "S12", "toNode": "S13", "distance": 5, "bidirectional": true},
                      {"fromNode": "S13", "toNode": "S14", "distance": 4, "bidirectional": true},
                      {"fromNode": "S14", "toNode": "S15", "distance": 3, "bidirectional": true}
                    ]
                  }
                }
                """));

        JSONObject root = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-chip-s13-guard",
                  "round": 415,
                  "events": [
                    {
                      "type": "ACTION_REJECTED",
                      "payload": {
                        "playerId": 5,
                        "action": "MOVE",
                        "errorCode": "MOVE_BLOCKED_BY_GUARD",
                        "targetNodeId": "S13"
                      }
                    }
                  ],
                  "nodes": [
                    {
                      "nodeId": "S13",
                      "guard": {
                        "active": true,
                        "defense": 6,
                        "ownerTeamId": "RED"
                      }
                    }
                  ],
                  "players": [
                    {
                      "playerId": 5,
                      "camp": "BLUE",
                      "state": "MOVING",
                      "currentNodeId": "S12",
                      "nextNodeId": "S13",
                      "freshness": 82.0,
                      "goodFruit": 90,
                      "badFruit": 0,
                      "verified": false,
                      "resources": {}
                    }
                  ]
                }
                """)));

        JSONObject action = firstAction(root);
        assertThat(action.getString("action")).isEqualTo("BREAK_GUARD");
        assertThat(action.getString("targetNodeId")).isEqualTo("S13");
        assertThat(action.getIntValue("goodFruit")).isEqualTo(2);
        assertThat(action.getIntValue("badFruit")).isZero();
    }

    @Test
    void usesServerBadFruitAttackValueWhenChoosingBreakGuard() {
        YhChampionPlannerAgent agent = new YhChampionPlannerAgent(5);
        agent.onStart(json("""
                {
                  "map": {
                    "edges": [
                      {"fromNode": "S10", "toNode": "S11", "distance": 8, "bidirectional": true},
                      {"fromNode": "S11", "toNode": "S14", "distance": 8, "bidirectional": true}
                    ]
                  }
                }
                """));

        JSONObject root = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-badfruit-attack",
                  "round": 166,
                  "nodes": [
                    {
                      "nodeId": "S11",
                      "guard": {
                        "active": true,
                        "defense": 4,
                        "ownerTeamId": "RED"
                      }
                    }
                  ],
                  "players": [
                    {
                      "playerId": 5,
                      "camp": "BLUE",
                      "state": "IDLE",
                      "currentNodeId": "S10",
                      "freshness": 82.0,
                      "verified": false,
                      "goodFruit": 0,
                      "badFruit": 1,
                      "resources": {}
                    }
                  ]
                }
                """)));

        assertThat(firstAction(root).getString("action")).isEqualTo("FORCED_PASS");
    }

    @Test
    void deliveredStateAlwaysRiskGatesToSingleWaitAction() {
        YhChampionPlannerAgent agent = new YhChampionPlannerAgent(5);
        agent.onStart(json("{\"map\":{\"edges\":[]}}"));

        JSONObject root = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-delivered",
                  "round": 501,
                  "phase": "RUSH",
                  "activeContests": [
                    {"contestId": "C1", "playerId": 5, "resolved": false}
                  ],
                  "players": [
                    {
                      "playerId": 5,
                      "camp": "BLUE",
                      "state": "DELIVERED",
                      "currentNodeId": "S15",
                      "verified": true,
                      "delivered": true,
                      "goodFruit": 90,
                      "resources": {"PASS_TOKEN": 1}
                    }
                  ]
                }
                """)));

        assertThat(root.getJSONObject("msg_data").getJSONArray("actions")).hasSize(1);
        assertThat(firstAction(root).getString("action")).isEqualTo("WAIT");
    }

    @Test
    void unverifiedPlayerAtTerminalReturnsToGateInsteadOfClaimingTerminalOpportunity() {
        YhChampionPlannerAgent agent = new YhChampionPlannerAgent(5);
        agent.onStart(json("""
                {
                  "map": {
                    "edges": [
                      {"fromNode": "S14", "toNode": "S15", "distance": 2, "bidirectional": true}
                    ]
                  }
                }
                """));

        JSONObject root = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-s15-risk-gate",
                  "round": 120,
                  "nodes": [
                    {"nodeId": "S15", "resourceStock": {"FAST_HORSE": 1}}
                  ],
                  "tasks": [
                    {
                      "taskId": "T10_120_01",
                      "nodeId": "S15",
                      "active": true,
                      "processType": "DELIVER_FRESHNESS_CHECK",
                      "score": 40,
                      "processRound": 1,
                      "expireRound": 200
                    }
                  ],
                  "players": [
                    {
                      "playerId": 5,
                      "camp": "BLUE",
                      "state": "IDLE",
                      "currentNodeId": "S15",
                      "freshness": 95.0,
                      "verified": false,
                      "resources": {}
                    }
                  ]
                }
                """)));

        JSONObject action = firstAction(root);
        assertThat(action.getString("action")).isEqualTo("MOVE");
        assertThat(action.getString("targetNodeId")).isEqualTo("S14");
    }

    @Test
    void usesRushSpeedBeforeGateWhenRushPhaseAndGateDistanceIsLong() {
        YhChampionPlannerAgent agent = new YhChampionPlannerAgent(5);
        agent.onStart(json("""
                {
                  "map": {
                    "edges": [
                      {"fromNode": "S09", "toNode": "S14", "distance": 50, "bidirectional": true}
                    ]
                  }
                }
                """));

        JSONObject root = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-pregate-rush",
                  "round": 185,
                  "phase": "RUSH",
                  "players": [
                    {
                      "playerId": 5,
                      "camp": "BLUE",
                      "state": "IDLE",
                      "currentNodeId": "S09",
                      "freshness": 85.0,
                      "verified": false,
                      "goodFruit": 3,
                      "rushTacticUsedCount": 0,
                      "resources": {}
                    }
                  ]
                }
                """)));

        assertThat(firstAction(root).getString("action")).isEqualTo("RUSH_SPEED");
    }

    @Test
    void bindsBreakOrderToGateWindowCardDuringRushContest() {
        YhChampionPlannerAgent agent = new YhChampionPlannerAgent(5);
        agent.onStart(json("{\"map\":{\"edges\":[]}}"));

        JSONObject root = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-gate-window-break-order",
                  "round": 421,
                  "phase": "RUSH",
                  "activeContests": [
                    {
                      "contestId": "C_420_002",
                      "contestType": "GATE",
                      "redPlayerId": 5,
                      "bluePlayerId": 6,
                      "roundIndex": 1,
                      "deadlineRound": 422,
                      "resolved": false
                    }
                  ],
                  "players": [
                    {
                      "playerId": 5,
                      "camp": "RED",
                      "state": "IDLE",
                      "currentNodeId": "S14",
                      "freshness": 72.0,
                      "verified": false,
                      "goodFruit": 91,
                      "guardActionPoint": 1,
                      "rushTacticUsedCount": 0,
                      "resources": {}
                    }
                  ]
                }
                """)));

        JSONObject windowAction = root.getJSONObject("msg_data").getJSONArray("actions").getJSONObject(1);
        assertThat(windowAction.getString("action")).isEqualTo("WINDOW_CARD");
        assertThat(windowAction.getString("contestId")).isEqualTo("C_420_002");
        assertThat(windowAction.getString("card")).isEqualTo("BING_ZHENG");
        assertThat(windowAction.getString("rushTactic")).isEqualTo("BREAK_ORDER");
    }

    @Test
    void staggersGateVerifyThenBindsBreakOrderWhenOpponentIsAlsoAtGate() {
        YhChampionPlannerAgent agent = new YhChampionPlannerAgent(5);
        agent.onStart(json("{\"map\":{\"edges\":[]}}"));

        JSONObject first = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-stagger-gate",
                  "round": 412,
                  "phase": "RUSH",
                  "players": [
                    {
                      "playerId": 5,
                      "camp": "RED",
                      "state": "IDLE",
                      "currentNodeId": "S14",
                      "freshness": 69.0,
                      "verified": false,
                      "goodFruit": 91,
                      "rushTacticUsedCount": 0,
                      "resources": {}
                    },
                    {
                      "playerId": 6,
                      "camp": "BLUE",
                      "state": "IDLE",
                      "currentNodeId": "S14",
                      "freshness": 69.0,
                      "verified": false,
                      "goodFruit": 91,
                      "rushTacticUsedCount": 0,
                      "resources": {}
                    }
                  ]
                }
                """)));

        assertThat(firstAction(first).getString("action")).isEqualTo("WAIT");

        JSONObject second = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-stagger-gate",
                  "round": 413,
                  "phase": "RUSH",
                  "players": [
                    {
                      "playerId": 5,
                      "camp": "RED",
                      "state": "IDLE",
                      "currentNodeId": "S14",
                      "freshness": 68.9,
                      "verified": false,
                      "goodFruit": 91,
                      "rushTacticUsedCount": 0,
                      "resources": {}
                    },
                    {
                      "playerId": 6,
                      "camp": "BLUE",
                      "state": "VERIFYING",
                      "currentNodeId": "S14",
                      "freshness": 68.9,
                      "verified": false,
                      "goodFruit": 91,
                      "rushTacticUsedCount": 0,
                      "resources": {}
                    }
                  ]
                }
                """)));

        assertThat(firstAction(second).getString("action")).isEqualTo("VERIFY_GATE");
        assertThat(firstAction(second).getString("rushTactic")).isEqualTo("BREAK_ORDER");
    }

    @Test
    void retriesBreakOrderGateVerifyAfterGateBusyRejectDoesNotConsumeTactic() {
        YhChampionPlannerAgent agent = new YhChampionPlannerAgent(5);
        agent.onStart(json("{\"map\":{\"edges\":[]}}"));

        JSONObject first = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-busy-gate-break-order",
                  "round": 449,
                  "phase": "RUSH",
                  "players": [
                    {
                      "playerId": 5,
                      "camp": "RED",
                      "state": "IDLE",
                      "currentNodeId": "S14",
                      "freshness": 82.0,
                      "verified": false,
                      "goodFruit": 90,
                      "rushTacticUsedCount": 0,
                      "resources": {}
                    },
                    {
                      "playerId": 6,
                      "camp": "BLUE",
                      "state": "VERIFYING",
                      "currentNodeId": "S14",
                      "freshness": 71.0,
                      "verified": false,
                      "resources": {}
                    }
                  ]
                }
                """)));

        assertThat(firstAction(first).getString("action")).isEqualTo("VERIFY_GATE");
        assertThat(firstAction(first).getString("rushTactic")).isEqualTo("BREAK_ORDER");

        JSONObject second = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-busy-gate-break-order",
                  "round": 450,
                  "phase": "RUSH",
                  "actionResults": [
                    {
                      "playerId": 5,
                      "accepted": false,
                      "errorCode": "OBJECT_BUSY",
                      "objectKey": "GATE:S14",
                      "ownerPlayerId": 6,
                      "ownerProcessType": "VERIFY_GATE",
                      "targetNodeId": "S14"
                    }
                  ],
                  "players": [
                    {
                      "playerId": 5,
                      "camp": "RED",
                      "state": "IDLE",
                      "currentNodeId": "S14",
                      "freshness": 81.9,
                      "verified": false,
                      "goodFruit": 90,
                      "rushTacticUsedCount": 0,
                      "resources": {}
                    },
                    {
                      "playerId": 6,
                      "camp": "BLUE",
                      "state": "VERIFYING",
                      "currentNodeId": "S14",
                      "freshness": 70.9,
                      "verified": false,
                      "resources": {}
                    }
                  ]
                }
                """)));

        assertThat(firstAction(second).getString("action")).isEqualTo("VERIFY_GATE");
        assertThat(firstAction(second).getString("rushTactic")).isEqualTo("BREAK_ORDER");
    }

    @Test
    void skipsLateLowValuePalaceTaskWhenOpponentCanForceWindowDraw() {
        YhChampionPlannerAgent agent = new YhChampionPlannerAgent(6);
        agent.onStart(json("""
                {
                  "map": {
                    "edges": [
                      {"fromNode": "S13", "toNode": "S14", "distance": 8, "bidirectional": true}
                    ]
                  }
                }
                """));

        JSONObject root = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-skip-late-palace-draw-task",
                  "round": 408,
                  "tasks": [
                    {
                      "taskId": "T_008",
                      "nodeId": "S13",
                      "active": true,
                      "completed": false,
                      "failed": false,
                      "processType": "CLAIM_TASK",
                      "score": 15,
                      "processRound": 4,
                      "expireRound": 420
                    }
                  ],
                  "players": [
                    {
                      "playerId": 6,
                      "camp": "RED",
                      "state": "IDLE",
                      "currentNodeId": "S13",
                      "freshness": 82.0,
                      "verified": false,
                      "resources": {},
                      "guardActionPoint": 4
                    },
                    {
                      "playerId": 16,
                      "camp": "BLUE",
                      "state": "IDLE",
                      "currentNodeId": "S13",
                      "freshness": 82.0,
                      "verified": false,
                      "resources": {"PASS_TOKEN": 1, "OFFICIAL_PERMIT": 1},
                      "guardActionPoint": 4
                    }
                  ]
                }
                """)));

        JSONObject action = firstAction(root);
        assertThat(action.getString("action")).isEqualTo("PROCESS");
        assertThat(action.getString("targetNodeId")).isEqualTo("S13");
    }

    @Test
    void contestsLatePalaceTaskWhenOpponentAlreadyHasTaskScoreToDenyExtraPoints() {
        YhChampionPlannerAgent agent = new YhChampionPlannerAgent(6);
        agent.onStart(json("""
                {
                  "map": {
                    "edges": [
                      {"fromNode": "S13", "toNode": "S14", "distance": 8, "bidirectional": true}
                    ]
                  }
                }
                """));

        JSONObject root = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-deny-late-palace-task",
                  "round": 408,
                  "tasks": [
                    {
                      "taskId": "T_008",
                      "nodeId": "S13",
                      "active": true,
                      "completed": false,
                      "failed": false,
                      "processType": "CLAIM_TASK",
                      "score": 15,
                      "processRound": 4,
                      "expireRound": 420
                    }
                  ],
                  "players": [
                    {
                      "playerId": 6,
                      "camp": "BLUE",
                      "state": "IDLE",
                      "currentNodeId": "S13",
                      "freshness": 82.0,
                      "verified": false,
                      "taskScore": 45,
                      "resources": {},
                      "guardActionPoint": 4
                    },
                    {
                      "playerId": 16,
                      "camp": "RED",
                      "state": "IDLE",
                      "currentNodeId": "S13",
                      "freshness": 82.0,
                      "verified": false,
                      "taskScore": 15,
                      "resources": {"PASS_TOKEN": 1, "OFFICIAL_PERMIT": 1},
                      "guardActionPoint": 4
                    }
                  ]
                }
                """)));

        JSONObject action = firstAction(root);
        assertThat(action.getString("action")).isEqualTo("CLAIM_TASK");
        assertThat(action.getString("taskId")).isEqualTo("T_008");
    }

    @Test
    void doesNotApplyProcessBackoffAfterTaskBusyRejectionAtProcessStation() {
        YhChampionPlannerAgent agent = new YhChampionPlannerAgent(201000);
        agent.onStart(json("{\"map\":{\"edges\":[]}}"));

        JSONObject root = json(agent.onInquire(json("""
                {
                  "matchId": "m-yh-task-busy-not-process-backoff",
                  "round": 326,
                  "actionResults": [
                    {
                      "playerId": 201000,
                      "accepted": false,
                      "action": "CLAIM_TASK",
                      "errorCode": "OBJECT_BUSY",
                      "objectKey": "TASK:T_007",
                      "ownerPlayerId": 100420,
                      "ownerProcessType": "CLAIM_TASK",
                      "targetNodeId": "S11",
                      "taskId": "T_007"
                    }
                  ],
                  "tasks": [
                    {
                      "taskId": "T_007",
                      "nodeId": "S11",
                      "active": false,
                      "completed": true,
                      "failed": false,
                      "processType": "STATION_PROCESS",
                      "score": 15,
                      "processRound": 5,
                      "expireRound": 420
                    }
                  ],
                  "players": [
                    {
                      "playerId": 201000,
                      "camp": "BLUE",
                      "state": "IDLE",
                      "currentNodeId": "S11",
                      "freshness": 81.2,
                      "verified": false,
                      "taskScore": 45,
                      "resources": {},
                      "guardActionPoint": 4
                    },
                    {
                      "playerId": 100420,
                      "camp": "RED",
                      "state": "MOVING",
                      "currentNodeId": "S11",
                      "nextNodeId": "S12",
                      "freshness": 80.3,
                      "verified": false,
                      "taskScore": 15,
                      "resources": {},
                      "guardActionPoint": 4
                    }
                  ]
                }
                """)));

        JSONObject action = firstAction(root);
        assertThat(action.getString("action")).isEqualTo("PROCESS");
        assertThat(action.getString("targetNodeId")).isEqualTo("S11");
    }

    private static JSONObject json(String text) {
        return JSON.parseObject(text);
    }

    private static JSONObject firstAction(JSONObject root) {
        return root.getJSONObject("msg_data").getJSONArray("actions").getJSONObject(0);
    }
}
