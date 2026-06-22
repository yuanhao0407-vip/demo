package com.huawei.game.demo.yhchampion.selfplay;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class GrandmasterSelfPlayEvaluatorTest {
    @Test
    void thirdRoundRequiresDemoATenPercentAverageScoreMargin() {
        GrandmasterSelfPlayEvaluator evaluator = new GrandmasterSelfPlayEvaluator();
        SelfPlaySeriesResult result = evaluator.evaluate("round-3", List.of(
                new SelfPlayMatchResult(900, 800),
                new SelfPlayMatchResult(880, 790),
                new SelfPlayMatchResult(910, 805)
        ));

        assertThat(result.demoAAverage()).isEqualTo(896.6666666666666);
        assertThat(result.demoBAverage()).isEqualTo(798.3333333333334);
        assertThat(result.marginRatioAgainstDemoB()).isGreaterThanOrEqualTo(0.10);
        assertThat(result.demoAMeetsTenPercentMargin()).isTrue();
    }
}
