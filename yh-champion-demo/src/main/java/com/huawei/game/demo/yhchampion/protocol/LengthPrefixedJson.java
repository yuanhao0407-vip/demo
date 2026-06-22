package com.huawei.game.demo.yhchampion.protocol;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class LengthPrefixedJson {
    private LengthPrefixedJson() {
    }

    public static String frame(String jsonBody) {
        String escapedBody = escapeNonAscii(jsonBody);
        int length = escapedBody.getBytes(StandardCharsets.UTF_8).length;
        return String.format(Locale.ENGLISH, "%05d%s", length, escapedBody);
    }

    static String escapeNonAscii(String input) {
        StringBuilder escaped = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            if (ch <= 127) {
                escaped.append(ch);
                continue;
            }
            String hex = Integer.toHexString(ch);
            escaped.append("\\u");
            for (int pad = hex.length(); pad < 4; pad++) {
                escaped.append('0');
            }
            escaped.append(hex);
        }
        return escaped.toString();
    }
}
