package com.huawei.game.demo.yhchampion;

import com.huawei.game.demo.yhchampion.client.GameClientConfig;
import com.huawei.game.demo.yhchampion.client.NettyGameClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class YhChampionClientApplication {
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(YhChampionClientApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.run(args);
    }

    @Bean
    GameClientConfig gameClientConfig(org.springframework.boot.ApplicationArguments arguments) {
        return YhChampionConfig.fromArgs(arguments.getSourceArgs(), System.getenv());
    }

    @Bean
    YhChampionPlannerAgent yhChampionPlannerAgent(GameClientConfig config) {
        return new YhChampionPlannerAgent(config.playerId());
    }

    @Bean
    CommandLineRunner yhRunner(GameClientConfig config, YhChampionPlannerAgent agent) {
        return ignored -> {
            try (NettyGameClient client = new NettyGameClient(config, agent)) {
                client.runUntilClosed();
            }
        };
    }
}
