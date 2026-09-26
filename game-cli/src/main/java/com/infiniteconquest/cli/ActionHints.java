package com.infiniteconquest.cli;

import com.infiniteconquest.core.*;
import com.infiniteconquest.data.Keyword;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ActionHints {
    public List<String> forActivePlayer(GameState state, GameEngine engine) {
        int player = state.activePlayer();
        List<String> hints = new ArrayList<>();
        List<UUID> hand = state.player(player).hand();

        for (int index = 0; index < hand.size(); index++) {
            CardInstance card = state.card(hand.get(index)).orElseThrow();
            if (!state.canPlayDevelopment(player, card.definition().type())) continue;
            boolean development = card.definition().type() == CardType.LAND
                    || card.definition().type() == CardType.STRUCTURE;
            if ((development && card.definition().cost() > state.personalTurnNumber(player))
                    || card.definition().goldCost() > state.player(player).currentGp()) continue;
            for (BoardPosition position : state.board().positions()) {
                if (card.definition().type() == CardType.LAND
                        && GameEngine.legalLandDestination(state, player, position)) {
                    hints.add("play " + index + " " + position.x() + " " + position.y());
                } else if (card.definition().type() == CardType.STRUCTURE && isControlledTopLand(state, player, position)) {
                    hints.add("play " + index + " " + position.x() + " " + position.y());
                } else if (card.definition().type() == CardType.CHARACTER && legalSummonCell(state, player, position)) {
                    hints.add("play " + index + " " + position.x() + " " + position.y());
                }
                if (card.definition().type() == CardType.CHARACTER
                        && card.definition().hasKeyword(Keyword.MOLE)
                        && isControlledTopLand(state, player, position)) {
                    hints.add("burrow " + index + " " + position.x() + " " + position.y());
                }
            }
        }
        hints.addAll(spellActionsForPlayer(state, player));

        for (BoardPosition from : state.board().positions()) {
            var top = state.board().topAt(from);
            if (top.isEmpty()) continue;
            CardInstance card = state.card(top.orElseThrow()).orElseThrow();
            if (card.owner() != player || card.definition().type() != CardType.CHARACTER) continue;
            for (BoardPosition to : engine.legalMovementDestinations(state, card.instanceId())) {
                hints.add("move " + from.x() + " " + from.y() + " " + to.x() + " " + to.y());
            }
            if (card.definition().hasKeyword(Keyword.BLINK) && !card.blinkUsedThisTurn()) {
                for (BoardPosition to : state.board().positions()) if (state.board().isEmpty(to)) {
                    hints.add("blink " + from.x() + " " + from.y() + " " + to.x() + " " + to.y());
                }
            }
            for (BoardPosition to : engine.legalAttackDestinations(state, card.instanceId())) {
                hints.add("attack " + from.x() + " " + from.y() + " " + to.x() + " " + to.y());
            }
        }
        for (BoardPosition position : state.board().positions()) {
            state.board().topAt(position).flatMap(state::card)
                    .filter(card -> card.owner() == player && !card.abilityUsedThisTurn())
                    .filter(card -> card.definition().abilities().stream()
                            .filter(ability -> ability.trigger() == AbilityTrigger.ACTIVATED)
                            .mapToInt(CardAbility::gpCost).sum() <= state.player(player).currentGp())
                    .filter(card -> card.definition().abilities().stream()
                            .anyMatch(ability -> ability.trigger() == AbilityTrigger.ACTIVATED))
                    .filter(card -> aimsAtEnemyCapital(card)
                            ? new LineOfSightRules().hasLineToEnemyCapital(state, card, position)
                            : true)
                    .ifPresent(card -> hints.add("activate " + position.x() + " " + position.y()));
        }
        hints.add("end");
        return List.copyOf(hints);
    }

    /**
     * True when one of the card's activated abilities strikes the enemy
     * Capital — an aimed shot that needs a clear sight line, so the hint list
     * only offers it when cover does not block it.
     */
    private boolean aimsAtEnemyCapital(CardInstance card) {
        return card.definition().abilities().stream()
                .anyMatch(ability -> ability.trigger() == AbilityTrigger.ACTIVATED
                        && ability.effect() == AbilityEffectType.DAMAGE_ENEMY_CAPITAL);
    }

    public List<String> spellActionsForPlayer(GameState state, int player) {
        List<String> result = new ArrayList<>();
        List<UUID> hand = state.player(player).hand();
        String prefix = player == state.activePlayer() ? "cast " : "react " + player + " ";
        for (int index = 0; index < hand.size(); index++) {
            CardInstance spell = state.card(hand.get(index)).orElseThrow();
            if (spell.definition().type() != CardType.SPELL
                    || spell.definition().cost() > state.player(player).currentGp()) continue;
            SpellEffect effect = spell.definition().effects().get(0);
            for (BoardPosition targetPosition : state.board().positions()) {
                var targetId = state.board().topAt(targetPosition);
                if (targetId.isEmpty()) continue;
                CardInstance target = state.card(targetId.orElseThrow()).orElseThrow();
                if (!validSpellTarget(effect, player, target)) continue;
                String base = prefix + index + " " + targetPosition.x() + " " + targetPosition.y();
                if (effect.type() == SpellEffectType.TELEPORT_CHARACTER) {
                    for (BoardPosition destination : state.board().positions()) {
                        if (state.board().isEmpty(destination)) {
                            result.add(base + " " + destination.x() + " " + destination.y());
                        }
                    }
                } else result.add(base);
            }
        }
        return result;
    }

    private boolean validSpellTarget(SpellEffect effect, int player, CardInstance target) {
        if (effect.target() == SpellTarget.FRIENDLY && target.owner() != player) return false;
        if (effect.target() == SpellTarget.ENEMY && target.owner() == player) return false;
        return switch (effect.type()) {
            case STRIKE_CHARACTER, TELEPORT_CHARACTER, RETURN_CHARACTER, BUFF_ATTACK, BUFF_DEFENSE ->
                    target.definition().type() == CardType.CHARACTER;
            case DAMAGE_PERMANENT, HEAL_PERMANENT -> target.definition().isPermanent();
        };
    }

    private boolean isControlledTopLand(GameState state, int player, BoardPosition position) {
        return state.board().topAt(position).flatMap(state::card)
                .filter(card -> card.owner() == player)
                .map(card -> card.definition().type() == CardType.LAND)
                .orElse(false);
    }

    private boolean legalSummonCell(GameState state, int player, BoardPosition destination) {
        boolean onFriendlyStack = !state.board().isEmpty(destination) && state.board().stackAt(destination).stream()
                .map(id -> state.card(id).orElseThrow())
                .allMatch(card -> card.owner() == player);
        boolean besidePermanent = state.board().isEmpty(destination) && state.board().positions().stream()
                .filter(p -> state.rules().geometry().adjacent(destination, p))
                .flatMap(position -> state.board().stackAt(position).stream())
                .map(id -> state.card(id).orElseThrow())
                .anyMatch(card -> card.owner() == player && card.definition().isPermanent());
        return onFriendlyStack || besidePermanent;
    }
}
