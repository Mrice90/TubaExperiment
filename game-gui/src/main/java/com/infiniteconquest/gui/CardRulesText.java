package com.infiniteconquest.gui;
import com.infiniteconquest.core.*;

/** One rules description shared by deck building, inspection and reactions. */
final class CardRulesText {
    static String details(CardDefinition c){
        StringBuilder text=new StringBuilder("<html><body style='font-family:sans-serif;font-size:14pt;color:#edf0f1;background:#1c2835;padding:12px'><h2>"+escape(c.name())+"</h2><p>"+c.faction()+" · "+c.type()+" · "+cost(c)+"</p>");
        if(!c.archetypes().isEmpty())text.append("<p>Archetypes: ").append(escape(String.join(" · ",c.archetypes()).replace('_',' '))).append("</p>");
        if(c.type()==CardType.CHARACTER)text.append("<p>Attack ").append(c.attack()).append(" · Defense ").append(c.defense()).append("<br>Move ").append(c.movement()).append(" · Range ").append(c.range()).append("</p>");
        if(c.isPermanent())text.append("<p>HP ").append(c.hitPoints()).append(" · +").append(c.type()==CardType.CAPITAL?1:c.income()).append(" GP/turn</p>");
        for(var keyword:c.keywords())text.append("<p><b>").append(keyword.name().replace('_',' ')).append("</b><br>").append(switch(keyword){
            case BLINK -> "Once per personal turn, move to any empty hex without spending normal movement.";
            case MOLE -> "May deploy beneath a controlled Land using Burrow.";
            case VANGUARD -> "Blocks line of sight while on top of its stack.";
            case FAST_STRIKE -> "Prevents retaliation when this attack strictly exceeds the defender's Defense.";
            case SIEGE -> "Deals double attack damage to Lands, Structures and Capitals.";
            case SHARP_SHOT -> "Gains +1 Attack and +1 Range on top of a friendly Structure or Capital.";
            default -> TerrainRules.describe(c,keyword);
        }).append("</p>");
        if(c.type()==CardType.CAPITAL)text.append("<h3>Capital passive</h3><p>").append(escape(new CapitalPassiveRules().description(c))).append("</p>");
        for(SpellEffect e:c.effects())text.append("<p>").append(escape(spellEffect(e))).append("</p>");
        if(c.type()==CardType.SPELL)text.append("<p>May be cast during either player’s Play Phase, including a reaction window. Spend banked gold; targets must be the exposed top card. Spells resolve immediately.</p>");
        for(CardAbility a:c.abilities())text.append("<p>").append(switch(a.trigger()){case ENTERS_PLAY->"When played";case DESTROYED->"When destroyed";case PASSIVE->"Start of your turn";case ACTIVATED->"Activate once per turn ("+a.gpCost()+" GP)";}).append(": ").append(switch(a.effect()){
            case DRAW_CARD->"draw "+a.amount()+" card(s)";
            case DRAW_CHARACTER->"draw "+a.amount()+" next Character(s) from your deck";
            case DRAW_STRUCTURE->"draw "+a.amount()+" next Structure(s) from your deck";
            case GAIN_GP->"gain "+a.amount()+" GP";
            case HEAL_SELF->"heal this card for "+a.amount();
            case HEAL_CAPITAL->"heal your Capital for "+a.amount();
            case BUFF_SELF_ATTACK->"gain +"+a.amount()+" Attack this turn";
            case BUFF_SELF_DEFENSE->"gain +"+a.amount()+" Defense this turn";
            case DAMAGE_ENEMY_CAPITAL->"deal "+a.amount()+" damage to the enemy Capital";
        }).append(".</p>");
        String passive=DevelopmentRules.passiveText(c.developmentPassive());if(!passive.isBlank())text.append("<p>").append(escape(passive)).append("</p>");
        return text.append("</body></html>").toString();
    }
    private static String cost(CardDefinition c){return c.type()==CardType.LAND||c.type()==CardType.STRUCTURE?"Turn "+Math.max(1,c.cost())+" · "+(c.developmentGoldCost()==0?"free":c.developmentGoldCost()+" Gold"):c.cost()+" GP";}
    static String spellEffect(SpellEffect e) {
        String side = e.target()==SpellTarget.ENEMY ? "enemy" : e.target()==SpellTarget.FRIENDLY ? "friendly" : "eligible";
        return switch(e.type()) {
            case STRIKE_CHARACTER -> "Destroy an " + side + " Character with effective Defense " + e.amount() + " or less. This is a threshold, not damage; marked damage does not lower it.";
            case DAMAGE_PERMANENT -> "Deal " + e.amount() + " damage to an " + side + " Land, Structure or Capital.";
            case HEAL_PERMANENT -> "Restore up to " + e.amount() + " lost HP to a " + side + " Land, Structure or Capital. Cannot exceed maximum HP.";
            case RETURN_CHARACTER -> "Return an " + side + " Character to its owner's hand. It must be paid for again when replayed.";
            case BUFF_ATTACK -> "Give a " + side + " Character +" + e.amount() + " Attack until that Character's owner's next turn.";
            case BUFF_DEFENSE -> "Give a " + side + " Character +" + e.amount() + " Defense until that Character's owner's next turn.";
            case TELEPORT_CHARACTER -> "Move a " + side + " Character to any empty hex. Choose the Character, then its destination; entry effects still apply.";
        };
    }
    static String spellSummary(CardDefinition c) {
        return c.effects().stream().map(CardRulesText::spellEffect).collect(java.util.stream.Collectors.joining(" "));
    }
    private static String escape(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}
}
