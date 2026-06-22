package com.huawei.game.demo.yhchampion.selfplay;

public record SelfPlaySeriesResult(String seriesName, double demoAAverage, double demoBAverage,
                                   double marginRatioAgainstDemoB, boolean demoAMeetsTenPercentMargin) {
}
