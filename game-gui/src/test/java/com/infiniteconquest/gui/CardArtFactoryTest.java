package com.infiniteconquest.gui;

import com.infiniteconquest.cli.PrototypeCardPool;
import com.infiniteconquest.core.CardDefinition;
import com.infiniteconquest.core.CardType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.DynamicTest;

import javax.swing.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class CardArtFactoryTest {
    private static final List<String> CAPITAL_IDS = List.of(
            "zeus_capital_olympus_citadel", "zeus_capital_keraunos_spire", "zeus_capital_cloud_throne",
            "poseidon_capital_atlantis_nexus", "poseidon_capital_trident_bastion", "poseidon_capital_abyssal_court");
    private static final List<String> PAINTED_ZEUS_CHARACTERS = List.of(
            "zeus_cloudline_courier", "zeus_aegis_airguard", "zeus_tempest_oracle",
            "zeus_ability_skyline_seer", "zeus_eagle_of_the_high_grid",
            "zeus_apex_olympian_storm_titan", "zeus_arc_relay_scout",
            "zeus_thunderhead_skirmisher", "zeus_iris_signal_runner",
            "zeus_cyclone_marksman", "zeus_boltwing_cavalier",
            "zeus_hera_protocol_warden", "zeus_keyword_sparkstep_runner",
            "zeus_fast_tempest_duelist", "zeus_keyword_stormgate_sentinel",
            "zeus_keyword_cloudline_raider", "zeus_sharp_aether_spotter",
            "zeus_stormgate_adept", "zeus_keyword_thunderhead_guardian",
            "zeus_siege_thunder_ram", "zeus_apex_keraunos_seraph",
            "zeus_keraunos_prime", "zeus_apex_skyfather_archon",
            "zeus_keyword_aetherbolt_avatar");
    private static final List<String> PAINTED_ZEUS_SPELLS = List.of(
            "zeus_chain_lightning", "zeus_skybreaker_bolt", "zeus_windstep_protocol",
            "zeus_stormcharge", "zeus_aegis_of_the_sky",
            "zeus_apex_thunder_gods_verdict", "zeus_apex_wrath_of_olympus",
            "zeus_apex_divine_tailwind", "zeus_apex_crownstorm_ascendance",
            "zeus_apex_imperial_sky_aegis");
    private static final List<String> PAINTED_ZEUS_LANDS = List.of(
            "zeus_olympian_cloudbank", "zeus_ionized_skyway", "zeus_eagles_perch_array",
            "zeus_throneward_conduit", "zeus_land_thunderstep_plateau", "zeus_land_aurora_reach",
            "zeus_ability_stormfront", "zeus_land_dawncloud_step", "zeus_land_empyrean_current",
            "zeus_apex_celestial_throne_grid", "zeus_tutor_land_1", "zeus_tutor_land_2",
            "zeus_tutor_land_3", "zeus_tutor_land_4", "zeus_tutor_land_5");
    private static final List<String> PAINTED_ZEUS_STRUCTURES = List.of(
            "zeus_storm_relay_pylon", "zeus_cloudwall_bastion", "zeus_keraunos_charging_spire",
            "zeus_zeus_command_nexus", "zeus_structure_stormglass_relay", "zeus_structure_cloud_archive",
            "zeus_ability_oracle_spire", "zeus_structure_aegis_conductor",
            "zeus_structure_oracle_of_storms", "zeus_apex_worldstorm_spire",
            "zeus_tutor_structure_1", "zeus_tutor_structure_2", "zeus_tutor_structure_3",
            "zeus_tutor_structure_4", "zeus_tutor_structure_5");
    private static final List<String> PAINTED_POSEIDON_SPELLS = List.of(
            "poseidon_crushing_depths", "poseidon_erode_foundation",
            "poseidon_restorative_tide", "poseidon_undertow_recall",
            "poseidon_tidal_armor", "poseidon_apex_maelstrom_verdict");
    private static final List<String> PAINTED_POSEIDON_FOUNDATIONAL_LANDS = List.of(
            "poseidon_neon_tidelands", "poseidon_coral_data_reef",
            "poseidon_abyssal_pressure_trench", "poseidon_palace_of_tides_approach",
            "poseidon_ability_healing_shoal");
    private static final List<String> PAINTED_POSEIDON_ECONOMY_AND_TUTOR_LANDS = List.of(
            "poseidon_land_saltmarsh_harbor", "poseidon_land_coral_tributary",
            "poseidon_land_leviathan_shelf", "poseidon_land_pelagic_kingdom",
            "poseidon_tutor_land_1", "poseidon_tutor_land_2", "poseidon_tutor_land_3",
            "poseidon_tutor_land_4", "poseidon_tutor_land_5");
    private static final List<String> PAINTED_POSEIDON_APEX_LANDS = List.of(
            "poseidon_apex_atlantis_crown_basin", "poseidon_apex_oceanus_current_vault",
            "poseidon_apex_leviathan_nursery_trench", "poseidon_apex_trident_confluence",
            "poseidon_apex_worldsea_platform");
    private static final List<String> PAINTED_POSEIDON_CHARACTERS = List.of(
            "poseidon_tidepool_surveyor", "poseidon_nereid_current_rider",
            "poseidon_reefline_defender", "poseidon_undertow_stalker",
            "poseidon_keyword_reef_tunneler", "poseidon_triton_waveguard",
            "poseidon_delphic_sonar_adept", "poseidon_keyword_breakwater_hoplite",
            "poseidon_fast_razorfin_lancer", "poseidon_kraken_tendril_drone",
            "poseidon_naiad_flowshaper", "poseidon_abyssal_molecrab",
            "poseidon_oceanid_pressure_mage", "poseidon_ability_reefwarden",
            "poseidon_keyword_trench_stalker", "poseidon_sharp_tidewall_harpooner",
            "poseidon_leviathan_wakeborn", "poseidon_keyword_maelstrom_bulwark",
            "poseidon_poseidons_trident_core", "poseidon_keyword_abyssal_leviathan",
            "poseidon_apex_atlantis_tide_sovereign", "poseidon_apex_kraken_prime_avatar",
            "poseidon_apex_abysswalker_nereid", "poseidon_siege_kraken_sapper");

    @Test void everyPlayableZeusCardHasPaintedArt() {
        List<CardDefinition> cards = new PrototypeCardPool().cardsForFaction("ZEUS");
        assertEquals(64, cards.size());
        cards.forEach(card -> assertTrue(CardArtFactory.hasPaintedArt(card), card.id()));
    }

    @Test void packagesFactionWorldsAndRendersDistinctCardIllustrations() {
        assertNotNull(CardArtFactory.class.getResource("/art/faction-environments.png"));
        assertTrue(VisualEffects.available(), "CC0 particle textures should be packaged");
        CardDefinition storm = new CardDefinition("test_storm_seer", "Storm Seer",
                CardType.CHARACTER, "ZEUS", 3, 3, 3, 2, 2);
        CardDefinition reef = new CardDefinition("test_reef_bastion", "Reef Bastion",
                CardType.STRUCTURE, "POSEIDON", 2, 0, 0, 0, 0, 8);

        ImageIcon first = CardArtFactory.iconFor(storm, 190, 78);
        ImageIcon second = CardArtFactory.iconFor(reef, 190, 78);
        assertEquals(190, first.getIconWidth());
        assertEquals(78, first.getIconHeight());
        assertNotEquals(pixelHash(first), pixelHash(second));
        assertSame(first, CardArtFactory.iconFor(storm, 190, 78), "rendered art should be cached");
        ImageIcon board = CardArtFactory.boardTokenIcon(storm);
        assertEquals(320, board.getIconWidth());
        assertEquals(280, board.getIconHeight());
    }

    @Test void packagesAUniquePaintedIllustrationForEveryCapital() {
        for (String id : CAPITAL_IDS) {
            assertNotNull(CardArtFactory.class.getResource("/art/capitals/" + id + ".jpg"), id);
        }
        CardDefinition capital = new CardDefinition("zeus_capital_olympus_citadel", "Olympus Citadel",
                CardType.CAPITAL, "ZEUS", 0, 0, 0, 0, 0, 20);
        assertTrue(CardArtFactory.hasPaintedArt(capital));
        ImageIcon wide = CardArtFactory.iconFor(capital, 300, 120);
        ImageIcon compact = CardArtFactory.boardTokenIcon(capital);
        assertEquals(300, wide.getIconWidth());
        assertEquals(120, wide.getIconHeight());
        assertEquals(320, compact.getIconWidth());
        assertEquals(280, compact.getIconHeight());
    }

    @Test void packagesThePaintedZeusCharacterRollout() {
        for (String id : PAINTED_ZEUS_CHARACTERS) {
            assertNotNull(CardArtFactory.class.getResource("/art/characters/" + id + ".jpg"), id);
        }
        CardDefinition courier = new CardDefinition("zeus_cloudline_courier", "Cloudline Courier",
                CardType.CHARACTER, "ZEUS", 1, 1, 1, 4, 1);
        CardDefinition awaitingArt = new CardDefinition("zeus_future_character", "Future Character",
                CardType.CHARACTER, "ZEUS", 1, 1, 1, 1, 1);
        assertTrue(CardArtFactory.hasPaintedArt(courier));
        assertFalse(CardArtFactory.hasPaintedArt(awaitingArt));
        assertEquals(190, CardArtFactory.iconFor(courier, 190, 78).getIconWidth());
        assertEquals(280, CardArtFactory.boardTokenIcon(courier).getIconHeight());
    }

    @Test void packagesThePaintedZeusSpellRollout() {
        for (String id : PAINTED_ZEUS_SPELLS) {
            assertNotNull(CardArtFactory.class.getResource("/art/spells/" + id + ".jpg"), id);
        }
        CardDefinition chainLightning = new PrototypeCardPool().require("zeus_chain_lightning");
        CardDefinition awaitingArt = new CardDefinition("zeus_future_spell", "Future Spell",
                CardType.SPELL, "ZEUS", chainLightning.cost(), 0, 0, 0, 0, 0,
                chainLightning.keywords(), chainLightning.effects());
        assertTrue(CardArtFactory.hasPaintedArt(chainLightning));
        assertFalse(CardArtFactory.hasPaintedArt(awaitingArt));
        assertEquals(190, CardArtFactory.iconFor(chainLightning, 190, 78).getIconWidth());
        assertEquals(280, CardArtFactory.boardTokenIcon(chainLightning).getIconHeight());
    }

    @Test void packagesThePaintedZeusLandRollout() {
        for (String id : PAINTED_ZEUS_LANDS) {
            assertNotNull(CardArtFactory.class.getResource("/art/lands/" + id + ".jpg"), id);
        }
        CardDefinition cloudbank = new CardDefinition("zeus_olympian_cloudbank", "Olympian Cloudbank",
                CardType.LAND, "ZEUS", 0, 0, 0, 0, 0, 5);
        CardDefinition awaitingArt = new CardDefinition("zeus_future_land", "Future Land",
                CardType.LAND, "ZEUS", 0, 0, 0, 0, 0, 5);
        assertTrue(CardArtFactory.hasPaintedArt(cloudbank));
        assertFalse(CardArtFactory.hasPaintedArt(awaitingArt));
        assertEquals(190, CardArtFactory.iconFor(cloudbank, 190, 78).getIconWidth());
        assertEquals(280, CardArtFactory.boardTokenIcon(cloudbank).getIconHeight());
    }

    @Test void packagesThePaintedZeusStructureRollout() {
        for (String id : PAINTED_ZEUS_STRUCTURES) {
            assertNotNull(CardArtFactory.class.getResource("/art/structures/" + id + ".jpg"), id);
        }
        CardDefinition pylon = new CardDefinition("zeus_storm_relay_pylon", "Storm Relay Pylon",
                CardType.STRUCTURE, "ZEUS", 1, 0, 0, 0, 0, 5);
        CardDefinition awaitingArt = new CardDefinition("zeus_future_structure", "Future Structure",
                CardType.STRUCTURE, "ZEUS", 1, 0, 0, 0, 0, 5);
        assertTrue(CardArtFactory.hasPaintedArt(pylon));
        assertFalse(CardArtFactory.hasPaintedArt(awaitingArt));
        assertEquals(190, CardArtFactory.iconFor(pylon, 190, 78).getIconWidth());
        assertEquals(280, CardArtFactory.boardTokenIcon(pylon).getIconHeight());
    }

    @Test void everyPlayablePoseidonSpellHasPaintedArt() {
        List<CardDefinition> spells = new PrototypeCardPool().cardsForFaction("POSEIDON").stream()
                .filter(card -> card.type() == CardType.SPELL).toList();
        assertEquals(PAINTED_POSEIDON_SPELLS.size(), spells.size());
        for (String id : PAINTED_POSEIDON_SPELLS) {
            assertNotNull(CardArtFactory.class.getResource("/art/spells/" + id + ".jpg"), id);
            assertTrue(CardArtFactory.hasPaintedArt(new PrototypeCardPool().require(id)), id);
        }
        CardDefinition crushingDepths = new PrototypeCardPool().require("poseidon_crushing_depths");
        CardDefinition awaitingArt = new CardDefinition("poseidon_future_spell", "Future Spell",
                CardType.SPELL, "POSEIDON", crushingDepths.cost(), 0, 0, 0, 0, 0,
                crushingDepths.keywords(), crushingDepths.effects());
        assertFalse(CardArtFactory.hasPaintedArt(awaitingArt));
        assertEquals(190, CardArtFactory.iconFor(crushingDepths, 190, 78).getIconWidth());
    }

    @Test void everyPlayablePoseidonLandHasPaintedArt() {
        List<CardDefinition> lands = new PrototypeCardPool().cardsForFaction("POSEIDON").stream()
                .filter(card -> card.type() == CardType.LAND).toList();
        assertEquals(19, lands.size());
        for (CardDefinition land : lands) {
            assertNotNull(CardArtFactory.class.getResource("/art/lands/" + land.id() + ".jpg"), land.id());
        }
        CardDefinition tidelands = new PrototypeCardPool().require("poseidon_neon_tidelands");
        assertTrue(CardArtFactory.hasPaintedArt(tidelands));
        CardDefinition awaitingArt = new CardDefinition("poseidon_future_land", "Future Land",
                CardType.LAND, "POSEIDON", tidelands.cost(), 0, 0, 0, 0, tidelands.hitPoints());
        assertFalse(CardArtFactory.hasPaintedArt(awaitingArt));
        assertEquals(190, CardArtFactory.iconFor(tidelands, 190, 78).getIconWidth());
        assertEquals(280, CardArtFactory.boardTokenIcon(tidelands).getIconHeight());
    }

    @TestFactory Stream<DynamicTest> decodesEachPoseidonLandPainting() {
        return new PrototypeCardPool().cardsForFaction("POSEIDON").stream()
                .filter(card -> card.type() == CardType.LAND)
                .map(card -> DynamicTest.dynamicTest(card.id(),
                        () -> assertTrue(CardArtFactory.hasPaintedArt(card), card.id())));
    }

    @Test void everyPlayablePoseidonStructureIsPackaged() {
        List<CardDefinition> structures = new PrototypeCardPool().cardsForFaction("POSEIDON").stream()
                .filter(card -> card.type() == CardType.STRUCTURE).toList();
        assertEquals(15, structures.size());
        for (CardDefinition structure : structures) {
            assertNotNull(CardArtFactory.class.getResource("/art/structures/" + structure.id() + ".jpg"),
                    structure.id());
        }
    }

    @TestFactory Stream<DynamicTest> decodesEachPoseidonStructurePainting() {
        return new PrototypeCardPool().cardsForFaction("POSEIDON").stream()
                .filter(card -> card.type() == CardType.STRUCTURE)
                .map(card -> DynamicTest.dynamicTest(card.id(),
                        () -> assertTrue(CardArtFactory.hasPaintedArt(card), card.id())));
    }

    @Test void everyPlayablePoseidonCharacterHasPaintedArt() {
        for (String id : PAINTED_POSEIDON_CHARACTERS) {
            assertNotNull(CardArtFactory.class.getResource("/art/characters/" + id + ".jpg"), id);
            assertTrue(CardArtFactory.hasPaintedArt(new PrototypeCardPool().require(id)), id);
        }
        CardDefinition surveyor = new PrototypeCardPool().require("poseidon_tidepool_surveyor");
        CardDefinition awaitingArt = new CardDefinition("poseidon_future_character", "Future Character",
                CardType.CHARACTER, "POSEIDON", 1, 1, 1, 1, 1);
        assertFalse(CardArtFactory.hasPaintedArt(awaitingArt));
        assertEquals(190, CardArtFactory.iconFor(surveyor, 190, 78).getIconWidth());
        assertEquals(280, CardArtFactory.boardTokenIcon(surveyor).getIconHeight());
    }

    private int pixelHash(ImageIcon icon) {
        BufferedImage image = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
        image.getGraphics().drawImage(icon.getImage(), 0, 0, null);
        int hash = 1;
        for (int y = 0; y < image.getHeight(); y += 5) {
            for (int x = 0; x < image.getWidth(); x += 5) hash = 31 * hash + image.getRGB(x, y);
        }
        return hash;
    }
}
