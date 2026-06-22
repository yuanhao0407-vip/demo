package com.huawei.game.demo.yhchampion.client;

public final class GameClientConfig {
    private final String backendHost;
    private final int backendPort;
    private final int playerId;
    private final String playerName;
    private final String version;

    public GameClientConfig(String backendHost, int backendPort, int playerId, String playerName, String version) {
        this.backendHost = backendHost;
        this.backendPort = backendPort;
        this.playerId = playerId;
        this.playerName = playerName;
        this.version = version;
    }

    public String backendHost() {
        return backendHost;
    }

    public int backendPort() {
        return backendPort;
    }

    public int playerId() {
        return playerId;
    }

    public String playerName() {
        return playerName;
    }

    public String version() {
        return version;
    }
}
