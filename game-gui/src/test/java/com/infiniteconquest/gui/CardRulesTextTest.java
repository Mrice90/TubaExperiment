package com.infiniteconquest.gui;
import com.infiniteconquest.cli.*;
import com.infiniteconquest.core.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class CardRulesTextTest {
    @Test void inspectionIncludesCharacterKeywordsCapitalPassiveTerrainAndLiveStats(){
        var pool=new PrototypeCardPool();var state=new GameState(1L);
        var card=new CardInstance(UUID.randomUUID(),pool.require("poseidon_reefline_defender"),0,Zone.HAND);state.register(card);card.addCombatDamage(1);
        String text=CardInspectionPanel.details(state,card);
        assertTrue(text.contains("Defense remaining 2 / 3"));assertTrue(text.contains("Blocks line of sight"));assertTrue(text.contains("background:#1c2835"));
        assertTrue(CardRulesText.details(pool.require("zeus_keyword_stormgate_sentinel")).contains("+1 Attack and +1 Range"));
        assertTrue(CardRulesText.details(pool.require("zeus_land_thunderstep_plateau")).contains("Adds 1 height"));
        assertTrue(CardRulesText.details(new CapitalRoster().require("zeus_capital_cloud_throne")).contains("+2 Defense"));
    }
    @Test void spellDescriptionsAndPreviewsExplainThresholdsAndTemporaryEffects(){
        var pool=new PrototypeCardPool();var strike=pool.require("zeus_chain_lightning");
        assertTrue(CardRulesText.spellSummary(strike).contains("Defense 4 or less"));
        assertTrue(CardRulesText.details(strike).contains("either player"));
        var target=new CardInstance(UUID.randomUUID(),pool.require("poseidon_keyword_maelstrom_bulwark"),1,Zone.BATTLEFIELD);target.addCombatDamage(7);
        assertTrue(ReactionPreview.outcome(strike,target).contains("Will NOT destroy"));
        assertTrue(CardRulesText.spellSummary(pool.require("zeus_stormcharge")).contains("owner's next turn"));
        for(var c:pool.cards())if(c.type()==CardType.SPELL)assertFalse(CardRulesText.spellSummary(c).isBlank(),c.id());
    }
}
