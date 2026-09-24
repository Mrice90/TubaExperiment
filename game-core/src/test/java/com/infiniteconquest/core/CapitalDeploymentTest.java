package com.infiniteconquest.core;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class CapitalDeploymentTest {
    private CardInstance capital(int owner) {
        CardDefinition definition = new CardDefinition("capital_" + owner, "Capital", CardType.CAPITAL, "DEV", 0, 0, 0, 0, 0);
        return new CardInstance(UUID.randomUUID(), definition, owner, Zone.HAND);
    }

    @Test void placementsRemainUnrevealedUntilBothPlayersCommit() {
        BoardState board = new BoardState();
        CapitalDeployment deployment = new CapitalDeployment();
        CardInstance first = capital(0);
        CardInstance second = capital(1);

        deployment.commit(0, first, new BoardPosition(0, 1));
        assertFalse(deployment.ready());
        assertTrue(board.isEmpty(new BoardPosition(0, 1)));

        deployment.commit(1, second, new BoardPosition(3, 4));
        assertTrue(deployment.ready());
        deployment.reveal(board);

        assertEquals(first.instanceId(), board.topAt(new BoardPosition(0, 1)).orElseThrow());
        assertEquals(second.instanceId(), board.topAt(new BoardPosition(3, 4)).orElseThrow());
        assertEquals(Zone.BATTLEFIELD, first.zone());
    }

    @Test void rejectsCapitalOutsideOwnersPlot() {
        assertThrows(IllegalArgumentException.class,
                () -> new CapitalDeployment().commit(0, capital(0), new BoardPosition(0, 5)));
    }
}
