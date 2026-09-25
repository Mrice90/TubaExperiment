package com.infiniteconquest.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CardAbilityRulesTest {
    @Test void enterDestroyedPassiveAndPaidAbilitiesResolveFromCardData() {
        GameState state = new GameState(91L);
        CardInstance home = add(state, 0, permanent("home", List.of()), new BoardPosition(0, 0));
        CardInstance enemyCapital = add(state, 1, new CardDefinition("enemy_capital", "Enemy Capital",
                CardType.CAPITAL, "TEST", 0, 0, 0, 0, 0, 20), new BoardPosition(0, 5));
        // The capital win rule ends the game when a player has no Capital, so the
        // fixture needs a home Capital to survive the deathrattle destroy below.
        add(state, 0, new CardDefinition("home_capital", "Home Capital",
                CardType.CAPITAL, "TEST", 0, 0, 0, 0, 0, 20), new BoardPosition(3, 5));

        CardInstance arrival = add(state, 0, permanent("arrival", List.of(
                ability(AbilityTrigger.ENTERS_PLAY, AbilityEffectType.GAIN_GP, 2, 0))), new BoardPosition(1, 0));
        state.recordCardPlayed(arrival);
        assertEquals(2, state.player(0).currentGp());

        CardInstance deathrattle = add(state, 0, permanent("deathrattle", List.of(
                ability(AbilityTrigger.DESTROYED, AbilityEffectType.DAMAGE_ENEMY_CAPITAL, 2, 0))), new BoardPosition(2, 0));
        state.destroy(deathrattle);
        assertEquals(2, enemyCapital.damage());

        CardInstance tide = add(state, 0, permanent("tide", List.of(
                ability(AbilityTrigger.PASSIVE, AbilityEffectType.HEAL_SELF, 1, 0))), new BoardPosition(3, 1));
        tide.addDamage(2);
        new CardAbilityRules().resolve(state, tide, AbilityTrigger.PASSIVE);
        assertEquals(1, tide.damage());

        CardInstance repair = add(state, 0, permanent("repair", List.of(
                ability(AbilityTrigger.ACTIVATED, AbilityEffectType.HEAL_SELF, 3, 2))), new BoardPosition(3, 0));
        repair.addDamage(4);
        ActionResult activated = new GameEngine().apply(state,
                new GameAction.ActivateAbility(0, repair.instanceId()));
        assertTrue(activated.accepted());
        assertEquals(1, repair.damage());
        assertEquals(0, state.player(0).currentGp());
        assertFalse(new GameEngine().apply(state,
                new GameAction.ActivateAbility(0, repair.instanceId())).accepted());
        List<GameEvent> spending = state.events().stream()
                .filter(event -> event.type() == GameEvent.Type.GP_SPENT).toList();
        assertEquals(1, spending.size(), "Rejected repeat activation must not create a spending event");
        assertEquals("2 for repair ability", spending.get(0).detail());
        assertEquals(0, spending.get(0).playerId());
        assertEquals(Zone.BATTLEFIELD, home.zone());
    }

    @Test void typedDrawAbilitiesFindTheRequestedCardWithoutPunishingAMiss() {
        GameState state = new GameState(92L);
        CardInstance character = inDeck(state, 0, new CardDefinition("character", "Character",
                CardType.CHARACTER, "TEST", 1, 1, 1, 1, 1));
        CardInstance land = inDeck(state, 0, permanentOfType("land", CardType.LAND));
        CardInstance structure = inDeck(state, 0, permanentOfType("structure", CardType.STRUCTURE));
        state.player(0).loadDeck(List.of(land.instanceId(), character.instanceId(), structure.instanceId()));

        CardInstance landTutor = add(state, 0, permanent("land_tutor", List.of(
                ability(AbilityTrigger.ACTIVATED, AbilityEffectType.DRAW_STRUCTURE, 1, 0))), new BoardPosition(0, 0));
        new CardAbilityRules().resolve(state, landTutor, AbilityTrigger.ACTIVATED);
        assertTrue(state.player(0).hand().contains(structure.instanceId()));

        CardInstance structureTutor = add(state, 0, permanentOfType("structure_tutor", CardType.STRUCTURE,
                List.of(ability(AbilityTrigger.ACTIVATED, AbilityEffectType.DRAW_CHARACTER, 1, 0))), new BoardPosition(1, 0));
        new CardAbilityRules().resolve(state, structureTutor, AbilityTrigger.ACTIVATED);
        assertTrue(state.player(0).hand().contains(character.instanceId()));

        int damageBefore = landTutor.damage();
        new CardAbilityRules().resolve(state, structureTutor, AbilityTrigger.ACTIVATED);
        assertEquals(damageBefore, landTutor.damage(), "A failed typed search is not deck-exhaustion damage");
        assertTrue(state.events().stream().anyMatch(event -> event.type() == GameEvent.Type.DRAW_FAILED));
    }

    private CardAbility ability(AbilityTrigger trigger, AbilityEffectType effect, int amount, int cost) {
        return new CardAbility(trigger, effect, amount, cost);
    }

    private CardDefinition permanent(String id, List<CardAbility> abilities) {
        return permanentOfType(id, CardType.LAND, abilities);
    }

    private CardDefinition permanentOfType(String id, CardType type) {
        return permanentOfType(id, type, List.of());
    }

    private CardDefinition permanentOfType(String id, CardType type, List<CardAbility> abilities) {
        return new CardDefinition(id, id, type, "TEST", 1,
                0, 0, 0, 0, 8, Set.of(), List.of(), 1, DevelopmentPassive.NONE, abilities);
    }

    private CardInstance inDeck(GameState state, int owner, CardDefinition definition) {
        CardInstance card = new CardInstance(UUID.randomUUID(), definition, owner, Zone.DECK);
        state.register(card);
        return card;
    }

    private CardInstance add(GameState state, int owner, CardDefinition definition, BoardPosition position) {
        CardInstance card = new CardInstance(UUID.randomUUID(), definition, owner, Zone.BATTLEFIELD);
        state.register(card);
        state.board().push(position, card.instanceId());
        return card;
    }
}
