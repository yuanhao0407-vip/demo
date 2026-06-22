package com.huawei.game.demo.yhchampion;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.game.demo.yhchampion.client.GameClientConfig;
import java.util.Map;
import org.junit.jupiter.api.Test;

class YhChampionConfigTest {
    @Test
    void acceptsServerStylePositionalArguments() {
        GameClientConfig config = YhChampionConfig.fromArgs(new String[]{"10042", "10.0.0.8", "31099"}, Map.of());

        assertThat(config.playerId()).isEqualTo(10042);
        assertThat(config.backendHost()).isEqualTo("10.0.0.8");
        assertThat(config.backendPort()).isEqualTo(31099);
    }

    @Test
    void keepsNamedArgumentsForMatrixHarnessCompatibility() {
        GameClientConfig config = YhChampionConfig.fromArgs(new String[]{
                "--backend-host=127.0.0.2",
                "--backend-port=32001",
                "--player-id=77",
                "--player-name=matrix-yh"
        }, Map.of());

        assertThat(config.playerId()).isEqualTo(77);
        assertThat(config.backendHost()).isEqualTo("127.0.0.2");
        assertThat(config.backendPort()).isEqualTo(32001);
        assertThat(config.playerName()).isEqualTo("matrix-yh");
    }
}
