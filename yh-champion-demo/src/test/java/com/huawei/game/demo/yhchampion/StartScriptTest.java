package com.huawei.game.demo.yhchampion;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class StartScriptTest {
    @Test
    void startScriptLaunchesPackagedJarWithServerArguments() throws Exception {
        Path projectDir = Path.of(System.getProperty("user.dir"));
        Path script = projectDir.resolve("start.sh");

        assertThat(script).isRegularFile();
        assertThat(Files.isExecutable(script)).isTrue();

        String content = Files.readString(script);
        assertThat(content).contains("yh-champion-demo.jar");
        assertThat(content).contains("\"${PLAYER_ID}\"");
        assertThat(content).contains("\"${HOST}\"");
        assertThat(content).contains("\"${PORT}\"");
        assertThat(content).doesNotContain("--player-id=");
        assertThat(content).doesNotContain("--backend-host=");
        assertThat(content).doesNotContain("--backend-port=");
    }
}
