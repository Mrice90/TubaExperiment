package com.infiniteconquest.core;

public final class DevelopmentRules {
    private DevelopmentRules() { }

    public static int standardGp(CardType type, int developmentTurn) {
        if (type != CardType.LAND && type != CardType.STRUCTURE) return 0;
        int turn = Math.max(1, developmentTurn);
        return Math.min(5, 1 + (turn - 1) / 2);
    }

    public static String passiveText(DevelopmentPassive passive) {
        return switch (passive) {
            case NONE -> "";
            case DRAW_ON_DEPLOY -> "Deploy: draw 1 card.";
            case HEAL_CAPITAL_ON_DEPLOY -> "Deploy: heal your Capital for 3.";
            case SELF_REPAIR -> "Start of your turn: heal 2 damage from this card.";
        };
    }
}
