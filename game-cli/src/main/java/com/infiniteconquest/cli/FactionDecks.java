package com.infiniteconquest.cli;

import com.infiniteconquest.core.CardDefinition;
import com.infiniteconquest.core.CardType;
import com.infiniteconquest.core.DeckValidator;
import com.infiniteconquest.data.Keyword;

import java.util.*;

public final class FactionDecks {
    public static final Set<String> FACTIONS = Set.of(
            "ZEUS", "POSEIDON");

    public static final Map<String, CardType> PRIMARY_TYPES = Map.of(
            "ZEUS", CardType.SPELL,
            "POSEIDON", CardType.LAND,
            "HADES", CardType.SPELL,
            "ARES", CardType.CHARACTER,
            "ATHENA", CardType.CHARACTER,
            "HEPHAESTUS", CardType.STRUCTURE);

    public static final Map<String, CardType> SECONDARY_TYPES = Map.of(
            "ZEUS", CardType.CHARACTER,
            "POSEIDON", CardType.CHARACTER,
            "HADES", CardType.CHARACTER,
            "ARES", CardType.SPELL,
            "ATHENA", CardType.STRUCTURE,
            "HEPHAESTUS", CardType.LAND);

    public static final Map<String, Keyword> PRIMARY_KEYWORDS = Map.of(
            "ZEUS", Keyword.BLINK,
            "POSEIDON", Keyword.MOLE,
            "HADES", Keyword.MOLE,
            "ARES", Keyword.FAST_STRIKE,
            "ATHENA", Keyword.VANGUARD,
            "HEPHAESTUS", Keyword.VANGUARD);

    public static final Map<String, Keyword> SECONDARY_KEYWORDS = Map.of(
            "ZEUS", Keyword.SHARP_SHOT,
            "POSEIDON", Keyword.VANGUARD,
            "HADES", Keyword.FAST_STRIKE,
            "ARES", Keyword.SIEGE,
            "ATHENA", Keyword.SHARP_SHOT,
            "HEPHAESTUS", Keyword.SIEGE);

    private final PrototypeCardPool pool;

    public FactionDecks(PrototypeCardPool pool) {
        this.pool = pool;
    }

    public List<CardDefinition> starter(String factionName) {
        String faction=factionName.toUpperCase(Locale.ROOT);
        if(!FACTIONS.contains(faction))throw new IllegalArgumentException("Unknown faction: "+factionName);
        List<CardDefinition> deck=new ArrayList<>();
        try(var input=FactionDecks.class.getResourceAsStream("/cards/faction-starters.json")){
            if(input==null)throw new IllegalStateException("Starter catalog missing");
            var entries=new com.fasterxml.jackson.databind.ObjectMapper().readTree(input).get(faction);
            if(entries==null || !entries.isArray())throw new IllegalStateException("Starter missing for "+faction);
            Set<String> seen=new HashSet<>();
            for(var entry:entries){
                var card=pool.require(entry.path("id").asText());int count=entry.path("copies").asInt();
                if(!card.faction().equals(faction)||card.type()==CardType.CAPITAL||count<1||count>4||!seen.add(card.id()))throw new IllegalStateException("Invalid starter entry: "+card.id());
                for(int copy=0;copy<count;copy++)deck.add(card);
            }
        }catch(java.io.IOException e){throw new IllegalStateException("Cannot load faction starters",e);}
        List<String> errors=new DeckValidator().validate(deck);
        if(deck.size()!=60 || !errors.isEmpty())throw new IllegalStateException("Invalid starter "+faction+": "+errors);
        return List.copyOf(deck);
    }
}
