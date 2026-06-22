package com.huawei.game.demo.yhchampion.protocol;

public final class RoleActionCommand {
    private final String action;
    private final String targetNodeId;
    private final String resourceType;
    private final String contestId;
    private final String card;
    private final String rushTactic;
    private final String taskId;
    private final Integer extraGoodFruit;
    private final Integer goodFruit;
    private final Integer badFruit;

    private RoleActionCommand(Builder builder) {
        this.action = builder.action;
        this.targetNodeId = builder.targetNodeId;
        this.resourceType = builder.resourceType;
        this.contestId = builder.contestId;
        this.card = builder.card;
        this.rushTactic = builder.rushTactic;
        this.taskId = builder.taskId;
        this.extraGoodFruit = builder.extraGoodFruit;
        this.goodFruit = builder.goodFruit;
        this.badFruit = builder.badFruit;
    }

    public static RoleActionCommand waitAction() {
        return builder("WAIT").build();
    }

    public static RoleActionCommand moveTo(String targetNodeId) {
        return builder("MOVE").targetNodeId(targetNodeId).build();
    }

    public static RoleActionCommand clearObstacle(String targetNodeId) {
        return builder("CLEAR").targetNodeId(targetNodeId).build();
    }

    public static RoleActionCommand verifyGate(String targetNodeId) {
        return builder("VERIFY_GATE").targetNodeId(targetNodeId).build();
    }

    public static RoleActionCommand deliver() {
        return builder("DELIVER").build();
    }

    public static RoleActionCommand claimResource(String targetNodeId, String resourceType) {
        return builder("CLAIM_RESOURCE").targetNodeId(targetNodeId).resourceType(resourceType).build();
    }

    public static RoleActionCommand useResource(String resourceType) {
        return builder("USE_RESOURCE").resourceType(resourceType).build();
    }

    public static RoleActionCommand process(String targetNodeId) {
        return builder("PROCESS").targetNodeId(targetNodeId).build();
    }

    public static RoleActionCommand claimTask(String taskId) {
        return builder("CLAIM_TASK").taskId(taskId).build();
    }

    public static RoleActionCommand setGuard(String targetNodeId, int extraGoodFruit) {
        return builder("SET_GUARD").targetNodeId(targetNodeId).extraGoodFruit(extraGoodFruit).build();
    }

    public static RoleActionCommand breakGuard(String targetNodeId, int goodFruit, int badFruit) {
        return builder("BREAK_GUARD").targetNodeId(targetNodeId).goodFruit(goodFruit).badFruit(badFruit).build();
    }

    public static RoleActionCommand forcedPass(String targetNodeId) {
        return builder("FORCED_PASS").targetNodeId(targetNodeId).build();
    }

    public static RoleActionCommand squadScout(String targetNodeId) {
        return builder("SQUAD_SCOUT").targetNodeId(targetNodeId).build();
    }

    public static RoleActionCommand squadClear(String targetNodeId) {
        return builder("SQUAD_CLEAR").targetNodeId(targetNodeId).build();
    }

    public static RoleActionCommand squadReinforce(String targetNodeId) {
        return builder("SQUAD_REINFORCE").targetNodeId(targetNodeId).build();
    }

    public static RoleActionCommand squadWeaken(String targetNodeId) {
        return builder("SQUAD_WEAKEN").targetNodeId(targetNodeId).build();
    }

    public static RoleActionCommand windowCard(String contestId, String card) {
        return builder("WINDOW_CARD").contestId(contestId).card(card).build();
    }

    public static RoleActionCommand rushSpeed() {
        return builder("RUSH_SPEED").build();
    }

    public static RoleActionCommand rushProtect() {
        return builder("RUSH_PROTECT").build();
    }

    public RoleActionCommand withRushTactic(String rushTactic) {
        return builder(action)
                .targetNodeId(targetNodeId)
                .resourceType(resourceType)
                .contestId(contestId)
                .card(card)
                .rushTactic(rushTactic)
                .taskId(taskId)
                .extraGoodFruit(extraGoodFruit)
                .goodFruit(goodFruit)
                .badFruit(badFruit)
                .build();
    }

    public String getAction() {
        return action;
    }

    public String getTargetNodeId() {
        return targetNodeId;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getContestId() {
        return contestId;
    }

    public String getCard() {
        return card;
    }

    public String getRushTactic() {
        return rushTactic;
    }

    public String getTaskId() {
        return taskId;
    }

    public Integer getExtraGoodFruit() {
        return extraGoodFruit;
    }

    public Integer getGoodFruit() {
        return goodFruit;
    }

    public Integer getBadFruit() {
        return badFruit;
    }

    private static Builder builder(String action) {
        return new Builder(action);
    }

    private static final class Builder {
        private final String action;
        private String targetNodeId;
        private String resourceType;
        private String contestId;
        private String card;
        private String rushTactic;
        private String taskId;
        private Integer extraGoodFruit;
        private Integer goodFruit;
        private Integer badFruit;

        private Builder(String action) {
            this.action = action;
        }

        private Builder targetNodeId(String value) {
            this.targetNodeId = value;
            return this;
        }

        private Builder resourceType(String value) {
            this.resourceType = value;
            return this;
        }

        private Builder contestId(String value) {
            this.contestId = value;
            return this;
        }

        private Builder card(String value) {
            this.card = value;
            return this;
        }

        private Builder rushTactic(String value) {
            this.rushTactic = value;
            return this;
        }

        private Builder taskId(String value) {
            this.taskId = value;
            return this;
        }

        private Builder extraGoodFruit(Integer value) {
            this.extraGoodFruit = value;
            return this;
        }

        private Builder goodFruit(Integer value) {
            this.goodFruit = value;
            return this;
        }

        private Builder badFruit(Integer value) {
            this.badFruit = value;
            return this;
        }

        private RoleActionCommand build() {
            return new RoleActionCommand(this);
        }
    }
}
