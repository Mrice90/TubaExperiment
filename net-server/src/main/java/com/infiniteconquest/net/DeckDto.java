package com.infiniteconquest.net;

/**
 * A player's deck list as transmitted to the host at lobby time.
 *
 * <p>The host needs the guest's deck list to run the authoritative game; the join UI
 * must plainly state "The host will see your deck list". The guest deck is sent only
 * to the host, never to lobby/Worker services or spectators.
 */
public record DeckDto(
        String name,
        String primaryFaction,
        String allyFaction,
        String capitalId,
        java.util.List<String> cardIds
) {}
