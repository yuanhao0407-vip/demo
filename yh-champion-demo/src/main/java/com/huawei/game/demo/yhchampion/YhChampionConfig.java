package com.huawei.game.demo.yhchampion;

import com.huawei.game.demo.yhchampion.client.GameClientConfig;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class YhChampionConfig {
    private static final String DEFAULT_BACKEND_HOST = "127.0.0.1";
    private static final int DEFAULT_BACKEND_PORT = 30000;
    private static final int DEFAULT_PLAYER_ID = 6;
    private static final String DEFAULT_PLAYER_NAME = "demo-yh-champion-java";
    private static final String DEFAULT_VERSION = "1.0";

    private YhChampionConfig() {
    }

    public static GameClientConfig defaultConfig() {
        return new GameClientConfig(DEFAULT_BACKEND_HOST, DEFAULT_BACKEND_PORT, DEFAULT_PLAYER_ID,
                DEFAULT_PLAYER_NAME, DEFAULT_VERSION);
    }

    public static GameClientConfig fromArgs(String[] args, Map<String, String> environment) {
        Map<String, String> cli = parseArgs(args);
        String backendHost = value(cli, "backend-host", environment, "LYCHEE_BACKEND_HOST", DEFAULT_BACKEND_HOST);
        int backendPort = intValue(value(cli, "backend-port", environment, "LYCHEE_BACKEND_PORT",
                String.valueOf(DEFAULT_BACKEND_PORT)), "backend-port");
        int playerId = intValue(value(cli, "player-id", environment, "LYCHEE_PLAYER_ID",
                String.valueOf(DEFAULT_PLAYER_ID)), "player-id");
        String playerName = value(cli, "player-name", environment, "LYCHEE_PLAYER_NAME", DEFAULT_PLAYER_NAME);
        String version = value(cli, "version", environment, "LYCHEE_CLIENT_VERSION", DEFAULT_VERSION);
        return new GameClientConfig(backendHost, backendPort, playerId, playerName, version);
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> values = new HashMap<>();
        if (args == null) {
            return values;
        }
        String[] positional = new String[3];
        int positionalCount = 0;
        for (int i = 0; i < args.length; i++) {
            String token = args[i];
            if (token == null) {
                continue;
            }
            if (!token.startsWith("--")) {
                if (positionalCount < positional.length) {
                    positional[positionalCount++] = token;
                }
                continue;
            }
            String withoutPrefix = token.substring(2);
            int equalsIndex = withoutPrefix.indexOf('=');
            if (equalsIndex >= 0) {
                values.put(normalize(withoutPrefix.substring(0, equalsIndex)), withoutPrefix.substring(equalsIndex + 1));
                continue;
            }
            if (i + 1 < args.length && args[i + 1] != null && !args[i + 1].startsWith("--")) {
                values.put(normalize(withoutPrefix), args[++i]);
            }
        }
        if (positionalCount >= 3) {
            values.putIfAbsent("player-id", positional[0]);
            values.putIfAbsent("backend-host", positional[1]);
            values.putIfAbsent("backend-port", positional[2]);
        }
        return values;
    }

    private static String value(Map<String, String> cli, String cliName, Map<String, String> environment,
                                String envName, String defaultValue) {
        String commandLine = cli.get(normalize(cliName));
        if (hasText(commandLine)) {
            return commandLine;
        }
        String env = environment == null ? null : environment.get(envName);
        return hasText(env) ? env : defaultValue;
    }

    private static int intValue(String raw, String name) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid integer for " + name + ": " + raw, ex);
        }
    }

    private static String normalize(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
