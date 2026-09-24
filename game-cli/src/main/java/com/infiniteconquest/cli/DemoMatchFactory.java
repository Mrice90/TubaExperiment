package com.infiniteconquest.cli;

import com.infiniteconquest.core.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class DemoMatchFactory {
    private static final List<String> STARTER_IDS = List.of(
            "neo_proto_naiad_recon_droid", "neo_proto_talus_defender",
            "neo_proto_asclepius_medibot", "neo_proto_zephyr_scout",
            "neo_proto_hephaestus_drone", "demo_land_a", "demo_land_b",
            "demo_land_c", "demo_structure_a", "demo_structure_b",
            "demo_volcanic_forge", "demo_ballista_nest", "demo_healing_sanctum",
            "demo_bronze_hoplite", "demo_oracle_skimmer");

    private final PrototypeCardPool pool = new PrototypeCardPool();
    private final CapitalRoster capitals = new CapitalRoster();

    public GameState create(long seed) {
        List<CardDefinition> deck = demoDeck();
        return create(seed, deck, deck);
    }

    public GameState create(long seed, List<CardDefinition> playerZeroDeck,
                            List<CardDefinition> playerOneDeck) {
        CardDefinition playerZeroCapital = capitals.defaultForDeck(playerZeroDeck).orElse(null);
        CardDefinition playerOneCapital = capitals.defaultForDeck(playerOneDeck).orElse(null);
        return create(seed, playerZeroDeck, playerOneDeck, playerZeroCapital, playerOneCapital);
    }

    public GameState create(long seed, List<CardDefinition> playerZeroDeck,
                            List<CardDefinition> playerOneDeck,
                            CardDefinition playerZeroCapital, CardDefinition playerOneCapital) {
        return create(seed, playerZeroDeck, playerOneDeck, playerZeroCapital, playerOneCapital,
                new BoardPosition(1, 0), randomBotCapitalPosition(seed));
    }

    public GameState create(long seed, List<CardDefinition> playerZeroDeck,
                            List<CardDefinition> playerOneDeck,
                            CardDefinition playerZeroCapital, CardDefinition playerOneCapital,
                            BoardPosition playerZeroCapitalPosition,
                            BoardPosition playerOneCapitalPosition) {
        validateCapitalChoice(playerZeroDeck, playerZeroCapital);
        validateCapitalChoice(playerOneDeck, playerOneCapital);
        GameState state = new MatchFactory().create(
                seed, MatchRules.current(), playerZeroDeck, playerOneDeck);
        deployCapitals(state, seed, playerZeroCapital, playerOneCapital,
                playerZeroCapitalPosition, playerOneCapitalPosition);
        return state;
    }

    public List<CardDefinition> demoDeck() {
        List<CardDefinition> deck = new ArrayList<>();
        for (String id : STARTER_IDS) {
            CardDefinition definition = pool.require(id);
            for (int copy = 0; copy < DeckValidator.MAX_COPIES; copy++) deck.add(definition);
        }
        return List.copyOf(deck);
    }

    public PrototypeCardPool pool() { return pool; }

    public GameState create(long seed, DeckBuild human, DeckBuild bot, BoardPosition humanPosition,
                            BoardPosition botPosition, BoardGeometry geometry) {
        GameState state = new MatchFactory().create(seed,
                geometry == BoardGeometry.HEX ? MatchRules.hex() : MatchRules.current(), human.cards(), bot.cards());
        deployCapitals(state, seed, human.capital(), bot.capital(), humanPosition, botPosition);
        return state;
    }
    public CapitalRoster capitals() { return capitals; }

    private void validateCapitalChoice(List<CardDefinition> deck, CardDefinition capital) {
        if (capital == null) return;
        if (capital.type() != CardType.CAPITAL) throw new IllegalArgumentException("Selected card is not a Capital");
        java.util.Set<String> factions = deck.stream().map(CardDefinition::faction)
                .filter(FactionDecks.FACTIONS::contains).collect(java.util.stream.Collectors.toSet());
        if (factions.size() == 1 && !factions.contains(capital.faction())) {
            throw new IllegalArgumentException("Capital faction must match the deck faction");
        }
    }

    private void deployCapitals(GameState state, long seed,
                                CardDefinition playerZeroCapital, CardDefinition playerOneCapital,
                                BoardPosition playerZeroPosition, BoardPosition playerOnePosition) {
        CapitalDeployment deployment = new CapitalDeployment();
        for (int player = 0; player < 2; player++) {
            CardDefinition selected = player == 0 ? playerZeroCapital : playerOneCapital;
            CardDefinition definition = selected != null ? selected : new CardDefinition(
                    "demo_capital_p" + player, "Player " + (player + 1) + " Capital",
                    CardType.CAPITAL, "DEMO", 0, 0, 0, 0, 0, 20);
            UUID id = UUID.nameUUIDFromBytes(
                    (seed + ":capital:" + player).getBytes(StandardCharsets.UTF_8));
            CardInstance capital = new CardInstance(id, definition, player, Zone.DECK);
            state.register(capital);
            deployment.commit(player, capital, player == 0 ? playerZeroPosition : playerOnePosition);
        }
        deployment.reveal(state.board());
        state.activateInitialCapitalPassive();
    }

    private BoardPosition randomBotCapitalPosition(long seed) {
        java.util.Random random = new java.util.Random(seed ^ 0xB07CA917L);
        return new BoardPosition(random.nextInt(BoardPosition.WIDTH),
                3 + random.nextInt(BoardPosition.HEIGHT / 2));
    }
}
