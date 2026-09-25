package com.infiniteconquest.core;

import java.util.*;
import com.infiniteconquest.data.Keyword;

public final class GameState {
    private final long seed;
    private final MatchRules rules;
    private final BoardState board;
    private final List<PlayerState> players;
    private final Map<UUID, CardInstance> cards;
    private final List<GameEvent> events;
    private final int[] personalTurns;
    private final boolean[] mulliganCompleted;
    private final int[] landsPlayedThisTurn;
    private final int[] structuresPlayedThisTurn;
    private final Set<String> terrainTriggersUsed;
    private final Set<String> capitalPassivesUsedThisTurn;
    private final CapitalPassiveRules capitalPassiveRules;
    private final CardAbilityRules cardAbilityRules;
    private int activePlayer;
    private int startingPlayer;
    private int turnNumber;
    private Phase phase = Phase.START;
    private long nextEventSequence;
    private Integer winner;
    private boolean started;
    private boolean mulliganWindowOpen = true;
    private boolean initialCapitalPassiveActivated;

    public GameState(long seed) { this(seed, MatchRules.current(), true); }
    GameState(long seed, MatchRules rules, boolean startImmediately) {
        this.seed = seed;
        this.rules = Objects.requireNonNull(rules);
        this.board = new BoardState();
        this.players = List.of(new PlayerState(0), new PlayerState(1));
        this.cards = new LinkedHashMap<>();
        this.events = new ArrayList<>();
        this.personalTurns = new int[2];
        this.mulliganCompleted = new boolean[2];
        this.landsPlayedThisTurn = new int[2];
        this.structuresPlayedThisTurn = new int[2];
        this.terrainTriggersUsed = new HashSet<>();
        this.capitalPassivesUsedThisTurn = new HashSet<>();
        this.capitalPassiveRules = new CapitalPassiveRules();
        this.cardAbilityRules = new CardAbilityRules();
        if (startImmediately) initializeMatch();
    }

    /**
     * Deep copy of the full authoritative state, used for AI lookahead
     * simulations. The rules objects are stateless and recreated; everything
     * else is duplicated, so applying engine actions to the copy can never
     * affect the original. Engine behavior is unchanged by this method's
     * existence.
     */
    public GameState copy() {
        return new GameState(this);
    }

    private GameState(GameState source) {
        this.seed = source.seed;
        this.rules = source.rules;
        this.board = new BoardState(source.board);
        this.players = List.of(new PlayerState(source.players.get(0)), new PlayerState(source.players.get(1)));
        this.cards = new LinkedHashMap<>();
        for (CardInstance card : source.cards.values()) this.cards.put(card.instanceId(), new CardInstance(card));
        this.events = new ArrayList<>(source.events);
        this.personalTurns = source.personalTurns.clone();
        this.mulliganCompleted = source.mulliganCompleted.clone();
        this.landsPlayedThisTurn = source.landsPlayedThisTurn.clone();
        this.structuresPlayedThisTurn = source.structuresPlayedThisTurn.clone();
        this.terrainTriggersUsed = new HashSet<>(source.terrainTriggersUsed);
        this.capitalPassivesUsedThisTurn = new HashSet<>(source.capitalPassivesUsedThisTurn);
        this.capitalPassiveRules = new CapitalPassiveRules();
        this.cardAbilityRules = new CardAbilityRules();
        this.activePlayer = source.activePlayer;
        this.startingPlayer = source.startingPlayer;
        this.turnNumber = source.turnNumber;
        this.phase = source.phase;
        this.nextEventSequence = source.nextEventSequence;
        this.winner = source.winner;
        this.started = source.started;
        this.mulliganWindowOpen = source.mulliganWindowOpen;
        this.initialCapitalPassiveActivated = source.initialCapitalPassiveActivated;
    }

    public long seed() { return seed; }
    public MatchRules rules() { return rules; }
    public BoardState board() { return board; }
    public PlayerState player(int id) { return players.get(id); }
    public int activePlayer() { return activePlayer; }
    public int startingPlayer() { return startingPlayer; }
    public int turnNumber() { return turnNumber; }
    public int personalTurnNumber(int id) { return personalTurns[id]; }
    public boolean canPlayDevelopment(int playerId, CardType type) {
        return switch (type) {
            case LAND -> landsPlayedThisTurn[playerId] == 0;
            case STRUCTURE -> structuresPlayedThisTurn[playerId] == 0;
            default -> true;
        };
    }
    /** Lands played this turn, for client-side legality hints in net snapshots. */
    public int landsPlayedThisTurn(int playerId) { return landsPlayedThisTurn[playerId]; }
    /** Structures played this turn, for client-side legality hints in net snapshots. */
    public int structuresPlayedThisTurn(int playerId) { return structuresPlayedThisTurn[playerId]; }
    public Phase phase() { return phase; }
    /** True while either player may still submit a mulligan decision. */
    public boolean isMulliganWindowOpen() { return mulliganWindowOpen; }
    public OptionalInt winner() { return winner == null ? OptionalInt.empty() : OptionalInt.of(winner); }
    public List<GameEvent> events() { return Collections.unmodifiableList(events); }
    public Optional<CardInstance> card(UUID id) { return Optional.ofNullable(cards.get(id)); }
    public List<CardInstance> battlefieldCards(int playerId) {
        return cards.values().stream().filter(card -> card.owner() == playerId && card.zone() == Zone.BATTLEFIELD).toList();
    }
    public int gpIncomePerTurn(int playerId) {
        return battlefieldCards(playerId).stream()
                .filter(card -> card.definition().type() == CardType.LAND
                        || card.definition().type() == CardType.STRUCTURE
                        || card.definition().type() == CardType.CAPITAL)
                .mapToInt(card -> card.definition().type() == CardType.CAPITAL
                        ? 1 : card.definition().income()).sum();
    }

    public void mulligan(int playerId, Collection<UUID> discardedCardIds) {
        if (playerId < 0 || playerId > 1) throw new IllegalArgumentException("Player must be 0 or 1");
        if (!mulliganWindowOpen || turnNumber != 1 || phase == Phase.GAME_OVER) throw new IllegalStateException("Mulligan window has closed");
        if (mulliganCompleted[playerId]) throw new IllegalStateException("Player already completed a mulligan");
        Set<UUID> discarded = Set.copyOf(discardedCardIds);
        if (discarded.size() > 3) throw new IllegalArgumentException("You may discard at most 3 cards");
        List<UUID> openingHand = new ArrayList<>(player(playerId).hand());
        if (!openingHand.containsAll(discarded)) throw new IllegalArgumentException("Discarded cards must be in the opening hand");
        int replaced = 0;
        for (UUID id : openingHand) if (discarded.contains(id)) {
            player(playerId).removeFromHand(id);
            CardInstance card = card(id).orElseThrow();
            card.moveTo(Zone.DISCARD);
            player(playerId).addToDiscard(id);
            replaced++;
        }
        for (int i = 0; i < replaced; i++) drawCard(playerId);
        mulliganCompleted[playerId] = true;
        if (mulliganCompleted[0] && mulliganCompleted[1]) mulliganWindowOpen = false;
        emit(GameEvent.Type.MULLIGAN_COMPLETED, playerId,
                "Discarded and redrew " + replaced);
    }
    public Optional<CapitalPassive> capitalPassiveFor(int playerId) {
        return battlefieldCards(playerId).stream()
                .filter(card -> card.definition().type() == CardType.CAPITAL)
                .findFirst().flatMap(card -> capitalPassiveRules.passiveFor(card.definition()));
    }

    public void register(CardInstance card) {
        if (cards.putIfAbsent(card.instanceId(), card) != null) throw new IllegalArgumentException("Duplicate card instance ID");
    }
    void setStartingPlayer(int playerId) {
        if (started) throw new IllegalStateException("Starting player is already locked");
        if (playerId < 0 || playerId > 1) throw new IllegalArgumentException("Player must be 0 or 1");
        startingPlayer = playerId;
    }
    void initializeMatch() {
        if (started) throw new IllegalStateException("Match already started");
        started = true;
        activePlayer = startingPlayer;
        turnNumber = 1;
        personalTurns[activePlayer] = 1;
        for (PlayerState player : players) player.initializeGp(
                player.id() == 0 ? rules.startingGp() : rules.secondPlayerStartingGp());
        emit(GameEvent.Type.MATCH_STARTED, activePlayer,
                "Match seed " + seed + "; coin flip: Player " + (activePlayer + 1) + " starts");
        startTurn();
    }
    public void activateInitialCapitalPassive() {
        if (!started || turnNumber != 1) throw new IllegalStateException("Initial Capital passive timing has passed");
        if (initialCapitalPassiveActivated) throw new IllegalStateException("Initial Capital passive already activated");
        initialCapitalPassiveActivated = true;
        generateCapitalGp(activePlayer);
        capitalPassiveRules.onTurnStarted(this, activePlayer);
    }
    void drawInitialHands() {
        for (int playerId = 0; playerId < 2; playerId++)
            for (int i = 0; i < rules.initialHandSizeFor(playerId != startingPlayer); i++) drawCard(playerId);
    }
    void advanceTurn() {
        mulliganWindowOpen = false;
        phase = Phase.END;
        emit(GameEvent.Type.PHASE_CHANGED, activePlayer, "END");
        emit(GameEvent.Type.TURN_ENDED, activePlayer, "Turn ended");
        activePlayer = 1 - activePlayer; turnNumber++; personalTurns[activePlayer]++;
        startTurn();
    }
    void recordCardPlayed(CardInstance card) {
        mulliganWindowOpen = false;
        if (card.definition().type() == CardType.LAND) landsPlayedThisTurn[card.owner()]++;
        if (card.definition().type() == CardType.STRUCTURE) structuresPlayedThisTurn[card.owner()]++;
        emit(GameEvent.Type.CARD_PLAYED, card.owner(), card.instanceId().toString());
        capitalPassiveRules.onCardPlayed(this, card);
        applyDevelopmentDeployPassive(card);
        cardAbilityRules.resolve(this, card, AbilityTrigger.ENTERS_PLAY);
    }

    void spendGp(int playerId, int amount, String reason) {
        player(playerId).spendGp(amount);
        if (amount > 0) emit(GameEvent.Type.GP_SPENT, playerId, amount + " for " + reason);
    }
    void recordCharacterMoved(CardInstance card, BoardPosition from, BoardPosition to, int distance) {
        mulliganWindowOpen = false;
        emit(GameEvent.Type.CHARACTER_MOVED, card.owner(), card.instanceId() + " " + from + " -> " + to + " cost " + distance);
    }
    void recordAttack(CardInstance attacker, CardInstance target) {
        mulliganWindowOpen = false;
        emit(GameEvent.Type.ATTACK_RESOLVED, attacker.owner(), attacker.instanceId() + " -> " + target.instanceId());
    }
    void recordOpportunityAttack(CardInstance attacker, CardInstance target, BoardPosition trigger) {
        emit(GameEvent.Type.OPPORTUNITY_ATTACK, attacker.owner(),
                attacker.instanceId() + " -> " + target.instanceId() + " at " + trigger.x() + "," + trigger.y());
    }
    void drawCards(int playerId, int amount) {
        for (int i = 0; i < amount; i++) drawCard(playerId);
    }
    void drawCardsOfType(int playerId, CardType type, int amount) {
        for (int i = 0; i < amount; i++) {
            Optional<UUID> drawn = player(playerId).drawFirst(id -> card(id)
                    .map(value -> value.definition().type() == type).orElse(false));
            if (drawn.isEmpty()) {
                emit(GameEvent.Type.DRAW_FAILED, playerId, "No " + type + " remains in deck");
                return;
            }
            CardInstance instance = card(drawn.orElseThrow()).orElseThrow();
            instance.moveTo(Zone.HAND);
            emit(GameEvent.Type.CARD_DRAWN, playerId, instance.instanceId().toString());
        }
    }
    void returnCharacterToHand(CardInstance card) {
        board.remove(card.instanceId());
        card.moveTo(Zone.HAND);
        player(card.owner()).addToHand(card.instanceId());
    }
    void destroy(CardInstance card) {
        if(card.zone()!=Zone.BATTLEFIELD)return;
        boolean permanent = card.definition().isPermanent();
        BoardPosition formerPosition = board.positionOf(card.instanceId()).orElse(null);
        board.remove(card.instanceId());
        card.moveTo(Zone.DISCARD);
        player(card.owner()).addToDiscard(card.instanceId());
        emit(GameEvent.Type.CARD_DESTROYED, card.owner(), card.instanceId().toString());
        cardAbilityRules.resolve(this, card, AbilityTrigger.DESTROYED);
        if(card.definition().hasKeyword(Keyword.ARCHIVE)) {
            int amount=card.definition().keywordValue(Keyword.ARCHIVE).amount();
            recordTerrain(card,card,Keyword.ARCHIVE,amount);
            drawCards(card.owner(),amount);
        }
        if (permanent) capitalPassiveRules.onPermanentDestroyed(this, card);
        if (permanent && phase != Phase.GAME_OVER) {
            int result = new VictoryEvaluator().winnerAfterCapitalLoss(this, card.owner());
            if (result >= 0) {
                finishGame(result, "Player " + result + " destroyed the enemy Capital");
            }
        }
        if (phase != Phase.GAME_OVER && formerPosition != null) {
            board.topAt(formerPosition).flatMap(this::card)
                    .filter(revealed -> revealed.definition().isPermanent())
                    .filter(revealed -> revealed.damage() >= revealed.definition().hitPoints())
                    .ifPresent(this::destroy);
        }
    }

    private void startTurn() {
        capitalPassivesUsedThisTurn.clear();
        terrainTriggersUsed.clear();
        landsPlayedThisTurn[activePlayer] = 0;
        structuresPlayedThisTurn[activePlayer] = 0;
        cards.values().forEach(CardInstance::clearCombatDamage);
        phase = Phase.START;
        emit(GameEvent.Type.PHASE_CHANGED, activePlayer, "START");
        generatePermanentGp(activePlayer);
        applyDevelopmentStartPassives(activePlayer);
        resetControlledCards(activePlayer);
        TerrainRules.startTurn(this, activePlayer);
        battlefieldCards(activePlayer).forEach(card -> cardAbilityRules.resolve(this, card, AbilityTrigger.PASSIVE));
        if (turnNumber > 1) {
            for (int i = 0; i < rules.cardsDrawnAtTurnStart(); i++) drawCard(activePlayer);
        }
        if (phase == Phase.GAME_OVER) return;
        capitalPassiveRules.onTurnStarted(this, activePlayer);
        if (phase == Phase.GAME_OVER) return;
        emit(GameEvent.Type.TURN_STARTED, activePlayer, "Personal turn " + personalTurns[activePlayer]);
        phase = Phase.PLAY;
        emit(GameEvent.Type.PHASE_CHANGED, activePlayer, "PLAY");
    }
    private void resetControlledCards(int playerId) {
        int untapped = 0;
        for (CardInstance card : cards.values()) if (card.owner() == playerId && card.zone() == Zone.BATTLEFIELD) {
            if (card.tapped()) untapped++;
            card.resetTurnActions();
        }
        emit(GameEvent.Type.CARDS_UNTAPPED, playerId, Integer.toString(untapped));
    }
    private void drawCard(int playerId) {
        Optional<UUID> drawn = player(playerId).drawOne();
        if (drawn.isEmpty()) {
            emit(GameEvent.Type.DRAW_FAILED, playerId, "Deck is empty");
            for (CardInstance card : cards.values()) if (card.owner() == playerId && card.zone() == Zone.BATTLEFIELD && card.definition().isPermanent()) {
                card.addDamage(1);
                emit(GameEvent.Type.EXHAUSTION_DAMAGE, playerId, card.instanceId().toString());
                if (card.damage() >= card.definition().hitPoints()
                        && board.positionOf(card.instanceId()).flatMap(board::topAt)
                        .filter(card.instanceId()::equals).isPresent()) destroy(card);
                if (phase == Phase.GAME_OVER) break;
            }
            return;
        }
        CardInstance instance = cards.get(drawn.orElseThrow());
        if (instance == null) throw new IllegalStateException("Deck references unregistered card");
        instance.moveTo(Zone.HAND);
        emit(GameEvent.Type.CARD_DRAWN, playerId, instance.instanceId().toString());
    }
    Optional<CardInstance> returnMostRecentDiscardedCharacter(int playerId) {
        Optional<UUID> id = player(playerId).removeMostRecentDiscard(value -> card(value)
                .map(card -> card.definition().type() == CardType.CHARACTER).orElse(false));
        if (id.isEmpty()) return Optional.empty();
        CardInstance returned = card(id.orElseThrow()).orElseThrow();
        returned.moveTo(Zone.HAND);
        player(playerId).addToHand(returned.instanceId());
        return Optional.of(returned);
    }
    boolean tryUseCapitalPassive(int playerId, CapitalPassive passive) {
        return capitalPassivesUsedThisTurn.add(playerId + ":" + passive.name());
    }
    void markCapitalPassiveUsed(int playerId, CapitalPassive passive) {
        capitalPassivesUsedThisTurn.add(playerId + ":" + passive.name());
    }
    void recordCapitalPassive(int playerId, CapitalPassive passive, String detail) {
        emit(GameEvent.Type.CAPITAL_PASSIVE_TRIGGERED, playerId, passive.name() + ": " + detail);
    }
    private void generatePermanentGp(int playerId) {
        int generated = gpIncomePerTurn(playerId);
        if (generated > 0) player(playerId).restoreGp(generated);
        emit(GameEvent.Type.GP_GENERATED, playerId, generated + " GP from Capital, Lands and Structures");
    }
    private void generateCapitalGp(int playerId) {
        boolean controlsCapital = battlefieldCards(playerId).stream()
                .anyMatch(card -> card.definition().type() == CardType.CAPITAL);
        if (!controlsCapital) return;
        player(playerId).restoreGp(1);
        emit(GameEvent.Type.GP_GENERATED, playerId, "1 GP from Capital");
    }
    private void applyDevelopmentDeployPassive(CardInstance card) {
        switch (card.definition().developmentPassive()) {
            case DRAW_ON_DEPLOY -> {
                drawCards(card.owner(), 1);
                recordDevelopmentPassive(card);
            }
            case HEAL_CAPITAL_ON_DEPLOY -> {
                battlefieldCards(card.owner()).stream()
                    .filter(value -> value.definition().type() == CardType.CAPITAL)
                    .findFirst().ifPresent(value -> value.healDamage(3));
                recordDevelopmentPassive(card);
            }
            default -> { }
        }
    }
    private void applyDevelopmentStartPassives(int playerId) {
        battlefieldCards(playerId).stream()
                .filter(card -> card.definition().developmentPassive() == DevelopmentPassive.SELF_REPAIR)
                .filter(card -> card.damage() > 0)
                .forEach(card -> {
                    card.healDamage(2);
                    recordDevelopmentPassive(card);
                });
    }
    private void recordDevelopmentPassive(CardInstance card) {
        emit(GameEvent.Type.DEVELOPMENT_PASSIVE_TRIGGERED, card.owner(),
                card.instanceId() + " " + DevelopmentRules.passiveText(card.definition().developmentPassive()));
    }
    void recordCardAbility(CardInstance card, CardAbility ability) {
        emit(GameEvent.Type.CARD_ABILITY_TRIGGERED, card.owner(), card.instanceId() + " "
                + ability.trigger() + " " + ability.effect() + " " + ability.amount()
                + (ability.gpCost() > 0 ? " cost " + ability.gpCost() : ""));
    }
    boolean useTerrainTrigger(CardInstance source, CardInstance target, Keyword keyword) {
        return terrainTriggersUsed.add(source.instanceId()+":"+target.instanceId()+":"+keyword);
    }
    void recordTerrain(CardInstance source,CardInstance target,Keyword keyword,int amount) {
        BoardPosition position=board.positionOf(target.instanceId()).orElse(null);
        emit(GameEvent.Type.TERRAIN_TRIGGERED,source.owner(),source.instanceId()+" "+target.instanceId()+" "+keyword+" "+amount
                + (position==null?"":" "+position.x()+","+position.y()));
    }
    private void finishGame(Integer winningPlayer, String detail) {
        winner = winningPlayer;
        phase = Phase.GAME_OVER;
        emit(GameEvent.Type.GAME_OVER, winningPlayer == null ? -1 : winningPlayer, detail);
    }
    private void emit(GameEvent.Type type, int playerId, String detail) {
        events.add(new GameEvent(nextEventSequence++, turnNumber, playerId, type, detail));
    }

    /**
     * Placeholder definition for hidden opponent cards. Instances are inert:
     * only hand/deck counts are ever read from them.
     */
    private static final CardDefinition HIDDEN_CARD =
            new CardDefinition("hidden_card", "Hidden Card", CardType.CHARACTER, "HIDDEN", 0, 0, 0, 0, 0);

    /** Deterministic placeholder IDs so consecutive snapshots diff cleanly. */
    private static UUID hiddenCardId(int player, Zone zone, int index) {
        return UUID.nameUUIDFromBytes(
                ("ic-hidden:" + player + ":" + zone + ":" + index).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * Rebuilds a client-side {@code GameState} from a redacted {@link GameSnapshot}.
     *
     * <p>The result is a faithful copy of everything the viewing player may see:
     * public zones in full, the viewer's own hand in full, and opponent
     * hidden zones as counts only. It is used to render confirmed server state;
     * commands are never applied to it locally. Unknown definition IDs fail fast.
     *
     * @param snapshot    a snapshot produced for the viewing player
     * @param definitions resolves definition IDs to {@link CardDefinition}s
     */
    public static GameState fromSnapshot(GameSnapshot snapshot,
                                         java.util.function.Function<String, CardDefinition> definitions) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(definitions, "definitions");
        MatchRules rules = "SQUARE".equals(snapshot.rulesId()) ? MatchRules.current() : MatchRules.hex();
        GameState state = new GameState(snapshot.seed(), rules, false);
        state.started = true;
        state.activePlayer = snapshot.activePlayer();
        state.startingPlayer = snapshot.startingPlayer();
        state.turnNumber = snapshot.turnNumber();
        System.arraycopy(snapshot.personalTurns(), 0, state.personalTurns, 0, 2);
        state.phase = Phase.valueOf(snapshot.phase());
        state.winner = snapshot.winner();
        state.mulliganWindowOpen = false;
        state.mulliganCompleted[0] = true;
        state.mulliganCompleted[1] = true;
        state.initialCapitalPassiveActivated = true;
        System.arraycopy(snapshot.landsPlayed(), 0, state.landsPlayedThisTurn, 0, 2);
        System.arraycopy(snapshot.structuresPlayed(), 0, state.structuresPlayedThisTurn, 0, 2);

        List<List<UUID>> deckOrder = List.of(new ArrayList<>(), new ArrayList<>());
        List<CardInstance> hands = new ArrayList<>();
        List<CardInstance> discards = new ArrayList<>();
        List<GameSnapshot.CardView> boardViews = new ArrayList<>();
        for (GameSnapshot.CardView view : snapshot.cards()) {
            CardDefinition definition = definitions.apply(view.definitionId());
            if (definition == null)
                throw new IllegalArgumentException("Unknown card definition: " + view.definitionId());
            CardInstance card = new CardInstance(view.instanceId(), definition, view.owner(), view.zone());
            card.addDamage(view.damage());
            card.setTapped(view.tapped());
            card.addAttackBonus(view.attackBonus());
            card.addDefenseBonus(view.defenseBonus());
            if (view.attackedThisTurn()) card.markAttacked();
            if (view.blinkUsedThisTurn()) card.markBlinkUsed();
            if (view.abilityUsedThisTurn()) card.markAbilityUsed();
            card.spendMovement(view.movementSpent());
            state.register(card);
            switch (view.zone()) {
                case DECK -> deckOrder.get(view.owner()).add(view.instanceId());
                case HAND -> hands.add(card);
                case DISCARD -> discards.add(card);
                case BATTLEFIELD -> boardViews.add(view);
            }
        }
        for (int playerId = 0; playerId < 2; playerId++) {
            List<UUID> order = new ArrayList<>(deckOrder.get(playerId));
            state.players.get(playerId).initializeGp(snapshot.maxGp()[playerId]);
            int excess = snapshot.maxGp()[playerId] - snapshot.gp()[playerId];
            if (excess > 0) state.players.get(playerId).spendGp(excess);
            if (playerId != snapshot.viewingPlayer()) {
                // Hidden deck: pad with deterministic placeholders so deck().size()
                // is exact. Placeholders are inert; only counts are ever read.
                for (int i = order.size(); i < snapshot.deckCounts()[playerId]; i++) {
                    UUID id = hiddenCardId(playerId, Zone.DECK, i);
                    state.register(new CardInstance(id, HIDDEN_CARD, playerId, Zone.DECK));
                    order.add(id);
                }
            }
            state.players.get(playerId).loadDeck(order);
        }
        for (CardInstance card : hands) state.players.get(card.owner()).addToHand(card.instanceId());
        if (snapshot.viewingPlayer() == 0 || snapshot.viewingPlayer() == 1) {
            int opponent = 1 - snapshot.viewingPlayer();
            for (int i = 0; i < snapshot.handCounts()[opponent]; i++) {
                UUID id = hiddenCardId(opponent, Zone.HAND, i);
                state.register(new CardInstance(id, HIDDEN_CARD, opponent, Zone.HAND));
                state.players.get(opponent).addToHand(id);
            }
        }
        for (CardInstance card : discards) state.players.get(card.owner()).addToDiscard(card.instanceId());
        for (GameSnapshot.CardView view : boardViews) state.board.push(view.position(), view.instanceId());

        state.events.addAll(snapshot.events());
        long maxSequence = snapshot.events().stream().mapToLong(GameEvent::sequence).max().orElse(-1L);
        state.nextEventSequence = maxSequence + 1;
        return state;
    }
}
