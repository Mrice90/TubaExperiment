package com.infiniteconquest.data;

import com.infiniteconquest.core.CardDefinition;
import com.infiniteconquest.core.CardType;
import com.infiniteconquest.core.SpellEffect;
import com.infiniteconquest.core.DevelopmentPassive;
import com.infiniteconquest.core.DevelopmentRules;
import com.infiniteconquest.core.CardAbility;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.Map;
import com.infiniteconquest.core.KeywordValue;

public record CardData(
        String id,
        String name,
        CardType type,
        String faction,
        int cost,
        int attack,
        int defense,
        int range,
        int movement,
        int hitPoints,
        List<Keyword> keywords,
        List<SpellEffect> effects,
        String rulesText,
        String description,
        int rarity,
        ContentStatus contentStatus,
        Integer gpGeneration,
        DevelopmentPassive developmentPassive,
        List<CardAbility> abilities,
        Map<Keyword, KeywordValue> keywordValues,
        Set<String> archetypes,
        int developmentGoldCost
) {
    public CardData {
        if (id == null || !id.matches("[a-z0-9]+(?:_[a-z0-9]+)*")) {
            throw new IllegalArgumentException("Card ID must be a stable lowercase snake_case identifier");
        }
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Card name is required");
        Objects.requireNonNull(type, "type");
        if (faction == null || faction.isBlank()) throw new IllegalArgumentException("Faction is required");
        if (cost < 0 || attack < 0 || defense < 0 || range < 0 || movement < 0 || hitPoints < 0 || rarity < 0
                || (gpGeneration != null && gpGeneration < 0)) {
            throw new IllegalArgumentException("Card numbers cannot be negative");
        }
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
        effects = effects == null ? List.of() : List.copyOf(effects);
        abilities = abilities == null ? List.of() : List.copyOf(abilities);
        if (keywords.stream().anyMatch(Objects::isNull) || effects.stream().anyMatch(Objects::isNull)
                || abilities.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Keywords, effects and abilities cannot contain null");
        }
        rulesText = rulesText == null ? "" : rulesText;
        description = description == null ? "" : description;
        Objects.requireNonNull(contentStatus, "contentStatus");
    }

    public CardDefinition toDefinition() {
        return new CardDefinition(id, name, type, faction, cost, attack, defense, movement, range,
                hitPoints, Set.copyOf(keywords), effects,
                gpGeneration == null ? DevelopmentRules.standardGp(type, cost) : gpGeneration,
                developmentPassive == null ? DevelopmentPassive.NONE : developmentPassive,
                abilities, keywordValues, archetypes, developmentGoldCost);
    }
}
