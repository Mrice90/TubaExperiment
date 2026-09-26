package com.infiniteconquest.cli;

import com.infiniteconquest.core.AbilityEffectType;
import com.infiniteconquest.core.AbilityTrigger;
import com.infiniteconquest.core.BoardPosition;
import com.infiniteconquest.core.CardAbility;
import com.infiniteconquest.core.CardDefinition;
import com.infiniteconquest.core.CardInstance;
import com.infiniteconquest.core.CardType;
import com.infiniteconquest.core.DevelopmentPassive;
import com.infiniteconquest.core.GameAction;
import com.infiniteconquest.core.GameEngine;
import com.infiniteconquest.core.GameState;
import com.infiniteconquest.core.Zone;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The bot builds screens: when an enemy attacker has a clear sight line to
 * the bot's Capital, HERO plays a structure onto the sight line instead of
 * an equivalent off-line hex. (Square geometry: the (1,1)-(1,4) sight line
 * crosses (1,2) and (1,3).)
 */
class BotDefensiveStructuresTest {

    private static CardInstance add(GameState state, int owner, CardDefinition definition, BoardPosition position) {
        CardInstance card = new CardInstance(UUID.randomUUID(), definition, owner, Zone.BATTLEFIELD);
        state.register(card);
        state.board().push(position, card.instanceId());
        return card;
    }

    private static CardDefinition capital(String id) {
        return new CardDefinition(id, "Test " + id, CardType.CAPITAL, "T", 0, 0, 0, 0, 0, 20);
    }

    private static CardDefinition land(String id) {
        return new CardDefinition(id, "Test " + id, CardType.LAND, "T", 0, 0, 0, 0, 0, 8);
    }

    private static CardDefinition structure(String id) {
        return new CardDefinition(id, "Test " + id, CardType.STRUCTURE, "T", 0, 0, 0, 0, 0, 5);
    }

    private static CardDefinition character(String id) {
        return new CardDefinition(id, "Test " + id, CardType.CHARACTER, "T", 0, 3, 3, 2, 1);
    }

    /** Fresh game with the bot (player 1) to move and an empty hand. */
    private static GameState botTurn(long seed) {
        GameState state = new GameState(seed);
        if (state.activePlayer() == 0) new GameEngine().apply(state, new GameAction.EndTurn(0));
        assertEquals(1, state.activePlayer());
        for (UUID id : List.copyOf(state.player(1).hand())) state.player(1).removeFromHand(id);
        return state;
    }

    private static CardInstance giveStructure(GameState state) {
        CardInstance wall = new CardInstance(UUID.randomUUID(), structure("wall"), 1, Zone.HAND);
        state.register(wall);
        state.player(1).addToHand(wall.instanceId());
        return wall;
    }

    @Test
    void heroScreensExposedCapitalInsteadOfBuildingOffLine() {
        GameState state = botTurn(77L);
        add(state, 1, capital("b_cap"), new BoardPosition(1, 4));
        add(state, 1, land("b_land_screen"), new BoardPosition(1, 3));
        add(state, 1, land("b_land_plain"), new BoardPosition(3, 3));
        add(state, 0, character("raider"), new BoardPosition(1, 1));
        CardInstance wall = giveStructure(state);
        int index = state.player(1).hand().indexOf(wall.instanceId());

        BotPlayer.Decision decision =
                new BotPlayer(BotDifficulty.HERO).takeNextAction(state, new CommandProcessor(state), 1);
        // The screen bonus (100) beats both the flat off-line play (85) and the
        // deterministic tie-break, which would otherwise prefer "play 0 3 3".
        assertEquals("play " + index + " 1 3", decision.command(),
                "the structure should go onto the attacker-capital sight line");
    }

    @Test
    void screenBonusFadesOnceTheCapitalIsAlreadyScreened() {
        GameState state = botTurn(78L);
        add(state, 1, capital("b_cap"), new BoardPosition(1, 4));
        add(state, 1, land("b_land_screen"), new BoardPosition(1, 3));
        add(state, 1, land("b_land_plain"), new BoardPosition(3, 3));
        add(state, 1, structure("existing_screen"), new BoardPosition(1, 2));
        add(state, 0, character("raider"), new BoardPosition(1, 1));
        CardInstance wall = giveStructure(state);
        int index = state.player(1).hand().indexOf(wall.instanceId());

        BotPlayer.Decision decision =
                new BotPlayer(BotDifficulty.HERO).takeNextAction(state, new CommandProcessor(state), 1);
        // The raider's sight line is already blocked, so nobody is exposed:
        // both placements score the flat 85 and the tie-break picks "play 0 3 3".
        assertEquals("play " + index + " 3 3", decision.command(),
                "with the capital screened the bot should stop paying the screen premium");
    }

    @Test
    void blockedPingerActivationIsHiddenFromHints() {
        GameState state = new GameState(91L);
        add(state, 0, capital("e_cap"), new BoardPosition(3, 1));
        add(state, 1, capital("b_cap"), new BoardPosition(3, 4));
        CardInstance pinger = add(state, 1, new CardDefinition("pinger", "Pinger", CardType.STRUCTURE, "T", 0,
                0, 0, 0, 0, 5, Set.of(), List.of(), 0, DevelopmentPassive.NONE,
                List.of(new CardAbility(AbilityTrigger.ACTIVATED,
                        AbilityEffectType.DAMAGE_ENEMY_CAPITAL, 1, 0))), new BoardPosition(0, 1));
        add(state, 0, structure("screen"), new BoardPosition(1, 1));
        if (state.activePlayer() == 0) new GameEngine().apply(state, new GameAction.EndTurn(0));

        List<String> hints = new ActionHints().forActivePlayer(state, new GameEngine());
        BoardPosition pingerPos = state.board().positionOf(pinger.instanceId()).orElseThrow();

        assertTrue(hints.stream().noneMatch(hint ->
                hint.equals("activate " + pingerPos.x() + " " + pingerPos.y())),
                "a cover-blocked capital striker must not be offered, but got: " + hints);
    }
}
