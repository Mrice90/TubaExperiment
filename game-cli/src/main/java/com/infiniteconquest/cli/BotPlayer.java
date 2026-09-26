package com.infiniteconquest.cli;

import com.infiniteconquest.core.*;
import com.infiniteconquest.data.Keyword;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;

public final class BotPlayer {
    public static final int BOT_ID = 1;
    /**
     * MORTAL mistake model, documented for players: on each decision the bot
     * misjudges like a human beginner. It is a cautious novice — it would
     * rather clear the board than strike the enemy Capital, so it filters out
     * capital attacks except for obvious lethals (even a beginner takes the
     * winning hit) and except when the Capital is the only enemy target left.
     * Independently, 25% of the time it explores, picking uniformly among
     * its top 3 heuristic actions instead of the best one. The misplaced
     * priorities are the systematic weakness; the exploration is ordinary
     * epsilon-greedy noise.
     */
    /** Chance MORTAL explores among its top actions instead of taking the best. */
    public static final double MORTAL_MISTAKE_RATE = 0.25;
    /** How many top heuristic actions MORTAL samples from when it explores. */
    public static final int MORTAL_EXPLORATION_WIDTH = 3;
    /**
     * DEMIGOD prescreen width: the heuristic score narrows the field to the
     * top 12 actions before the 1-ply simulation, keeping a turn well under
     * a second even on modest hardware.
     */
    public static final int DEMIGOD_PRESCREEN = 12;

    private final BotDifficulty difficulty;
    private final Random random;
    private final ActionHints hints = new ActionHints();

    /** Classic behavior: HERO difficulty with a fresh unseeded RNG. */
    public BotPlayer() {
        this(BotDifficulty.HERO, new Random());
    }

    public BotPlayer(BotDifficulty difficulty) {
        this(difficulty, new Random());
    }

    /**
     * @param difficulty which skill level to play at
     * @param random     drives MORTAL exploration; pass a seeded instance for
     *                   fully deterministic games (same seed + same decks =
     *                   identical game)
     */
    public BotPlayer(BotDifficulty difficulty, Random random) {
        this.difficulty = Objects.requireNonNull(difficulty, "difficulty");
        this.random = Objects.requireNonNull(random, "random");
    }

    public BotDifficulty difficulty() { return difficulty; }

    public Decision takeNextAction(GameState state, CommandProcessor commands) {
        return takeNextAction(state, commands, BOT_ID);
    }

    public Decision takeNextAction(GameState state, CommandProcessor commands, int playerId) {
        if (state.activePlayer() != playerId) throw new IllegalStateException("It is not player " + playerId + "'s turn");
        List<String> legal = hints.forActivePlayer(state, new GameEngine());
        String command = choose(state, legal, playerId);
        return new Decision(command, commands.execute(command));
    }

    public Decision react(GameState state, CommandProcessor commands) {
        return react(state, commands, BOT_ID);
    }

    public Decision react(GameState state, CommandProcessor commands, int playerId) {
        if (state.activePlayer() == playerId) return null;
        List<String> legal = hints.spellActionsForPlayer(state, playerId);
        if (legal.isEmpty()) return null;
        String command = choose(state, legal, playerId);
        return new Decision(command, commands.execute(command));
    }

    private String choose(GameState state, List<String> legal, int playerId) {
        if (legal.isEmpty()) return "end";
        return switch (difficulty) {
            case MORTAL -> mortalChoice(state, legal, playerId);
            case HERO -> heroChoice(state, legal, playerId);
            case DEMIGOD -> demigodChoice(state, legal, playerId);
        };
    }

    /** Original behavior, byte-for-byte: argmax on the heuristic score, largest command string wins ties. */
    private String heroChoice(GameState state, List<String> legal, int playerId) {
        return ranked(state, legal, playerId).get(0);
    }

    /**
     * Human-like mistakes: MORTAL plays like a cautious beginner. It would
     * rather clear the board than strike the enemy Capital, so non-lethal
     * capital attacks are filtered out of its options — but it still takes
     * an obvious lethal on the Capital, and it still attacks the Capital
     * when nothing else is left to hit. On top of that,
     * {@link #MORTAL_MISTAKE_RATE} of the time it explores, picking
     * uniformly among its top {@link #MORTAL_EXPLORATION_WIDTH} heuristic
     * actions instead of the best. RNG draws happen in a fixed order per
     * decision, so a seeded {@link Random} reproduces the same game exactly.
     */
    private String mortalChoice(GameState state, List<String> legal, int playerId) {
        List<String> pool = filterCapitalAttacks(state, legal, playerId);
        List<String> ranked = ranked(state, pool, playerId);
        if (ranked.size() > 1 && random.nextDouble() < MORTAL_MISTAKE_RATE) {
            return ranked.get(random.nextInt(Math.min(MORTAL_EXPLORATION_WIDTH, ranked.size())));
        }
        return ranked.get(0);
    }

    /**
     * The novice's misplaced priorities: drop strikes on the enemy Capital —
     * both attacks and activated damage abilities — unless the strike would
     * obviously destroy it, or unless the Capital is the only enemy target
     * available. Never returns an empty pool — if every legal command was a
     * filtered capital strike, the full list is kept.
     */
    private List<String> filterCapitalAttacks(GameState state, List<String> legal, int playerId) {
        List<String> capitalStrikes = new ArrayList<>();
        List<String> others = new ArrayList<>(legal.size());
        for (String command : legal) {
            if (isCapitalStrike(state, command)) capitalStrikes.add(command);
            else others.add(command);
        }
        if (capitalStrikes.isEmpty()) return legal;
        boolean anyOtherAttackTarget = others.stream().anyMatch(command -> command.startsWith("attack"));
        if (!anyOtherAttackTarget) return legal; // nothing else to hit: even a novice swings at the Capital
        for (String command : capitalStrikes) {
            if (isLethalCapitalStrike(state, command, playerId)) others.add(command); // even a novice takes lethal
        }
        return others.isEmpty() ? legal : others;
    }

    /** True if the command strikes the enemy Capital: an attack on it, or an
     * activated ability whose every effect damages it. */
    private boolean isCapitalStrike(GameState state, String command) {
        return isCapitalAttack(state, command) || isCapitalPinger(state, command);
    }

    /** True if the command activates an ability that does nothing but damage the enemy Capital. */
    private boolean isCapitalPinger(GameState state, String command) {
        if (!command.startsWith("activate")) return false;
        String[] parts = command.split("\\s+");
        BoardPosition position = new BoardPosition(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        return state.board().topAt(position).flatMap(state::card)
                .map(card -> {
                    List<CardAbility> abilities = card.definition().abilities().stream()
                            .filter(ability -> ability.trigger() == AbilityTrigger.ACTIVATED)
                            .toList();
                    return !abilities.isEmpty() && abilities.stream()
                            .allMatch(ability -> ability.effect() == AbilityEffectType.DAMAGE_ENEMY_CAPITAL);
                })
                .orElse(false);
    }

    /** True if the capital strike would obviously destroy the enemy Capital. */
    private boolean isLethalCapitalStrike(GameState state, String command, int playerId) {
        if (command.startsWith("attack")) return isLethalCapitalAttack(state, command);
        String[] parts = command.split("\\s+");
        BoardPosition position = new BoardPosition(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        int ping = state.board().topAt(position).flatMap(state::card)
                .map(card -> card.definition().abilities().stream()
                        .filter(ability -> ability.trigger() == AbilityTrigger.ACTIVATED)
                        .mapToInt(CardAbility::amount)
                        .sum())
                .orElse(0);
        return ping >= enemyCapitalRemaining(state, playerId);
    }

    /** True if the command attacks a Capital. */
    private boolean isCapitalAttack(GameState state, String command) {
        if (!command.startsWith("attack")) return false;
        String[] parts = command.split("\\s+");
        BoardPosition target = new BoardPosition(Integer.parseInt(parts[3]), Integer.parseInt(parts[4]));
        return state.board().topAt(target).flatMap(state::card)
                .map(card -> card.definition().type() == CardType.CAPITAL).orElse(false);
    }

    /**
     * True if the attack would obviously destroy the enemy Capital: the
     * attacker's power (doubled by SIEGE, as the engine applies it) meets or
     * beats the Capital's remaining hit points. Terrain damage reduction is
     * ignored on purpose — a novice misjudges that, and erring toward
     * attacking the Capital is the desired direction.
     */
    private boolean isLethalCapitalAttack(GameState state, String command) {
        String[] parts = command.split("\\s+");
        BoardPosition from = new BoardPosition(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        BoardPosition target = new BoardPosition(Integer.parseInt(parts[3]), Integer.parseInt(parts[4]));
        CardInstance attacker = state.board().topAt(from).flatMap(state::card).orElse(null);
        CardInstance capital = state.board().topAt(target).flatMap(state::card).orElse(null);
        if (attacker == null || capital == null || capital.definition().type() != CardType.CAPITAL) return false;
        int power = attacker.effectiveAttack();
        if (attacker.definition().hasKeyword(Keyword.SIEGE)) power *= 2;
        return power >= capital.definition().hitPoints() - capital.damage();
    }

    /**
     * 1-ply lookahead: heuristic-prescreen to the top {@link #DEMIGOD_PRESCREEN}
     * actions, simulate each on a deep copy of the state, and take the move
     * with the best resulting position. Simulation never touches the real
     * game; the copy is discarded after evaluation.
     */
    private String demigodChoice(GameState state, List<String> legal, int playerId) {
        List<String> ranked = ranked(state, legal, playerId);
        List<String> candidates = ranked.subList(0, Math.min(DEMIGOD_PRESCREEN, ranked.size()));
        String best = candidates.get(0);
        double bestValue = Double.NEGATIVE_INFINITY;
        for (String command : candidates) {
            GameState simulated = state.copy();
            String result = new CommandProcessor(simulated).execute(command);
            if (!result.startsWith("OK:")) continue; // legal list came from this engine; stay safe anyway
            double value = evaluate(simulated, playerId);
            if (value > bestValue) {
                bestValue = value;
                best = command;
            }
        }
        return best;
    }

    /**
     * Position evaluation from {@code playerId}'s perspective. An immediate
     * win/loss dwarfs everything — a simulated move that destroys the enemy
     * Capital scores near-infinite, so lethal-on-capital detection dominates
     * the score automatically. Otherwise the Capital-health differential is
     * the primary axis: dealing Capital damage and preventing own-Capital
     * damage outrank material, GP, and hand-size differentials by an order
     * of magnitude, because the Capital is the win condition.
     */
    private double evaluate(GameState state, int playerId) {
        int foe = 1 - playerId;
        if (state.winner().isPresent()) return state.winner().getAsInt() == playerId ? 1e9 : -1e9;
        double capitals = capitalHealth(state, playerId) - capitalHealth(state, foe);
        double material = materialValue(state, playerId) - materialValue(state, foe);
        double gp = state.player(playerId).currentGp() - state.player(foe).currentGp();
        double cards = state.player(playerId).hand().size() - state.player(foe).hand().size();
        // A screened Capital (few enemy attackers with a sight line to it)
        // and an exposed enemy Capital are both worth real, if modest, value:
        // screens buy the turns that win games.
        double exposure = capitalExposure(state, foe) - capitalExposure(state, playerId);
        return 100.0 * capitals + 10.0 * material + 1.5 * gp + 2.0 * cards + 8.0 * exposure;
    }

    private double materialValue(GameState state, int playerId) {
        double total = 0;
        for (CardInstance card : state.battlefieldCards(playerId)) {
            if (card.definition().type() == CardType.CAPITAL) continue;
            total += card.effectiveAttack() + Math.max(0, card.defenseRemaining());
        }
        return total;
    }

    private double capitalHealth(GameState state, int playerId) {
        return state.battlefieldCards(playerId).stream()
                .filter(card -> card.definition().type() == CardType.CAPITAL)
                .mapToDouble(card -> Math.max(0, card.definition().hitPoints() - card.damage()))
                .sum();
    }

    /**
     * Heuristic ranking, best first. Sort order reproduces the original
     * {@code max(comparingInt(score).thenComparing(naturalOrder))} exactly:
     * highest score wins, and the largest command string wins score ties.
     */
    private List<String> ranked(GameState state, List<String> legal, int playerId) {
        return legal.stream()
                .sorted(Comparator.comparingInt((String value) -> score(state, value, playerId)).reversed()
                        .thenComparing(Comparator.reverseOrder()))
                .toList();
    }

    private int score(GameState state, String command, int playerId) {
        String[] parts = command.split("\\s+");
        int base = switch (parts[0]) {
            case "cast", "react" -> spellScore(state, parts, playerId);
            case "attack" -> {
                BoardPosition from = new BoardPosition(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
                BoardPosition target = new BoardPosition(Integer.parseInt(parts[3]), Integer.parseInt(parts[4]));
                CardInstance attacker = state.board().topAt(from).flatMap(state::card).orElseThrow();
                CardInstance card = state.board().topAt(target).flatMap(state::card).orElseThrow();
                if (card.definition().type() == CardType.CAPITAL) {
                    // Destroying the Capital wins the game. A lethal strike is
                    // the best possible action; chip damage is worth dealing,
                    // but killing enemy Characters that threaten our own
                    // Capital comes first.
                    int power = attacker.effectiveAttack();
                    if (attacker.definition().hasKeyword(Keyword.SIEGE)) power *= 2;
                    int remaining = card.definition().hitPoints() - card.damage();
                    yield power >= remaining ? 150 : 108;
                }
                // Characters are threats to the Capital; other permanents are
                // support pieces — worth hitting, but not before the real war.
                yield card.definition().type() == CardType.CHARACTER ? 110 : 95;
            }
            case "play" -> {
                int index = Integer.parseInt(parts[1]);
                CardType type = state.card(state.player(playerId).hand().get(index)).orElseThrow().definition().type();
                yield switch (type) {
                    case LAND -> 90;
                    // A structure that screens the Capital — standing on a
                    // sight line between an exposed enemy attacker and home —
                    // is worth far more than a bare stat play. The bonus
                    // fades on its own: once attackers are screened they no
                    // longer count as exposed.
                    case STRUCTURE -> 85 + screenBonus(state, parts, playerId);
                    case CHARACTER -> 80;
                    default -> 0;
                };
            }
            case "burrow" -> 82;
            case "blink" -> 45;
            case "move" -> 35;
            case "activate" -> activateScore(state, parts, playerId);
            case "end" -> 0;
            default -> 1;
        };
        return base + capitalSynergy(state, playerId, parts[0]);
    }

    private int spellScore(GameState state, String[] parts, int playerId) {
        int handIndex = Integer.parseInt(parts[0].equals("react") ? parts[2] : parts[1]);
        CardInstance spell = state.card(state.player(playerId).hand().get(handIndex)).orElseThrow();
        SpellEffect effect = spell.definition().effects().get(0);
        return switch (effect.type()) {
            case DAMAGE_PERMANENT -> 140 + effect.amount();
            case STRIKE_CHARACTER -> 135 + effect.amount();
            case RETURN_CHARACTER -> 125;
            case HEAL_PERMANENT -> 115 + effect.amount();
            case BUFF_ATTACK -> 105 + effect.amount();
            case BUFF_DEFENSE -> 100 + effect.amount();
            case TELEPORT_CHARACTER -> 60;
        };
    }

    /**
     * Values an activated ability by what it actually does instead of a flat
     * score. Lethal damage on the enemy Capital wins the game, so it scores
     * like a lethal attack; chip damage, card draw, healing, and buffs score
     * on the same scale as the rest of the heuristic, minus the GP the
     * ability costs to fire. Firing a heal with nothing to heal scores below
     * "end", so the bot holds its GP instead of wasting it.
     */
    private int activateScore(GameState state, String[] parts, int playerId) {
        BoardPosition position = new BoardPosition(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        CardInstance card = state.board().topAt(position).flatMap(state::card).orElse(null);
        if (card == null) return 1;
        int total = 0;
        int gpCost = 0;
        for (CardAbility ability : card.definition().abilities()) {
            if (ability.trigger() != AbilityTrigger.ACTIVATED) continue;
            gpCost += ability.gpCost();
            total += switch (ability.effect()) {
                case DAMAGE_ENEMY_CAPITAL -> {
                    int remaining = enemyCapitalRemaining(state, playerId);
                    if (ability.amount() >= remaining) yield 150;
                    yield 104 + 2 * ability.amount();
                }
                case DRAW_CARD, DRAW_CHARACTER, DRAW_STRUCTURE -> 66 + 6 * ability.amount();
                case GAIN_GP -> 48 + 2 * ability.amount();
                case HEAL_SELF -> {
                    int missing = card.damage();
                    if (missing == 0) yield 0;
                    yield 40 + 4 * Math.min(ability.amount(), missing);
                }
                case HEAL_CAPITAL -> {
                    int missing = ownCapitalMissing(state, playerId);
                    if (missing == 0) yield 0;
                    yield 50 + 5 * Math.min(ability.amount(), missing);
                }
                case BUFF_SELF_ATTACK -> 58 + 4 * ability.amount();
                case BUFF_SELF_DEFENSE -> 58 + 3 * ability.amount();
            };
        }
        return total - gpCost;
    }

    /** Least remaining hit points across the enemy Capitals. */
    private int enemyCapitalRemaining(GameState state, int playerId) {
        return state.battlefieldCards(1 - playerId).stream()
                .filter(card -> card.definition().type() == CardType.CAPITAL)
                .mapToInt(card -> Math.max(0, card.definition().hitPoints() - card.damage()))
                .min()
                .orElse(Integer.MAX_VALUE);
    }

    /** Total missing hit points across the player's own Capitals. */
    private int ownCapitalMissing(GameState state, int playerId) {
        return state.battlefieldCards(playerId).stream()
                .filter(card -> card.definition().type() == CardType.CAPITAL)
                .mapToInt(CardInstance::damage)
                .sum();
    }

    /**
     * Bonus for playing a structure on a hex that would screen the Capital:
     * +15 per enemy attacker whose currently-clear sight line to our Capital
     * the new structure would block, capped at +45. Evaluated against the
     * live board, so the bonus naturally disappears once the Capital is
     * already screened.
     */
    private int screenBonus(GameState state, String[] parts, int playerId) {
        BoardPosition at = new BoardPosition(Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        BoardPosition capital = capitalPosition(state, playerId);
        if (capital == null) return 0;
        int foe = 1 - playerId;
        LineOfSightRules sight = new LineOfSightRules();
        List<BoardPosition> exposed = new ArrayList<>();
        for (CardInstance enemy : state.battlefieldCards(foe)) {
            if (enemy.definition().type() != CardType.CHARACTER) continue;
            BoardPosition from = state.board().positionOf(enemy.instanceId()).orElse(null);
            if (from == null) continue;
            if (!state.board().topAt(from).map(top -> top.equals(enemy.instanceId())).orElse(false)) continue;
            if (sight.hasLineOfSight(state, from, capital)) exposed.add(from);
        }
        if (exposed.isEmpty()) return 0;
        // Ghost the structure onto the candidate hex and re-check the sight lines.
        GameState probe = state.copy();
        int index = Integer.parseInt(parts[1]);
        CardDefinition definition = state.card(state.player(playerId).hand().get(index)).orElseThrow().definition();
        CardInstance ghost = new CardInstance(UUID.randomUUID(), definition, playerId, Zone.BATTLEFIELD);
        probe.register(ghost);
        probe.board().push(at, ghost.instanceId());
        int screened = 0;
        for (BoardPosition from : exposed) {
            if (!sight.hasLineOfSight(probe, from, capital)) screened++;
        }
        return Math.min(45, 15 * screened);
    }

    private BoardPosition capitalPosition(GameState state, int playerId) {
        return state.battlefieldCards(playerId).stream()
                .filter(card -> card.definition().type() == CardType.CAPITAL)
                .map(card -> state.board().positionOf(card.instanceId()).orElse(null))
                .filter(Objects::nonNull)
                .findFirst().orElse(null);
    }

    /** How many of {@code owner}'s enemies can currently see their Capital. */
    private int capitalExposure(GameState state, int owner) {
        BoardPosition capital = capitalPosition(state, owner);
        if (capital == null) return 0;
        int foe = 1 - owner;
        LineOfSightRules sight = new LineOfSightRules();
        int exposed = 0;
        for (CardInstance enemy : state.battlefieldCards(foe)) {
            if (enemy.definition().type() != CardType.CHARACTER) continue;
            BoardPosition from = state.board().positionOf(enemy.instanceId()).orElse(null);
            if (from != null && sight.hasLineOfSight(state, from, capital)) exposed++;
        }
        return exposed;
    }

    private int capitalSynergy(GameState state, int playerId, String action) {        CapitalPassive passive = state.capitalPassiveFor(playerId).orElse(null);
        if (passive == null) return 0;
        return switch (passive) {
            case STORM_TITHE -> action.equals("cast") || action.equals("react") ? 8 : 0;
            case TRIDENT_RESTORATION -> action.equals("play") ? 3 : 0;
            case DEEP_RESERVES -> action.equals("burrow") ? 8 : 0;
            case CLOUDWARD -> action.equals("blink") ? 5 : 0;
            default -> 0;
        };
    }

    public record Decision(String command, String result) {}
}
