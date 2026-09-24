package com.infiniteconquest.gui;
import com.infiniteconquest.cli.PrototypeCardPool;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TerrainDetailsTest {
    @Test void editorShowsTurnGoldArchetypeAndExactFunctionalParameters() {
        var pool=new PrototypeCardPool();String turret=DeckBuilderDialog.details(pool.require("zeus_apex_worldstorm_spire"));
        assertTrue(turret.contains("Turn 8 · 3 Gold"));assertTrue(turret.contains("range 2"));assertTrue(turret.contains("takes 2"));
        assertTrue(DeckBuilderDialog.details(pool.require("zeus_cloudline_courier")).contains("HUMAN"));
        String basic=DeckBuilderDialog.details(pool.require("zeus_olympian_cloudbank"));assertTrue(basic.contains("free"));
        assertTrue(DeckBuilderDialog.details(pool.require("poseidon_structure_pearl_infirmary")).contains("heals 2"));
    }
}
