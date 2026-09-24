package com.infiniteconquest.core;

import java.util.List;

final class CardAbilityRules {
    List<CardAbility> abilities(CardInstance source, AbilityTrigger trigger) {
        return source.definition().abilities().stream().filter(ability -> ability.trigger() == trigger).toList();
    }

    void resolve(GameState state, CardInstance source, AbilityTrigger trigger) {
        for (CardAbility ability : abilities(source, trigger)) resolve(state, source, ability);
    }

    void resolve(GameState state, CardInstance source, CardAbility ability) {
        switch (ability.effect()) {
            case DRAW_CARD -> state.drawCards(source.owner(), ability.amount());
            case DRAW_CHARACTER -> state.drawCardsOfType(source.owner(), CardType.CHARACTER, ability.amount());
            case DRAW_STRUCTURE -> state.drawCardsOfType(source.owner(), CardType.STRUCTURE, ability.amount());
            case GAIN_GP -> state.player(source.owner()).restoreGp(ability.amount());
            case HEAL_SELF -> {
                if (source.definition().isPermanent()) source.healDamage(ability.amount());
            }
            case HEAL_CAPITAL -> state.battlefieldCards(source.owner()).stream()
                    .filter(card -> card.definition().type() == CardType.CAPITAL)
                    .findFirst().ifPresent(card -> card.healDamage(ability.amount()));
            case BUFF_SELF_ATTACK -> {
                if (source.zone() == Zone.BATTLEFIELD && source.definition().type() == CardType.CHARACTER) {
                    source.addAttackBonus(ability.amount());
                }
            }
            case BUFF_SELF_DEFENSE -> {
                if (source.zone() == Zone.BATTLEFIELD && source.definition().type() == CardType.CHARACTER) {
                    source.addDefenseBonus(ability.amount());
                }
            }
            case DAMAGE_ENEMY_CAPITAL -> state.battlefieldCards(1 - source.owner()).stream()
                    .filter(card -> card.definition().type() == CardType.CAPITAL)
                    .findFirst().ifPresent(card -> {
                        card.addDamage(ability.amount());
                        if (card.damage() >= card.definition().hitPoints()
                                && state.board().positionOf(card.instanceId()).flatMap(state.board()::topAt)
                                .filter(card.instanceId()::equals).isPresent()) state.destroy(card);
                    });
        }
        state.recordCardAbility(source, ability);
    }
}
