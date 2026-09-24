package com.infiniteconquest.cli;

import com.infiniteconquest.core.*;

import java.util.Comparator;
import java.util.List;

public final class BotPlayer {
    public static final int BOT_ID = 1;
    private final ActionHints hints = new ActionHints();

    public Decision takeNextAction(GameState state, CommandProcessor commands) {
        return takeNextAction(state, commands, BOT_ID);
    }

    public Decision takeNextAction(GameState state, CommandProcessor commands, int playerId) {
        if (state.activePlayer() != playerId) throw new IllegalStateException("It is not player " + playerId + "'s turn");
        List<String> legal = hints.forActivePlayer(state, new GameEngine());
        String command = legal.stream()
                .max(Comparator.comparingInt((String value) -> score(state, value, playerId))
                        .thenComparing(Comparator.naturalOrder()))
                .orElse("end");
        return new Decision(command, commands.execute(command));
    }

    public Decision react(GameState state, CommandProcessor commands) {
        return react(state, commands, BOT_ID);
    }

    public Decision react(GameState state, CommandProcessor commands, int playerId) {
        if (state.activePlayer() == playerId) return null;
        List<String> legal = hints.spellActionsForPlayer(state, playerId);
        if (legal.isEmpty()) return null;
        String command = legal.stream()
                .max(Comparator.comparingInt((String value) -> score(state, value, playerId))
                        .thenComparing(Comparator.naturalOrder()))
                .orElseThrow();
        return new Decision(command, commands.execute(command));
    }

    private int score(GameState state, String command, int playerId) {
        String[] parts = command.split("\\s+");
        int base = switch (parts[0]) {
            case "cast", "react" -> spellScore(state, parts, playerId);
            case "attack" -> {
                BoardPosition target = new BoardPosition(Integer.parseInt(parts[3]), Integer.parseInt(parts[4]));
                CardInstance card = state.board().topAt(target).flatMap(state::card).orElseThrow();
                yield card.definition().isPermanent() ? 115 : 105;
            }
            case "play" -> {
                int index = Integer.parseInt(parts[1]);
                CardType type = state.card(state.player(playerId).hand().get(index)).orElseThrow().definition().type();
                yield switch (type) {
                    case LAND -> 90;
                    case STRUCTURE -> 85;
                    case CHARACTER -> 80;
                    default -> 0;
                };
            }
            case "burrow" -> 82;
            case "blink" -> 45;
            case "move" -> 35;
            case "activate" -> 75;
            case "end" -> 0;
            default -> 1;
        };
        return base + capitalSynergy(state, playerId, parts[0]);
    }

    private int spellScore(GameState state, String[] parts, int playerId) {
        int handIndex = Integer.parseInt(parts[0].equals("react") ? parts[2] : parts[1]);
        CardInstance spell = state.card(state.player(playerId).hand().get(handIndex)).orElseThrow();
        SpellEffect effect = spell.definition().effects().get(0);
        return switch (effect.type()) {
            case DAMAGE_PERMANENT -> 140 + effect.amount();
            case STRIKE_CHARACTER -> 135 + effect.amount();
            case RETURN_CHARACTER -> 125;
            case HEAL_PERMANENT -> 115 + effect.amount();
            case BUFF_ATTACK -> 105 + effect.amount();
            case BUFF_DEFENSE -> 100 + effect.amount();
            case TELEPORT_CHARACTER -> 60;
        };
    }

    private int capitalSynergy(GameState state, int playerId, String action) {
        CapitalPassive passive = state.capitalPassiveFor(playerId).orElse(null);
        if (passive == null) return 0;
        return switch (passive) {
            case STORM_TITHE -> action.equals("cast") || action.equals("react") ? 8 : 0;
            case TRIDENT_RESTORATION -> action.equals("play") ? 3 : 0;
            case DEEP_RESERVES -> action.equals("burrow") ? 8 : 0;
            case CLOUDWARD -> action.equals("blink") ? 5 : 0;
            default -> 0;
        };
    }

    public record Decision(String command, String result) {}
}
