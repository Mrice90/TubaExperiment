package com.infiniteconquest.gui;

import com.infiniteconquest.core.BoardPosition;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Glanceable battlefield markers: which Characters can still attack this turn,
 * and which top-of-stack Lands/Structures still have an unused activated
 * ability. Derived from the legal command list, so a marker appears exactly
 * when the action is legal and disappears the moment it is spent.
 */
final class ReadinessIndicators {
    record Readiness(Set<BoardPosition> attackReady, Set<BoardPosition> abilityReady) { }

    private ReadinessIndicators() { }

    static Readiness compute(List<String> legalCommands) {
        Set<BoardPosition> attackReady = new HashSet<>();
        Set<BoardPosition> abilityReady = new HashSet<>();
        for (String command : legalCommands) {
            String[] parts = command.split("\\s+");
            // Only attack/activate carry a source board position in slots 1-2;
            // other commands (play <handIndex> <x> <y>, move, cast, …) do not.
            boolean isAttack = parts.length >= 3 && parts[0].equals("attack");
            boolean isActivate = parts.length == 3 && parts[0].equals("activate");
            if (!isAttack && !isActivate) continue;
            try {
                BoardPosition from = new BoardPosition(
                        Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
                if (isAttack) attackReady.add(from);
                else abilityReady.add(from);
            } catch (NumberFormatException ignored) { }
        }
        return new Readiness(attackReady, abilityReady);
    }
}
