package com.infiniteconquest.core;

import com.infiniteconquest.data.Keyword;

import java.util.*;

public final class CapitalPassiveRules {
    private static final Map<String, CapitalPassive> BY_CAPITAL = Map.ofEntries(
            Map.entry("zeus_capital_olympus_citadel", CapitalPassive.OLYMPIAN_MUSTER),
            Map.entry("zeus_capital_keraunos_spire", CapitalPassive.STORM_TITHE),
            Map.entry("zeus_capital_cloud_throne", CapitalPassive.CLOUDWARD),
            Map.entry("poseidon_capital_atlantis_nexus", CapitalPassive.TIDAL_RENEWAL),
            Map.entry("poseidon_capital_trident_bastion", CapitalPassive.TRIDENT_RESTORATION),
            Map.entry("poseidon_capital_abyssal_court", CapitalPassive.DEEP_RESERVES));

    private static final Map<CapitalPassive, String> DESCRIPTIONS = Map.ofEntries(
            Map.entry(CapitalPassive.OLYMPIAN_MUSTER, "Start of your turn: your first Blink Character gains +2 Attack this turn."),
            Map.entry(CapitalPassive.STORM_TITHE, "The first Spell you cast each turn refunds 1 GP."),
            Map.entry(CapitalPassive.CLOUDWARD, "The first Character you Blink each turn gains +2 Defense until your next turn."),
            Map.entry(CapitalPassive.TIDAL_RENEWAL, "Start of your turn: heal 3 damage from your most damaged Land."),
            Map.entry(CapitalPassive.TRIDENT_RESTORATION, "The first Land you play each turn heals your Capital for 2."),
            Map.entry(CapitalPassive.DEEP_RESERVES, "The first Mole you burrow each turn refunds 1 GP."));

    public Optional<CapitalPassive> passiveFor(CardDefinition capital) {
        if (capital.type() != CardType.CAPITAL) return Optional.empty();
        return Optional.ofNullable(BY_CAPITAL.get(capital.id()));
    }

    public String description(CardDefinition capital) {
        return passiveFor(capital).map(DESCRIPTIONS::get).orElse("No passive ability.");
    }

    public int supportedCapitalCount() { return BY_CAPITAL.size(); }

    void onTurnStarted(GameState state, int playerId) {
        CapitalPassive activePassive = passive(state, playerId).orElse(null);
        if (activePassive == null) return;
        switch (activePassive) {
            case OLYMPIAN_MUSTER -> firstBattlefieldCard(state, playerId,
                    card -> card.definition().type() == CardType.CHARACTER && card.definition().hasKeyword(Keyword.BLINK))
                    .ifPresent(card -> { card.addAttackBonus(2); trigger(state, playerId, CapitalPassive.OLYMPIAN_MUSTER); });
            case TIDAL_RENEWAL -> mostDamaged(state, playerId, CardType.LAND).ifPresent(card -> {
                card.healDamage(3); trigger(state, playerId, CapitalPassive.TIDAL_RENEWAL);
            });
            default -> { }
        }
    }

    void onCardPlayed(GameState state, CardInstance card) {
        CapitalPassive passive = passive(state, card.owner()).orElse(null);
        if (passive == null) return;
        if (passive == CapitalPassive.STORM_TITHE && card.definition().type() == CardType.SPELL) refund(state, card.owner(), passive);
        if (passive == CapitalPassive.TRIDENT_RESTORATION && card.definition().type() == CardType.LAND
                && use(state, card.owner(), passive)) {
            capital(state, card.owner()).ifPresent(value -> value.healDamage(2)); emit(state, card.owner(), passive);
        }
    }

    void onBurrowed(GameState state, CardInstance card) {
        if (passive(state, card.owner()).orElse(null) == CapitalPassive.DEEP_RESERVES) refund(state, card.owner(), CapitalPassive.DEEP_RESERVES, 1);
    }

    void onBlinked(GameState state, CardInstance card) {
        if (passive(state, card.owner()).orElse(null) == CapitalPassive.CLOUDWARD
                && use(state, card.owner(), CapitalPassive.CLOUDWARD)) {
            card.addDefenseBonus(2); emit(state, card.owner(), CapitalPassive.CLOUDWARD);
        }
    }

    void onMoved(GameState state, CardInstance card) {
    }

    void beforeAttack(GameState state, CardInstance attacker, CardInstance target) {
    }

    void onCharacterReturnedBySpell(GameState state, int casterId, CardInstance target) {
    }

    void onPermanentDestroyed(GameState state, CardInstance destroyed) {
    }

    private Optional<CapitalPassive> passive(GameState state, int playerId) {
        return capital(state, playerId).flatMap(card -> passiveFor(card.definition()));
    }

    private Optional<CardInstance> capital(GameState state, int playerId) {
        return firstBattlefieldCard(state, playerId, card -> card.definition().type() == CardType.CAPITAL);
    }

    private Optional<CardInstance> firstBattlefieldCard(GameState state, int playerId,
                                                        java.util.function.Predicate<CardInstance> predicate) {
        return state.battlefieldCards(playerId).stream().filter(predicate)
                .min(Comparator.comparing(card -> card.instanceId().toString()));
    }

    private Optional<CardInstance> mostDamaged(GameState state, int playerId, CardType type) {
        return state.battlefieldCards(playerId).stream().filter(card -> card.definition().type() == type && card.damage() > 0)
                .max(Comparator.comparingInt(CardInstance::damage).thenComparing(card -> card.instanceId().toString()));
    }

    private Optional<CardInstance> mostDamagedPermanent(GameState state, int playerId) {
        return state.battlefieldCards(playerId).stream().filter(card -> card.definition().isPermanent() && card.damage() > 0)
                .max(Comparator.comparingInt(CardInstance::damage).thenComparing(card -> card.instanceId().toString()));
    }

    private void refund(GameState state, int playerId, CapitalPassive passive) {
        refund(state, playerId, passive, 1);
    }

    private void refund(GameState state, int playerId, CapitalPassive passive, int amount) {
        if (use(state, playerId, passive)) {
            state.player(playerId).restoreGp(amount); emit(state, playerId, passive);
        }
    }

    private boolean use(GameState state, int playerId, CapitalPassive passive) {
        return state.tryUseCapitalPassive(playerId, passive);
    }

    private void trigger(GameState state, int playerId, CapitalPassive passive) {
        state.markCapitalPassiveUsed(playerId, passive); emit(state, playerId, passive);
    }

    private void emit(GameState state, int playerId, CapitalPassive passive) {
        state.recordCapitalPassive(playerId, passive, DESCRIPTIONS.get(passive));
    }
}
