package com.infiniteconquest.data;

import com.infiniteconquest.core.AbilityTrigger;
import com.infiniteconquest.core.CardType;
import com.infiniteconquest.core.DevelopmentPassive;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class CardCatalogTest {
    /**
     * Locks the land/structure philosophy in docs/faction-themes.md: every
     * LAND or STRUCTURE must carry a decision point or trigger — an
     * ACTIVATED/triggered ability, an aura keyword, or a deploy passive
     * (the two "Deploy:" cards, whose spec locks them against redesign).
     * Every card with abilities must also have rules text.
     */
    private static final Set<Keyword> AURA_KEYWORDS = EnumSet.of(
            Keyword.WAYSTATION, Keyword.HIGH_GROUND, Keyword.BULWARK, Keyword.MEDIC_TENT);

    @Test
    void factionCardsLandsAndStructuresAreNeverVanilla() {
        CardCatalog catalog = CardCatalog.loadResource("/cards/faction-cards.json");

        for (CardData card : catalog.cards()) {
            if (card.type() != CardType.LAND && card.type() != CardType.STRUCTURE) continue;
            boolean hasAbility = !card.abilities().isEmpty();
            boolean hasAura = card.keywords().stream().anyMatch(AURA_KEYWORDS::contains);
            boolean hasDeploy = card.developmentPassive() != null
                    && card.developmentPassive() != DevelopmentPassive.NONE;
            assertTrue(hasAbility || hasAura || hasDeploy,
                    "Vanilla land/structure: " + card.id());
            if (hasAbility) {
                assertFalse(card.rulesText().isBlank(),
                        "Card with abilities has no rulesText: " + card.id());
            }
            if (card.abilities().stream().anyMatch(a -> a.trigger() == AbilityTrigger.ACTIVATED)) {
                assertTrue(card.rulesText().contains("Activate once per turn"),
                        "Activated ability not described: " + card.id() + " -> " + card.rulesText());
            }
        }

        assertFalse(catalog.cards().stream()
                .noneMatch(c -> c.id().equals("zeus_ion_storm_lattice")), "new Zeus structure missing");
        assertFalse(catalog.cards().stream()
                .noneMatch(c -> c.id().equals("poseidon_moonwell_tidegate")), "new Poseidon structure missing");
        assertFalse(catalog.cards().stream()
                .noneMatch(c -> c.id().equals("poseidon_drowned_archive")), "new Poseidon land missing");
    }

    @Test
    void loadsDrivePrototypeCharactersWithStableIdsAndTypedKeywords() {
        CardCatalog catalog = CardCatalog.loadResource("/cards/prototype-characters.json");

        assertEquals(5, catalog.cards().size());
        CardData talus = catalog.require("neo_proto_talus_defender");
        assertEquals("Talus Defender", talus.name());
        assertEquals(CardType.CHARACTER, talus.type());
        assertEquals(3, talus.cost());
        assertEquals(1, talus.attack());
        assertEquals(4, talus.defense());
        assertEquals(1, talus.range());
        assertEquals(1, talus.movement());
        assertEquals(ContentStatus.PROTOTYPE, talus.contentStatus());
        assertEquals(java.util.List.of(Keyword.VANGUARD), talus.keywords());

        CardData zephyr = catalog.require("neo_proto_zephyr_scout");
        assertTrue(zephyr.keywords().contains(Keyword.BLINK));
        assertEquals(5, catalog.definitions().size());
    }

    @Test
    void rejectsUnsupportedSchemaAndDuplicateIds() {
        assertThrows(IllegalArgumentException.class, () -> load("""
                {"schemaVersion":2,"cards":[]}
                """));
        assertThrows(IllegalArgumentException.class, () -> load("""
                {"schemaVersion":1,"cards":[
                  {"id":"same_id","name":"A","type":"CHARACTER","faction":"UNASSIGNED",
                   "cost":0,"attack":0,"defense":0,"range":0,"movement":0,"hitPoints":0,
                   "keywords":[],"rulesText":"","description":"","rarity":0,"contentStatus":"PROTOTYPE"},
                  {"id":"same_id","name":"B","type":"CHARACTER","faction":"UNASSIGNED",
                   "cost":0,"attack":0,"defense":0,"range":0,"movement":0,"hitPoints":0,
                   "keywords":[],"rulesText":"","description":"","rarity":0,"contentStatus":"PROTOTYPE"}
                ]}
                """));
    }

    @Test
    void registryRequiresTypedUniqueHandlers() {
        KeywordEffectRegistry<String> registry = new KeywordEffectRegistry<>();
        AtomicBoolean invoked = new AtomicBoolean();
        registry.register(Keyword.BLINK, ignored -> invoked.set(true));

        assertTrue(registry.supports(Keyword.BLINK));
        registry.require(Keyword.BLINK).apply("context");
        assertTrue(invoked.get());
        assertThrows(IllegalStateException.class,
                () -> registry.register(Keyword.BLINK, ignored -> {}));
        assertThrows(IllegalStateException.class,
                () -> registry.require(Keyword.MOLE));
    }

    private static CardCatalog load(String json) {
        return CardCatalog.load(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }
}
