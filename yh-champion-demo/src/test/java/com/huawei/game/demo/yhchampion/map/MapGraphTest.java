package com.huawei.game.demo.yhchampion.map;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.junit.jupiter.api.Test;

class MapGraphTest {
    @Test
    void prefersRoadDetourWhenBranchLogicalDistanceIsShorterButRealTickCostIsHigher() {
        MapGraph graph = MapGraph.fromStart(json("""
                {
                  "map": {
                    "edges": [
                      {"fromNodeId": "S09", "toNodeId": "S10", "routeType": "ROAD", "distance": 40, "bidirectional": true},
                      {"fromNodeId": "S10", "toNodeId": "S11", "routeType": "ROAD", "distance": 36, "bidirectional": true},
                      {"fromNodeId": "S09", "toNodeId": "S11", "routeType": "BRANCH", "distance": 72, "bidirectional": true}
                    ]
                  }
                }
                """));

        assertThat(graph.nextHop("S09", "S11")).contains("S10");
    }

    private static JSONObject json(String raw) {
        return JSON.parseObject(raw);
    }
}
