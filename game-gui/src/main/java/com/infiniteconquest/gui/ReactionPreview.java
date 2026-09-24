package com.infiniteconquest.gui;
import com.infiniteconquest.core.*;
final class ReactionPreview {
    static String outcome(CardDefinition spell,CardInstance target){
        return spell.effects().stream().map(e->switch(e.type()){
            case STRIKE_CHARACTER -> e.amount()>=target.effectiveDefense()?"Destroy this Character":"Will NOT destroy: Defense "+target.effectiveDefense()+" exceeds threshold "+e.amount();
            case DAMAGE_PERMANENT -> "Deal "+e.amount()+" damage"+(target.damage()+e.amount()>=target.definition().hitPoints()?" · destroys target":"");
            case HEAL_PERMANENT -> "Restore "+Math.min(e.amount(),target.damage())+" HP"+(target.damage()==0?" · already at full HP":"");
            case RETURN_CHARACTER -> "Return to owner's hand";
            case BUFF_ATTACK -> "+"+e.amount()+" Attack until owner's next turn";
            case BUFF_DEFENSE -> "Defense "+target.effectiveDefense()+" → "+(target.effectiveDefense()+e.amount())+" until owner's next turn";
            case TELEPORT_CHARACTER -> "Choose an empty destination next · entry effects apply";
        }).collect(java.util.stream.Collectors.joining("; "));
    }
}
