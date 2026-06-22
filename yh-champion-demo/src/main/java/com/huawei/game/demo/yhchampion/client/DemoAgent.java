package com.huawei.game.demo.yhchampion.client;

import com.alibaba.fastjson.JSONObject;

public interface DemoAgent {
    default void onStart(JSONObject startData) {
    }

    String onInquire(JSONObject inquireData);

    default void onError(JSONObject errorData) {
    }

    default void onOver(JSONObject overData) {
    }
}
