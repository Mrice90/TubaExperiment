package com.infiniteconquest.cli;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.infiniteconquest.core.*;
import java.nio.file.*;
import java.util.*;

public final class StarterBalanceRunner {
    public static void main(String[] args)throws Exception{
        Path out=Path.of(args[0]);Files.createDirectories(out);
        var json=new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        var pool=new PrototypeCardPool();var capitals=new CapitalRoster();var decks=new FactionDecks(pool);
        var sim=new BalanceSimulator();var factions=FactionDecks.FACTIONS.stream().sorted().toList();
        var previous=json.readTree(StarterBalanceRunner.class.getResourceAsStream("/cards/previous-faction-starters.json"));
        for(String version:List.of("before","after")){
            Map<String,List<CardDefinition>> lists=new TreeMap<>();
            for(String f:factions){
                List<CardDefinition> cards=new ArrayList<>();
                if(version.equals("before"))for(var id:previous.get(f))cards.add(pool.require(id.asText()));else cards.addAll(decks.starter(f));
                lists.put(f,cards);
            }
            var totals=new BalanceSimulator.Accumulator(1,424200L);int n=0;
            for(String a:factions)for(String b:factions)for(int c=0;c<3;c++){
                totals.add(sim.play(424200L+n++,a,b,capitals.forFaction(a).get(c),capitals.forFaction(b).get(c),lists.get(a),lists.get(b)));
            }
            sim.write(totals.report(),out.resolve(version+"-matches.json"));
            Map<String,Object> stats=new TreeMap<>();Map<String,String> codes=new TreeMap<>();
            for(String f:factions){var cards=lists.get(f);long earlyLand=cards.stream().filter(c->c.type()==CardType.LAND&&c.cost()<=1&&c.goldCost()==0).count();
                var row=new TreeMap<String,Object>();row.put("cards",cards.stream().map(CardDefinition::id).toList());
                row.put("freeTurnOneLands",earlyLand);row.put("openingFiveHasFreeTurnOneLand",1-choose(60-(int)earlyLand,5)/choose(60,5));
                row.put("openingSixHasFreeTurnOneLand",1-choose(60-(int)earlyLand,6)/choose(60,6));
                row.put("charactersCostingThreeOrLess",cards.stream().filter(c->c.type()==CardType.CHARACTER&&c.cost()<=3).count());
                row.put("lateDevelopmentsTurnSixPlus",cards.stream().filter(c->c.type()==CardType.LAND||c.type()==CardType.STRUCTURE).filter(c->c.cost()>=6).count());
                row.put("averageCharacterCost",cards.stream().filter(c->c.type()==CardType.CHARACTER).mapToInt(CardDefinition::cost).average().orElse(0));
                stats.put(f,row);
                codes.put(f,new DeckBuildStore(pool,capitals).exportCode(new DeckBuild(f+" Starter 0.4.2",f,null,capitals.forFaction(f).get(0),cards)));
            }
            json.writeValue(out.resolve(version+"-decks.json").toFile(),stats);
            if(version.equals("after"))json.writeValue(out.resolve("deck-codes.json").toFile(),codes);
            System.out.println(version+": "+n+" fixed-seed hex matches completed");
        }
    }
    private static double choose(int n,int k){double value=1;for(int i=1;i<=k;i++)value=value*(n-i+1)/i;return value;}
}
