package com.alcatraz.client;

import com.alcatraz.client.Messages.MovePayload;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MoveSequenceTest {
    @Test
    void buffersEarlyAndOutOfOrderMovesAndKeepsFirstContent() {
        var sequence = new MoveSequence("demo");
        var first = move(0, 0, 0);
        var second = move(1, 1, 4);
        sequence.accept(second);
        sequence.accept(first);
        assertNull(sequence.pending());
        sequence.initialize(2);
        assertEquals(first, sequence.pending());
        sequence.accept(move(0, 0, 1));
        assertEquals(first, sequence.pending());
        sequence.advance();
        assertEquals(second, sequence.pending());
        sequence.advance();
        sequence.accept(first);
        assertNull(sequence.pending());
        assertEquals(2, sequence.next());
    }

    @Test
    void rejectsWrongGameAndTurnAndRechecksEarlyMoves() {
        var sequence = new MoveSequence("demo");
        sequence.accept(move(0, 1, 4));
        sequence.initialize(2);
        assertNull(sequence.pending());
        var wrongTurn = assertThrows(RequestException.class, () -> sequence.accept(move(0, 1, 4)));
        assertEquals(409, wrongTurn.status);
        var wrongGame = assertThrows(RequestException.class,
                () -> sequence.accept(new MovePayload("other", 0, 0, 0, 0, 1, 1)));
        assertEquals(404, wrongGame.status);
        sequence.accept(move(0, 0, 0));
        assertNotNull(sequence.pending());
    }

    @Test
    void preservesRowColumnAndBoatCoordinates() {
        var sequence = new MoveSequence("demo");
        sequence.initialize(2);
        for (int kind = 0; kind < 3; kind++) {
            var move = new MovePayload("demo", kind, kind % 2, 0, kind, -1, 9);
            sequence.accept(move);
            assertEquals(move, sequence.pending());
            sequence.advance();
        }
    }

    @Test
    void rejectsMissingMoveFields() {
        assertThrows(RuntimeException.class, () -> ClientHttp.JSON.readValue(
                "{\"gameId\":\"demo\",\"sequenceNumber\":0}", MovePayload.class));
    }

    private static MovePayload move(int sequence, int player, int prisoner) {
        return new MovePayload("demo", sequence, player, prisoner, 0, 1, 1);
    }
}
