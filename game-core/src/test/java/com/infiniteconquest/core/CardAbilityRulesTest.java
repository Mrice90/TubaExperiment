package com.infiniteconquest.core;

import com.infiniteconquest.data.CardCatalog;
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

    /**
     * Exercises the newly redesigned lands/structures straight from
     * faction-cards.json, so the JSON content itself is pinned to working
     * engine behavior.
     */
    @Test void redesignedFactionPermanentsResolveTheirCatalogAbilities() {
        CardCatalog catalog = CardCatalog.loadResource("/cards/faction-cards.json");
        GameState state = new GameState(93L);
        CardInstance homeCapital = add(state, 0, new CardDefinition("home_capital", "Home Capital",
                CardType.CAPITAL, "TEST", 0, 0, 0, 0, 0, 20), new BoardPosition(3, 5));
        CardInstance enemyCapital = add(state, 1, new CardDefinition("enemy_capital", "Enemy Capital",
                CardType.CAPITAL, "TEST", 0, 0, 0, 0, 0, 20), new BoardPosition(0, 5));
        GameEngine engine = new GameEngine();

        // DESTROYED on a land: Throneward Conduit gains 2 GP when destroyed.
        int gpBefore = state.player(0).currentGp();
        CardInstance conduit = add(state, 0,
                catalog.require("zeus_throneward_conduit").toDefinition(), new BoardPosition(0, 0));
        assertEquals(CardType.LAND, conduit.definition().type());
        state.destroy(conduit);
        assertEquals(gpBefore + 2, state.player(0).currentGp(), "Conduit deathrattle");

        // DESTROYED discharge: Keraunos Charging Spire hits the enemy Capital.
        CardInstance spire = add(state, 0,
                catalog.require("zeus_keraunos_charging_spire").toDefinition(), new BoardPosition(1, 0));
        state.destroy(spire);
        assertEquals(3, enemyCapital.damage(), "Spire discharge");

        // ACTIVATED payment + once-per-turn: Storm Relay Pylon.
        CardInstance pylon = add(state, 0,
                catalog.require("zeus_storm_relay_pylon").toDefinition(), new BoardPosition(2, 0));
        state.player(0).restoreGp(2);
        int gpBeforePylon = state.player(0).currentGp();
        assertTrue(engine.apply(state, new GameAction.ActivateAbility(0, pylon.instanceId())).accepted());
        assertEquals(4, enemyCapital.damage(), "Pylon strike");
        assertEquals(gpBeforePylon - 2, state.player(0).currentGp(), "Pylon paid 2 GP");
        assertFalse(engine.apply(state, new GameAction.ActivateAbility(0, pylon.instanceId())).accepted(),
                "Pylon is once per turn");

        // PASSIVE heal on a structure: Tidal Pump Station.
        CardInstance pump = add(state, 0,
                catalog.require("poseidon_tidal_pump_station").toDefinition(), new BoardPosition(3, 0));
        pump.addDamage(3);
        new CardAbilityRules().resolve(state, pump, AbilityTrigger.PASSIVE);
        assertEquals(1, pump.damage(), "Pump heals itself 2 at start of turn");

        // PASSIVE Capital heal: Moonwell Tidegate.
        CardInstance moonwell = add(state, 0,
                catalog.require("poseidon_moonwell_tidegate").toDefinition(), new BoardPosition(0, 1));
        homeCapital.addDamage(2);
        new CardAbilityRules().resolve(state, moonwell, AbilityTrigger.PASSIVE);
        assertEquals(1, homeCapital.damage(), "Moonwell heals Capital 1");

        // DESTROYED dredge: Drowned Archive draws 2 on death.
        CardInstance filler1 = inDeck(state, 0, permanent("filler1", List.of()));
        CardInstance filler2 = inDeck(state, 0, permanent("filler2", List.of()));
        CardInstance lf1 = inDeck(state, 0, permanent("lf1", List.of()));
        CardInstance lf2 = inDeck(state, 0, permanent("lf2", List.of()));
        state.player(0).loadDeck(List.of(filler1.instanceId(), filler2.instanceId(),
                lf1.instanceId(), lf2.instanceId()));
        int handBefore = state.player(0).hand().size();
        CardInstance archive = add(state, 0,
                catalog.require("poseidon_drowned_archive").toDefinition(), new BoardPosition(1, 1));
        state.destroy(archive);
        assertEquals(handBefore + 2, state.player(0).hand().size(), "Archive dredges 2 cards");

        // Storm surge engine: Ion Storm Lattice pays 3 GP to draw 2 cards.
        CardInstance lattice = add(state, 0,
                catalog.require("zeus_ion_storm_lattice").toDefinition(), new BoardPosition(2, 1));
        int handBeforeLattice = state.player(0).hand().size();
        state.player(0).restoreGp(3);
        int gpBeforeLattice = state.player(0).currentGp();
        assertTrue(engine.apply(state, new GameAction.ActivateAbility(0, lattice.instanceId())).accepted());
        assertEquals(handBeforeLattice + 2, state.player(0).hand().size(), "Lattice surges 2 cards");
        assertEquals(gpBeforeLattice - 3, state.player(0).currentGp(), "Lattice paid 3 GP");
    }

    private CardInstance add(GameState state, int owner, CardDefinition definition, BoardPosition position) {
        CardInstance card = new CardInstance(UUID.randomUUID(), definition, owner, Zone.BATTLEFIELD);
        state.register(card);
        state.board().push(position, card.instanceId());
        return card;
    }
}
