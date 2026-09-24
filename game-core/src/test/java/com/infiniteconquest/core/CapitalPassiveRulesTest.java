package com.infiniteconquest.core;

import com.infiniteconquest.data.CardCatalog;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class CapitalPassiveRulesTest {
    private final List<CardDefinition> capitals = CardCatalog.loadResource("/cards/faction-capitals.json").definitions();

    @Test
    void allSixCapitalsHaveDifferentImplementedPassives() {
        CapitalPassiveRules rules = new CapitalPassiveRules();
        assertEquals(6, rules.supportedCapitalCount());
        Set<CapitalPassive> passives = new HashSet<>();
        Set<String> descriptions = new HashSet<>();
        for (CardDefinition capital : capitals) {
            passives.add(rules.passiveFor(capital).orElseThrow());
            descriptions.add(rules.description(capital));
        }
        assertEquals(6, passives.size());
        assertEquals(6, descriptions.size());
    }

    @Test
    void stormTitheRefundsOnlyTheFirstSpellEachTurn() {
        GameState state = new GameState(1L);
        state.player(0).restoreGp(1);
        add(state, 0, capital("zeus_capital_keraunos_spire"), Zone.BATTLEFIELD, new BoardPosition(1, 0));
        CardDefinition spellDefinition = new CardDefinition("test_spell", "Test Spell", CardType.SPELL, "ZEUS",
                1, 0, 0, 0, 0, 0, Set.of(), List.of(new SpellEffect(SpellEffectType.BUFF_ATTACK, 1, SpellTarget.FRIENDLY)));
        CardInstance spell = add(state, 0, spellDefinition, Zone.HAND, null);
        CardInstance target = add(state, 0, character("target", 1, 1, 1), Zone.BATTLEFIELD, new BoardPosition(0, 0));

        assertTrue(new GameEngine().apply(state,
                new GameAction.CastSpell(0, spell.instanceId(), target.instanceId(), null)).accepted());
        assertEquals(1, state.player(0).currentGp());
        assertEquals(1, passiveEvents(state, CapitalPassive.STORM_TITHE));
    }

    @Test
    void zeusCapitalBonusesUseTheNewTwoPointBudget() {
        var rules = new CapitalPassiveRules();
        var blink = new CardDefinition("blink", "Blink", CardType.CHARACTER, "ZEUS", 3,
                2, 2, 3, 1, 0, Set.of(com.infiniteconquest.data.Keyword.BLINK));
        GameState muster = new GameState(10L);
        add(muster, 0, capital("zeus_capital_olympus_citadel"), Zone.BATTLEFIELD, new BoardPosition(1, 0));
        var attacker = add(muster, 0, blink, Zone.BATTLEFIELD, new BoardPosition(0, 0));
        rules.onTurnStarted(muster, 0); assertEquals(4, attacker.effectiveAttack());
        GameState cloud = new GameState(11L);
        add(cloud, 0, capital("zeus_capital_cloud_throne"), Zone.BATTLEFIELD, new BoardPosition(1, 0));
        var defender = add(cloud, 0, blink, Zone.BATTLEFIELD, new BoardPosition(0, 0));
        rules.onBlinked(cloud, defender); rules.onBlinked(cloud, defender);
        assertEquals(4, defender.effectiveDefense());
    }

    private CardDefinition capital(String id) {
        return capitals.stream().filter(card -> card.id().equals(id)).findFirst().orElseThrow();
    }

    private CardDefinition character(String id, int attack, int defense, int movement) {
        return new CardDefinition(id, id, CardType.CHARACTER, "TEST", 0, attack, defense, movement, 1);
    }

    private CardInstance add(GameState state, int owner, CardDefinition definition, Zone zone, BoardPosition position) {
        CardInstance card = new CardInstance(UUID.randomUUID(), definition, owner, zone);
        state.register(card);
        if (zone == Zone.HAND) state.player(owner).addToHand(card.instanceId());
        if (position != null) state.board().push(position, card.instanceId());
        return card;
    }

    private long passiveEvents(GameState state, CapitalPassive passive) {
        return state.events().stream().filter(event -> event.type() == GameEvent.Type.CAPITAL_PASSIVE_TRIGGERED)
                .filter(event -> event.detail().startsWith(passive.name())).count();
    }
}
