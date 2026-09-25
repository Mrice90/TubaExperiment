package com.infiniteconquest.cli;

import com.infiniteconquest.core.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Random;

public final class BotPlayer {
    public static final int BOT_ID = 1;
    /**
     * MORTAL mistake model, documented for players: on each decision the bot
     * misjudges like a human beginner. It is face-blind — it does not even
     * consider attacking the enemy Capital, preferring to clear the board
     * like a cautious novice who has not learned to go for the win.
     * Independently, 25% of the time it explores, picking uniformly among
     * its top 3 heuristic actions instead of the best one. The face-blindness
     * is the systematic weakness; the exploration is ordinary epsilon-greedy
     * noise.
     */
    /**
     * Chance MORTAL ignores Capital attacks on a decision (face-blind).
     * Set to 1.0: a cautious beginner who never goes for the win. Lower it
     * if MORTAL ever needs to close games; even 0.8 lets it win nearly half
     * its games against HERO, so keep this at 1.0 for a clear difficulty gap.
     */
    public static final double MORTAL_FACE_BLIND_RATE = 1.00;
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
     * Human-like mistakes: MORTAL is face-blind — like a cautious beginner it
     * would rather clear the board than strike the enemy Capital, so most
     * decisions it does not even consider attacks on Capitals. On top of
     * that, {@link #MORTAL_MISTAKE_RATE} of the time it explores, picking
     * uniformly among its top {@link #MORTAL_EXPLORATION_WIDTH} heuristic
     * actions instead of the best. RNG draws happen in a fixed order per
     * decision, so a seeded {@link Random} reproduces the same game exactly.
     */
    private String mortalChoice(GameState state, List<String> legal, int playerId) {
        List<String> pool = legal;
        if (random.nextDouble() < MORTAL_FACE_BLIND_RATE) {
            List<String> noFace = new ArrayList<>(legal.size());
            for (String command : legal) {
                if (isCapitalAttack(state, command)) continue;
                noFace.add(command);
            }
            if (!noFace.isEmpty()) pool = noFace;
        }
        List<String> ranked = ranked(state, pool, playerId);
        if (ranked.size() > 1 && random.nextDouble() < MORTAL_MISTAKE_RATE) {
            return ranked.get(random.nextInt(Math.min(MORTAL_EXPLORATION_WIDTH, ranked.size())));
        }
        return ranked.get(0);
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
     * win/loss dwarfs everything; otherwise material (attack + remaining
     * defense of non-Capital permanents), Capital health, GP, and hand size
     * differentials decide, with weights tuned so killing the enemy Capital
     * always outranks incremental gains.
     */
    private double evaluate(GameState state, int playerId) {
        int foe = 1 - playerId;
        if (state.winner().isPresent()) return state.winner().getAsInt() == playerId ? 1e9 : -1e9;
        double material = materialValue(state, playerId) - materialValue(state, foe);
        double capitals = capitalHealth(state, playerId) - capitalHealth(state, foe);
        double gp = state.player(playerId).currentGp() - state.player(foe).currentGp();
        double cards = state.player(playerId).hand().size() - state.player(foe).hand().size();
        return 10.0 * material + 30.0 * capitals + 1.5 * gp + 2.0 * cards;
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
                BoardPosition target = new BoardPosition(Integer.parseInt(parts[3]), Integer.parseInt(parts[4]));
                CardInstance card = state.board().topAt(target).flatMap(state::card).orElseThrow();
                yield card.definition().isPermanent() ? 115 : 105;
            }
            case "play" -> {
                int index = Integer.parseInt(parts[1]);
                CardType type = state.card(state.player(playerId).hand().get(index)).orElseThrow().definition().type();
                yield switch (type) {
                    case LAND -> 90;
                    case STRUCTURE -> 85;
                    case CHARACTER -> 80;
                    default -> 0;
                };
            }
            case "burrow" -> 82;
            case "blink" -> 45;
            case "move" -> 35;
            case "activate" -> 75;
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

    private int capitalSynergy(GameState state, int playerId, String action) {
        CapitalPassive passive = state.capitalPassiveFor(playerId).orElse(null);
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
