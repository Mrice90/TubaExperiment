package com.infiniteconquest.cli;

import com.infiniteconquest.core.*;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class CommandProcessor {
    private final GameState state;
    private final GameEngine engine;
    private final BattlefieldRenderer renderer;
    private final ActionHints hints;
    private boolean quit;

    public CommandProcessor(GameState state) {
        this(state, new GameEngine(), new BattlefieldRenderer(), new ActionHints());
    }

    CommandProcessor(GameState state, GameEngine engine, BattlefieldRenderer renderer, ActionHints hints) {
        this.state = state;
        this.engine = engine;
        this.renderer = renderer;
        this.hints = hints;
    }

    public boolean quitRequested() { return quit; }

    public String execute(String input) {
        if (input == null) { quit = true; return "Input closed."; }
        String trimmed = input.trim();
        if (trimmed.isEmpty()) return "";
        String[] parts = trimmed.split("\\s+");
        String command = parts[0].toLowerCase(Locale.ROOT);
        try {
            return switch (command) {
                case "help" -> help();
                case "board", "hand" -> renderer.render(state);
                case "inspect" -> inspect(parts);
                case "actions" -> String.join(System.lineSeparator(), hints.forActivePlayer(state, engine));
                case "play" -> apply(play(parts, false));
                case "burrow" -> apply(play(parts, true));
                case "move" -> apply(boardAction(parts, "move"));
                case "blink" -> apply(boardAction(parts, "blink"));
                case "attack" -> apply(boardAction(parts, "attack"));
                case "activate" -> apply(activate(parts));
                case "cast" -> apply(spell(parts, state.activePlayer(), 1));
                case "react" -> {
                    if (parts.length < 2) throw new IllegalArgumentException("Reaction requires a player number");
                    int player = number(parts[1]);
                    if (player < 0 || player > 1 || player == state.activePlayer()) {
                        throw new IllegalArgumentException("Reaction player must be the inactive player");
                    }
                    yield apply(spell(parts, player, 2));
                }
                case "end" -> apply(new GameAction.EndTurn(state.activePlayer()));
                case "quit", "exit" -> { quit = true; yield "Match closed."; }
                default -> "Unknown command. Type help.";
            };
        } catch (IllegalArgumentException | IndexOutOfBoundsException exception) {
            return "Invalid command: " + exception.getMessage();
        }
    }

    private String inspect(String[] parts) {
        if (parts.length == 2) return renderer.inspectHand(state, number(parts[1]));
        if (parts.length == 3) return renderer.inspectCell(state, position(parts[1], parts[2]));
        throw new IllegalArgumentException("Use inspect <hand#> or inspect <x> <y>");
    }

    private GameAction play(String[] parts, boolean burrow) {
        requireLength(parts, 4);
        int handIndex = number(parts[1]);
        BoardPosition destination = position(parts[2], parts[3]);
        List<UUID> hand = state.player(state.activePlayer()).hand();
        UUID cardId = hand.get(handIndex);
        if (burrow) return new GameAction.BurrowCharacter(state.activePlayer(), cardId, destination);
        CardType type = state.card(cardId).orElseThrow().definition().type();
        return switch (type) {
            case LAND -> new GameAction.PlayLand(state.activePlayer(), cardId, destination);
            case STRUCTURE -> new GameAction.PlayStructure(state.activePlayer(), cardId, destination);
            case CHARACTER -> new GameAction.SummonCharacter(state.activePlayer(), cardId, destination);
            default -> throw new IllegalArgumentException("That card type is not playable yet");
        };
    }

    private GameAction spell(String[] parts, int player, int handOffset) {
        int remaining = parts.length - handOffset;
        if (remaining != 3 && remaining != 5) {
            throw new IllegalArgumentException("Use target coordinates and optional teleport destination");
        }
        int handIndex = number(parts[handOffset]);
        UUID spellId = state.player(player).hand().get(handIndex);
        BoardPosition targetPosition = position(parts[handOffset + 1], parts[handOffset + 2]);
        UUID targetId = state.board().topAt(targetPosition)
                .orElseThrow(() -> new IllegalArgumentException("No spell target at coordinates"));
        BoardPosition destination = remaining == 5
                ? position(parts[handOffset + 3], parts[handOffset + 4]) : null;
        return new GameAction.CastSpell(player, spellId, targetId, destination);
    }

    private GameAction boardAction(String[] parts, String command) {
        requireLength(parts, 5);
        BoardPosition from = position(parts[1], parts[2]);
        BoardPosition to = position(parts[3], parts[4]);
        UUID source = state.board().topAt(from)
                .orElseThrow(() -> new IllegalArgumentException("No card at source"));
        return switch (command) {
            case "move" -> new GameAction.MoveCharacter(state.activePlayer(), source, to);
            case "blink" -> new GameAction.BlinkCharacter(state.activePlayer(), source, to);
            case "attack" -> {
                UUID target = state.board().topAt(to)
                        .orElseThrow(() -> new IllegalArgumentException("No target at destination"));
                yield new GameAction.Attack(state.activePlayer(), source, target);
            }
            default -> throw new IllegalArgumentException("Unsupported board command");
        };
    }

    private GameAction activate(String[] parts) {
        requireLength(parts, 3);
        UUID source = state.board().topAt(position(parts[1], parts[2]))
                .orElseThrow(() -> new IllegalArgumentException("No card at source"));
        return new GameAction.ActivateAbility(state.activePlayer(), source);
    }

    private String apply(GameAction action) {
        ActionResult result = engine.apply(state, action);
        return (result.accepted() ? "OK: " : "REJECTED: ") + result.message();
    }

    private BoardPosition position(String x, String y) {
        return new BoardPosition(number(x), number(y));
    }

    private int number(String text) {
        try { return Integer.parseInt(text); }
        catch (NumberFormatException exception) { throw new IllegalArgumentException("Expected a number"); }
    }

    private void requireLength(String[] parts, int expected) {
        if (parts.length != expected) throw new IllegalArgumentException("Wrong number of arguments");
    }

    public static String help() {
        return """
                Commands:
                  board                         show battlefield and active hand
                  actions                       list currently legal command forms
                  inspect <hand#>               inspect a card in your hand
                  inspect <x> <y>               inspect a battlefield stack
                  play <hand#> <x> <y>          play Land, Structure, or Character
                  burrow <hand#> <x> <y>        place a Mole beneath your top Land
                  move <fromX> <fromY> <x> <y>  move the top Character
                  blink <fromX> <fromY> <x> <y> teleport a Blink Character
                  attack <fromX> <fromY> <x> <y> attack the top enemy card
                  activate <x> <y>              pay GP to use a top card's ability
                  cast <hand#> <x> <y> [toX toY] cast during your turn
                  react <player#> <hand#> <x> <y> [toX toY]
                                                cast using saved GP on the enemy turn
                  end                           end the active player's turn
                  help                          show commands
                  quit                          close the match
                """.strip();
    }
}
