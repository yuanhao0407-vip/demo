package com.huawei.game.demo.yhchampion.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.junit.jupiter.api.Test;

class ProtocolMessagesTest {
    @Test
    void readyCarriesMatchIdAndRoundForStrictServerValidation() {
        JSONObject root = JSON.parseObject(ProtocolMessages.ready(6, "match-yh", 1));
        JSONObject data = root.getJSONObject("msg_data");

        assertThat(root.getString("msg_name")).isEqualTo("ready");
        assertThat(data.getIntValue("playerId")).isEqualTo(6);
        assertThat(data.getString("matchId")).isEqualTo("match-yh");
        assertThat(data.getIntValue("round")).isEqualTo(1);
    }
}
