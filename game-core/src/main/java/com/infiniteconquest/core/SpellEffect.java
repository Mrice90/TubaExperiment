package com.infiniteconquest.core;

import java.util.Objects;

public record SpellEffect(SpellEffectType type, int amount, SpellTarget target) {
    public SpellEffect {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(target, "target");
        if (amount < 1) throw new IllegalArgumentException("Spell effect amount must be positive");
    }
}
