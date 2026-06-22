package com.huawei.game.demo.yhchampion.strategy;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class GrandmasterStrategyTables {
    private static final String DEFAULT_RESOURCE = "/strategy/yh-champion-v1.json";

    private final String version;
    private final Map<String, Integer> routeBiases;
    private final Map<String, Integer> taskBonuses;
    private final EnumMap<StrategyIntent, Integer> intentBiases;
    private final Map<String, Integer> thresholds;
    private final Map<String, List<String>> contestCards;
    private final Set<String> breakOrderContestTypes;

    private GrandmasterStrategyTables(String version, Map<String, Integer> routeBiases,
                                      Map<String, Integer> taskBonuses,
                                      EnumMap<StrategyIntent, Integer> intentBiases,
                                      Map<String, Integer> thresholds,
                                      Map<String, List<String>> contestCards,
                                      Set<String> breakOrderContestTypes) {
        this.version = version;
        this.routeBiases = Map.copyOf(routeBiases);
        this.taskBonuses = Map.copyOf(taskBonuses);
        this.intentBiases = new EnumMap<>(intentBiases);
        this.thresholds = Map.copyOf(thresholds);
        this.contestCards = copyContestCards(contestCards);
        this.breakOrderContestTypes = Set.copyOf(breakOrderContestTypes);
    }

    public static GrandmasterStrategyTables loadDefault() {
        try (InputStream input = GrandmasterStrategyTables.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (input != null) {
                String text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                return fromJson(JSON.parseObject(text));
            }
        } catch (IOException | RuntimeException ignored) {
            // Fall back to the built-in table so the demo stays runnable even if resources are unavailable.
        }
        return builtInDefault();
    }

    private static GrandmasterStrategyTables builtInDefault() {
        Map<String, Integer> routeBiases = new HashMap<>();
        routeBiases.put(routeKey("ROAD", "CLEAR"), 22);
        routeBiases.put(routeKey("ROAD", "HEAVY_RAIN"), 16);
        routeBiases.put(routeKey("WATER", "CLEAR"), 9);
        routeBiases.put(routeKey("WATER", "HEAVY_RAIN"), -32);
        routeBiases.put(routeKey("MOUNTAIN", "CLEAR"), -2);
        routeBiases.put(routeKey("MOUNTAIN", "MOUNTAIN_FOG"), -28);

        Map<String, Integer> taskBonuses = new HashMap<>();
        taskBonuses.put(normalize("STATION_PROCESS"), 10);
        taskBonuses.put(normalize("PASS_NODE"), 9);
        taskBonuses.put(normalize("CLEAR_OBSTACLE"), 8);
        taskBonuses.put(normalize("PERMIT_PROCESS"), 11);
        taskBonuses.put(normalize("FRESHNESS_CHECK"), 22);
        taskBonuses.put(normalize("DELIVER_FRESHNESS_CHECK"), 24);
        taskBonuses.put(normalize("CLAIM_TASK"), 18);
        taskBonuses.put(normalize("WIN_RESOURCE_WINDOW"), 16);
        taskBonuses.put(normalize("WIN_HORSE_WINDOW"), 18);
        taskBonuses.put(normalize("WIN_DOCK_WINDOW"), 14);
        taskBonuses.put(normalize("RUSH_VERIFY_GATE"), 26);

        EnumMap<StrategyIntent, Integer> intentBiases = new EnumMap<>(StrategyIntent.class);
        intentBiases.put(StrategyIntent.FAST_DELIVERY, 22);
        intentBiases.put(StrategyIntent.FRESH_DELIVERY, 20);
        intentBiases.put(StrategyIntent.RESOURCE_CONTROL, 16);
        intentBiases.put(StrategyIntent.TASK_CAP_PUSH, 21);
        intentBiases.put(StrategyIntent.BOUNTY_CHASE, 12);
        intentBiases.put(StrategyIntent.GUARD_PRESSURE, 14);
        intentBiases.put(StrategyIntent.CONTEST_DOMINANCE, 18);
        intentBiases.put(StrategyIntent.RUSH_COMEBACK, 28);
        intentBiases.put(StrategyIntent.LEAD_PROTECT, 30);

        Map<String, Integer> thresholds = new HashMap<>();
        thresholds.put(normalize("preGateRushSpeedMinDistance"), 24);
        thresholds.put(normalize("staggerGateVerifyWithBreakOrder"), 1);

        Map<String, List<String>> contestCards = new HashMap<>();
        contestCards.put(normalize("GATE"), List.of("BING_ZHENG", "XIAN_GONG", "BING_ZHENG"));
        contestCards.put(normalize("RESOURCE"), List.of("YAN_DIE", "QIANG_XING", "BING_ZHENG"));
        contestCards.put(normalize("TASK"), List.of("XIAN_GONG", "BING_ZHENG", "YAN_DIE"));
        contestCards.put(normalize("PASS"), List.of("BING_ZHENG", "YAN_DIE", "QIANG_XING"));
        contestCards.put(normalize("DOCK"), List.of("QIANG_XING", "YAN_DIE", "XIAN_GONG"));

        Set<String> breakOrderContestTypes = Set.of(normalize("GATE"));

        return new GrandmasterStrategyTables("yh-champion-v1", routeBiases, taskBonuses, intentBiases,
                thresholds, contestCards, breakOrderContestTypes);
    }

    static GrandmasterStrategyTables fromJson(JSONObject root) {
        if (root == null) {
            return builtInDefault();
        }

        Map<String, Integer> routeBiases = new HashMap<>();
        JSONObject routeJson = root.getJSONObject("routeBiases");
        if (routeJson != null) {
            for (String key : routeJson.keySet()) {
                String[] parts = key.split("#", 2);
                if (parts.length == 2) {
                    routeBiases.put(routeKey(parts[0], parts[1]), routeJson.getIntValue(key));
                }
            }
        }

        Map<String, Integer> taskBonuses = new HashMap<>();
        JSONObject taskJson = root.getJSONObject("taskBonuses");
        if (taskJson != null) {
            for (String key : taskJson.keySet()) {
                taskBonuses.put(normalize(key), taskJson.getIntValue(key));
            }
        }

        EnumMap<StrategyIntent, Integer> intentBiases = new EnumMap<>(StrategyIntent.class);
        JSONObject intentJson = root.getJSONObject("intentBiases");
        if (intentJson != null) {
            for (String key : intentJson.keySet()) {
                try {
                    intentBiases.put(StrategyIntent.valueOf(normalize(key)), intentJson.getIntValue(key));
                } catch (IllegalArgumentException ignored) {
                    // Unknown future intent names are ignored by this demo version.
                }
            }
        }

        Map<String, Integer> thresholds = new HashMap<>();
        JSONObject thresholdJson = root.getJSONObject("thresholds");
        if (thresholdJson != null) {
            for (String key : thresholdJson.keySet()) {
                thresholds.put(normalize(key), thresholdJson.getIntValue(key));
            }
        }

        Map<String, List<String>> contestCards = new HashMap<>();
        JSONObject contestJson = root.getJSONObject("contestCards");
        if (contestJson != null) {
            for (String key : contestJson.keySet()) {
                JSONArray cardsJson = contestJson.getJSONArray(key);
                List<String> cards = new ArrayList<>();
                if (cardsJson != null) {
                    for (int i = 0; i < cardsJson.size(); i++) {
                        cards.add(normalize(cardsJson.getString(i)));
                    }
                }
                if (!cards.isEmpty()) {
                    contestCards.put(normalize(key), cards);
                }
            }
        }

        boolean hasBreakOrderContestTypes = root.containsKey("breakOrderContestTypes");
        Set<String> breakOrderContestTypes = parseStringSet(root.getJSONArray("breakOrderContestTypes"));

        GrandmasterStrategyTables fallback = builtInDefault();
        return new GrandmasterStrategyTables(
                root.getString("version") == null ? fallback.version() : root.getString("version"),
                routeBiases.isEmpty() ? fallback.routeBiases : routeBiases,
                taskBonuses.isEmpty() ? fallback.taskBonuses : taskBonuses,
                intentBiases.isEmpty() ? fallback.intentBiases : intentBiases,
                thresholds.isEmpty() ? fallback.thresholds : thresholds,
                contestCards.isEmpty() ? fallback.contestCards : contestCards,
                hasBreakOrderContestTypes ? breakOrderContestTypes : fallback.breakOrderContestTypes);
    }

    public String version() {
        return version;
    }

    public int routeBias(String routeType, String weatherType) {
        String normalizedWeather = normalize(weatherType);
        Integer exact = routeBiases.get(routeKey(routeType, normalizedWeather));
        if (exact != null) {
            return exact;
        }
        return routeBiases.getOrDefault(routeKey(routeType, "CLEAR"), 0);
    }

    public int taskProcessTypeBonus(String processType) {
        return taskBonuses.getOrDefault(normalize(processType), 0);
    }

    public int intentBias(StrategyIntent intent) {
        return intent == null ? 0 : intentBiases.getOrDefault(intent, 0);
    }

    public int threshold(String name, int defaultValue) {
        return thresholds.getOrDefault(normalize(name), defaultValue);
    }

    public String contestCard(String contestType, int roundIndex) {
        List<String> cards = contestCards.get(normalize(contestType));
        if (cards == null || cards.isEmpty()) {
            return "";
        }
        int index = Math.max(1, roundIndex) - 1;
        if (index >= cards.size()) {
            index = cards.size() - 1;
        }
        return cards.get(index);
    }

    public boolean shouldBindBreakOrderForContest(String contestType) {
        return breakOrderContestTypes.contains(normalize(contestType));
    }

    private static Map<String, List<String>> copyContestCards(Map<String, List<String>> source) {
        Map<String, List<String>> copied = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : source.entrySet()) {
            copied.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Map.copyOf(copied);
    }

    private static Set<String> parseStringSet(JSONArray values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        java.util.HashSet<String> result = new java.util.HashSet<>();
        for (int i = 0; i < values.size(); i++) {
            String value = normalize(values.getString(i));
            if (!value.isEmpty()) {
                result.add(value);
            }
        }
        return result;
    }

    private static String routeKey(String routeType, String weatherType) {
        return normalize(routeType) + "#" + normalize(weatherType);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
