package com.huawei.game.demo.yhchampion.selfplay;

import java.util.List;

public final class GrandmasterSelfPlayEvaluator {
    private static final double REQUIRED_MARGIN_RATIO = 0.10D;

    public SelfPlaySeriesResult evaluate(String seriesName, List<SelfPlayMatchResult> matches) {
        if (matches == null || matches.isEmpty()) {
            throw new IllegalArgumentException("At least one self-play match result is required.");
        }

        double demoATotal = 0.0D;
        double demoBTotal = 0.0D;
        for (SelfPlayMatchResult match : matches) {
            if (match == null) {
                throw new IllegalArgumentException("Self-play match result must not be null.");
            }
            demoATotal += match.demoAScore();
            demoBTotal += match.demoBScore();
        }

        double demoAAverage = demoATotal / matches.size();
        double demoBAverage = demoBTotal / matches.size();
        double marginRatio = demoBAverage <= 0.0D
                ? Double.POSITIVE_INFINITY
                : (demoAAverage - demoBAverage) / demoBAverage;
        return new SelfPlaySeriesResult(seriesName, demoAAverage, demoBAverage,
                marginRatio, marginRatio >= REQUIRED_MARGIN_RATIO);
    }
}
