package com.infiniteconquest.data;

import com.infiniteconquest.core.CardType;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class CardCatalogTest {
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
