package com.huawei.game.demo.yhchampion.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.fastjson.JSON;
import org.junit.jupiter.api.Test;

class GrandmasterStrategyTablesTest {
    @Test
    void loadsDemoAStrategyVersionAndWeights() {
        GrandmasterStrategyTables tables = GrandmasterStrategyTables.loadDefault();

        assertThat(tables.version()).isEqualTo("yh-champion-v1");
        assertThat(tables.routeBias("WATER", "HEAVY_RAIN")).isLessThan(tables.routeBias("ROAD", "HEAVY_RAIN"));
        assertThat(tables.taskProcessTypeBonus("FRESHNESS_CHECK")).isPositive();
        assertThat(tables.intentBias(StrategyIntent.LEAD_PROTECT)).isPositive();
        assertThat(tables.threshold("preGateRushSpeedMinDistance", 999)).isEqualTo(24);
        assertThat(tables.contestCard("GATE", 1)).isEqualTo("BING_ZHENG");
        assertThat(tables.contestCard("GATE", 2)).isEqualTo("XIAN_GONG");
        assertThat(tables.shouldBindBreakOrderForContest("GATE")).isTrue();
        assertThat(tables.shouldBindBreakOrderForContest("RESOURCE")).isFalse();
    }

    @Test
    void emptyBreakOrderContestTypesDisablesFallbackBinding() {
        GrandmasterStrategyTables tables = GrandmasterStrategyTables.fromJson(JSON.parseObject("""
                {
                  "version": "no-break-order",
                  "breakOrderContestTypes": []
                }
                """));

        assertThat(tables.shouldBindBreakOrderForContest("GATE")).isFalse();
    }
}
