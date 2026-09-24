package com.infiniteconquest.core;

import com.infiniteconquest.data.Keyword;
import java.util.*;

/** Development passives remain active under friendly occupants. Characters do not add height. */
public final class TerrainRules {
    public static Set<Keyword> landKeywords() { return Set.of(Keyword.HIGH_GROUND,Keyword.COVER,Keyword.WAYSTATION,Keyword.FERTILE,Keyword.SANCTUARY,Keyword.ARCHIVE); }
    public static Set<Keyword> structureKeywords() { return Set.of(Keyword.TURRET,Keyword.MEDIC_TENT,Keyword.WATCHTOWER,Keyword.BULWARK,Keyword.WORKSHOP,Keyword.BEACON); }
    public static KeywordValue defaultValue(Keyword k) {
        return switch(k) {
            case TURRET -> new KeywordValue(2,1);
            case MEDIC_TENT, WORKSHOP -> new KeywordValue(1,2);
            case BEACON -> new KeywordValue(1,1);
            default -> new KeywordValue(0,1);
        };
    }
    public static List<CardInstance> stack(GameState state, BoardPosition position) {
        return state.board().stackAt(position).stream().map(id->state.card(id).orElseThrow()).toList();
    }
    public static int height(GameState state, BoardPosition position) {
        return stack(state,position).stream().mapToInt(c -> {
            int value=c.definition().type()==CardType.STRUCTURE || c.definition().type()==CardType.CAPITAL ? 1 : 0;
            return value+(c.definition().hasKeyword(Keyword.HIGH_GROUND)?c.definition().keywordValue(Keyword.HIGH_GROUND).amount():0);
        }).sum();
    }
    public static int eyeLevel(GameState state, BoardPosition position) {
        CardInstance top=state.board().topAt(position).flatMap(state::card).orElse(null);
        int topBody=top!=null && (top.definition().type()==CardType.STRUCTURE || top.definition().type()==CardType.CAPITAL) ? 1 : 0;
        return height(state,position)-topBody+1;
    }
    public static int obstacleHeight(GameState state, BoardPosition position) {
        var cards=stack(state,position);
        boolean building=cards.stream().anyMatch(c->c.definition().type()==CardType.STRUCTURE || c.definition().type()==CardType.CAPITAL);
        boolean guard=!cards.isEmpty() && cards.get(cards.size()-1).definition().hasKeyword(Keyword.VANGUARD);
        return guard?height(state,position)+1:building?height(state,position):0;
    }
    private static boolean active(GameState state,CardInstance source) {
        var p=state.board().positionOf(source.instanceId());
        return source.zone()==Zone.BATTLEFIELD && p.isPresent() && stack(state,p.get()).stream().allMatch(c->c.owner()==source.owner());
    }
    private static List<CardInstance> sources(GameState state) {
        List<CardInstance> result=new ArrayList<>();
        for(BoardPosition p:state.board().positions()) for(CardInstance c:stack(state,p))
            if((c.definition().type()==CardType.LAND || c.definition().type()==CardType.STRUCTURE) && active(state,c)) result.add(c);
        return result;
    }
    public static int rangeBonus(GameState state,CardInstance card) {
        var p=state.board().positionOf(card.instanceId());if(p.isEmpty())return 0;
        return stack(state,p.get()).stream().filter(c->c.owner()==card.owner() && c.definition().hasKeyword(Keyword.WATCHTOWER))
                .mapToInt(c->c.definition().keywordValue(Keyword.WATCHTOWER).amount()).max().orElse(0);
    }
    public static int reduceDamage(GameState state,CardInstance target,int amount,boolean ranged) {
        var p=state.board().positionOf(target.instanceId());if(p.isEmpty())return amount;
        int reduction=0;
        for(CardInstance source:stack(state,p.get())) if(source.owner()==target.owner()) {
            if(ranged && target.definition().type()==CardType.CHARACTER && source.definition().hasKeyword(Keyword.COVER)) reduction=Math.max(reduction,source.definition().keywordValue(Keyword.COVER).amount());
            if(source.definition().hasKeyword(Keyword.BULWARK)) reduction=Math.max(reduction,source.definition().keywordValue(Keyword.BULWARK).amount());
        }
        return Math.max(0,amount-reduction);
    }
    public static void entered(GameState state,CardInstance entrant,BoardPosition from,BoardPosition to,boolean summoned) {
        if(entrant.definition().type()!=CardType.CHARACTER || entrant.zone()!=Zone.BATTLEFIELD
                || !state.board().topAt(to).filter(entrant.instanceId()::equals).isPresent())return;
        for(CardInstance source:sources(state)) {
            if(state.phase()==Phase.GAME_OVER || entrant.zone()!=Zone.BATTLEFIELD)break;
            BoardPosition origin=state.board().positionOf(source.instanceId()).orElseThrow();
            for(Keyword keyword:List.of(Keyword.TURRET,Keyword.MEDIC_TENT,Keyword.WAYSTATION,Keyword.BEACON)) {
                if(!source.definition().hasKeyword(keyword))continue;
                KeywordValue value=source.definition().keywordValue(keyword);
                boolean friendly=source.owner()==entrant.owner();
                if(keyword==Keyword.TURRET ? friendly : !friendly)continue;
                if(keyword==Keyword.BEACON && !summoned)continue;
                if(keyword==Keyword.WAYSTATION && summoned)continue;
                if(state.rules().geometry().distance(origin,to)>value.range())continue;
                if(from!=null && state.rules().geometry().distance(origin,from)<=value.range())continue;
                if(!new LineOfSightRules().hasLineOfSight(state,origin,to))continue;
                if(keyword==Keyword.MEDIC_TENT && entrant.combatDamage()==0)continue;
                if(!state.useTerrainTrigger(source,entrant,keyword))continue;
                switch(keyword) {
                    case TURRET -> {
                        int damage=reduceDamage(state,entrant,value.amount(),state.rules().geometry().distance(origin,to)>1);
                        entrant.addCombatDamage(damage);
                        state.recordTerrain(source,entrant,keyword,damage);
                        if(entrant.combatDamage()>=entrant.effectiveDefense())state.destroy(entrant);
                    }
                    case MEDIC_TENT -> { int healed=Math.min(value.amount(),entrant.combatDamage());entrant.healCombatDamage(healed);state.recordTerrain(source,entrant,keyword,healed); }
                    case WAYSTATION -> { entrant.restoreMovement(value.amount());state.recordTerrain(source,entrant,keyword,value.amount()); }
                    case BEACON -> { entrant.addAttackBonus(value.amount());state.recordTerrain(source,entrant,keyword,value.amount()); }
                    default -> { }
                }
            }
        }
    }
    public static void startTurn(GameState state,int player) {
        for(CardInstance source:sources(state)) if(source.owner()==player) {
            BoardPosition origin=state.board().positionOf(source.instanceId()).orElseThrow();
            for(Keyword keyword:List.of(Keyword.SANCTUARY,Keyword.WORKSHOP)) if(source.definition().hasKeyword(keyword)) {
                KeywordValue value=source.definition().keywordValue(keyword);
                state.battlefieldCards(player).stream().filter(c->c.damage()>0)
                        .filter(c->keyword==Keyword.WORKSHOP?c.definition().type()==CardType.STRUCTURE:c.definition().isPermanent())
                        .filter(c->state.board().positionOf(c.instanceId()).map(p->state.rules().geometry().distance(origin,p)<=value.range()).orElse(false))
                        .max(Comparator.comparingInt(CardInstance::damage).thenComparing(c->c.instanceId().toString()))
                        .ifPresent(c->{int healed=Math.min(value.amount(),c.damage());c.healDamage(healed);state.recordTerrain(source,c,keyword,healed);});
            }
        }
    }
    public static String describe(CardDefinition card,Keyword keyword) {
        KeywordValue v=card.keywordValue(keyword);
        return switch(keyword) {
            case HIGH_GROUND -> "Adds "+v.amount()+" height level to this stack. Land itself does not block sight.";
            case COVER -> "Friendly Characters on this stack take "+v.amount()+" less ranged attack or Turret damage. Uses the strongest protection, not a sum.";
            case WAYSTATION -> "A friendly Character entering this hex by movement recovers "+v.amount()+" movement, once per Character per turn.";
            case FERTILE -> "Generates "+v.amount()+" extra gold each owner turn (included in displayed income).";
            case SANCTUARY -> "Start of your turn: repair "+v.amount()+" damage on the most damaged friendly Permanent in this hex.";
            case ARCHIVE -> "When destroyed, draw "+v.amount()+" card(s).";
            case TURRET -> "An enemy Character entering visible range "+v.range()+" takes "+v.amount()+" damage. Once per entrant per turn; moving within the area does not retrigger.";
            case MEDIC_TENT -> "A friendly Character entering visible range "+v.range()+" heals "+v.amount()+" marked combat damage. Once per entrant per turn.";
            case WATCHTOWER -> "Friendly Characters on this stack gain +"+v.amount()+" Range.";
            case BULWARK -> "This stack's friendly occupant takes "+v.amount()+" less attack or Turret damage. Uses the strongest protection, not a sum.";
            case WORKSHOP -> "Start of your turn: repair "+v.amount()+" damage on the most damaged friendly Structure within "+v.range()+" space(s).";
            case BEACON -> "A friendly Character summoned within visible range "+v.range()+" gains +"+v.amount()+" Attack until its next turn.";
            default -> "";
        };
    }
}
