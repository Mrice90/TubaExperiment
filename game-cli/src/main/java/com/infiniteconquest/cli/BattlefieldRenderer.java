package com.infiniteconquest.cli;

import com.infiniteconquest.core.*;

import java.util.List;
import java.util.UUID;

public final class BattlefieldRenderer {
    public String render(GameState state) {
        StringBuilder out = new StringBuilder();
        out.append("Turn ").append(state.turnNumber())
                .append(" | ").append(playerLabel(state.activePlayer()))
                .append(" | GP ").append(state.player(state.activePlayer()).currentGp())
                .append(System.lineSeparator());
        out.append("       x0                 x1                 x2                 x3")
                .append(System.lineSeparator());
        for (int y = 0; y < BoardPosition.HEIGHT; y++) {
            out.append("y").append(y).append(" ");
            for (int x = 0; x < BoardPosition.WIDTH; x++) {
                BoardPosition position = new BoardPosition(x, y);
                out.append(String.format("| %-17s ", cell(state, position)));
            }
            out.append('|').append(System.lineSeparator());
        }
        out.append(renderHand(state, state.activePlayer())).append(System.lineSeparator());
        out.append("Opponent: ").append(state.player(1 - state.activePlayer()).hand().size())
                .append(" cards in hand");
        return out.toString();
    }

    public String renderHand(GameState state, int playerId) {
        StringBuilder out = new StringBuilder(playerLabel(playerId)).append(" hand:")
                .append(System.lineSeparator());
        List<UUID> hand = state.player(playerId).hand();
        if (hand.isEmpty()) out.append("  (empty)").append(System.lineSeparator());
        for (int index = 0; index < hand.size(); index++) {
            out.append("  [").append(index).append("] ")
                    .append(describe(state.card(hand.get(index)).orElseThrow()))
                    .append(System.lineSeparator());
        }
        return out.toString().stripTrailing();
    }

    public String inspectHand(GameState state, int index) {
        UUID id = state.player(state.activePlayer()).hand().get(index);
        return describe(state.card(id).orElseThrow());
    }

    public String inspectCell(GameState state, BoardPosition position) {
        List<UUID> stack = state.board().stackAt(position);
        if (stack.isEmpty()) return position + ": empty";
        StringBuilder out = new StringBuilder(position + " stack (bottom to top):");
        for (int index = 0; index < stack.size(); index++) {
            CardInstance card = state.card(stack.get(index)).orElseThrow();
            out.append(System.lineSeparator()).append("  ").append(index + 1).append(". ")
                    .append(describe(card));
            if (card.definition().isPermanent()) {
                out.append(" — damage ").append(card.damage()).append('/').append(card.definition().hitPoints());
            }
        }
        return out.toString();
    }

    private String describe(CardInstance card) {
        CardDefinition d = card.definition();
        StringBuilder out = new StringBuilder(d.name())
                .append(" — ").append(playerLabel(card.owner()))
                .append(" — ").append(d.type()).append(" — ")
                .append(d.type() == CardType.LAND || d.type() == CardType.STRUCTURE
                        ? "Turn " + Math.max(1, d.cost()) + " ("+(d.developmentGoldCost()==0?"free":d.developmentGoldCost()+" Gold")+")" : d.cost() + " GP");
        if (d.type() == CardType.CHARACTER) {
            out.append(" — A").append(card.effectiveAttack()).append("/D").append(card.defenseRemaining())
                    .append('/').append(card.effectiveDefense())
                    .append("/R").append(d.range()).append("/M").append(d.movement());
        } else if (d.isPermanent()) {
            out.append(" — HP ").append(d.hitPoints());
        }
        if (d.type() == CardType.LAND || d.type() == CardType.STRUCTURE) {
            out.append(" — +").append(d.income()).append(" GP/turn");
            String passive = DevelopmentRules.passiveText(d.developmentPassive());
            if (!passive.isBlank()) out.append(" — ").append(passive);
        }
        if (d.type() == CardType.CAPITAL) out.append(" — Passive: ")
                .append(new CapitalPassiveRules().description(d));
        if (!d.keywords().isEmpty()) out.append(" — ").append(d.keywords());
        return out.toString();
    }

    private String cell(GameState state, BoardPosition position) {
        List<UUID> stack = state.board().stackAt(position);
        if (stack.isEmpty()) return ".";
        CardInstance top = state.card(stack.get(stack.size() - 1)).orElseThrow();
        String type = switch (top.definition().type()) {
            case CHARACTER -> "C";
            case LAND -> "L";
            case STRUCTURE -> "S";
            case CAPITAL -> "K";
            case SPELL -> "?";
        };
        String name = top.definition().name();
        if (name.length() > 8) name = name.substring(0, 8);
        return (top.owner() == 0 ? "YOU " : "BOT ") + type + ":" + name
                + (stack.size() > 1 ? "[" + stack.size() + "]" : "");
    }

    private String playerLabel(int playerId) {
        return playerId == 0 ? "Player 1 (You)" : "Player 2 (Bot)";
    }
}
