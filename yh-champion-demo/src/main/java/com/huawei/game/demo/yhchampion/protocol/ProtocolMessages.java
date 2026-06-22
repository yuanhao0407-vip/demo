package com.huawei.game.demo.yhchampion.protocol;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import java.util.List;

public final class ProtocolMessages {
    private ProtocolMessages() {
    }

    public static String registration(int playerId, String playerName, String version) {
        JSONObject data = new JSONObject();
        data.put("playerId", playerId);
        data.put("playerName", playerName);
        data.put("version", version);
        return envelope("registration", data);
    }

    public static String ready(int playerId, String matchId, int round) {
        JSONObject data = new JSONObject();
        data.put("playerId", playerId);
        data.put("matchId", matchId);
        data.put("round", round);
        return envelope("ready", data);
    }

    public static String action(String matchId, int playerId, int round, List<RoleActionCommand> actions) {
        JSONObject data = new JSONObject();
        data.put("matchId", matchId);
        data.put("playerId", playerId);
        data.put("round", round);
        data.put("serverGeneratedMissingAction", false);
        JSONArray actionNodes = new JSONArray();
        if (actions != null) {
            actionNodes.addAll(actions);
        }
        data.put("actions", actionNodes);
        return envelope("action", data);
    }

    private static String envelope(String messageName, JSONObject data) {
        JSONObject root = new JSONObject();
        root.put("msg_name", messageName);
        root.put("msg_data", data);
        return JSON.toJSONString(root, SerializerFeature.DisableCircularReferenceDetect);
    }
}
