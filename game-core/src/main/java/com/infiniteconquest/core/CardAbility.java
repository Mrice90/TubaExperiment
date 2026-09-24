package com.infiniteconquest.core;

import java.util.Objects;

public record CardAbility(AbilityTrigger trigger, AbilityEffectType effect, int amount, int gpCost) {
    public CardAbility {
        Objects.requireNonNull(trigger, "trigger");
        Objects.requireNonNull(effect, "effect");
        if (amount <= 0) throw new IllegalArgumentException("Ability amount must be positive");
        if (gpCost < 0) throw new IllegalArgumentException("Ability GP cost cannot be negative");
        if (trigger != AbilityTrigger.ACTIVATED && gpCost != 0) {
            throw new IllegalArgumentException("Only activated abilities may have a GP cost");
        }
    }
}
