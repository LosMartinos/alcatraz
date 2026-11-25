package com.alcatraz.client;

import com.alcatraz.client.Messages.MovePayload;
import java.util.TreeMap;
import java.util.logging.Logger;

/** Tracks accepted moves and the next move to apply. Accessed on the Swing thread. */
final class MoveSequence {
    private static final Logger LOG = Logger.getLogger(MoveSequence.class.getName());
    private final String gameId;
    private final TreeMap<Integer, MovePayload> moves = new TreeMap<>();
    private int playerCount;
    private int next;

    /**
     * Creates the sequence for one game.
     * @param gameId game ID
     */
    MoveSequence(String gameId) {
        this.gameId = gameId;
    }

    /**
     * Validates buffered turns once the roster is known.
     * @param count number of players
     */
    void initialize(int count) {
        playerCount = count;
        moves.values().removeIf(move -> {
            if (move.playerId() != move.sequenceNumber() % count || move.prisonerId() >= count * 4) {
                LOG.warning("Discarding invalid early move " + move.sequenceNumber());
                return true;
            }
            return false;
        });
    }

    /**
     * Accepts a move, keeping the first content for each sequence number.
     * @param move received or local move
     */
    void accept(MovePayload move) {
        if (move == null || !gameId.equals(move.gameId())) throw new RequestException(404, "Game not found");
        if (move.sequenceNumber() < 0 || move.playerId() < 0 || move.playerId() > 3
                || move.prisonerId() < 0 || move.prisonerId() >= 16
                || move.rowOrCol() < 0 || move.rowOrCol() > 2) {
            throw new RequestException(400, "Invalid move fields");
        }
        var previous = moves.get(move.sequenceNumber());
        if (previous != null || move.sequenceNumber() < next) {
            if (previous != null && !previous.equals(move)) {
                LOG.warning("Ignoring conflicting move " + move.sequenceNumber());
            }
            return;
        }
        if (playerCount > 0 && move.playerId() != move.sequenceNumber() % playerCount) {
            LOG.warning("Ignoring move from the wrong player at sequence " + move.sequenceNumber());
            throw new RequestException(409, "Player does not match the move sequence");
        }
        if (playerCount > 0 && move.prisonerId() >= playerCount * 4) {
            throw new RequestException(400, "Invalid prisoner ID");
        }
        moves.put(move.sequenceNumber(), move);
    }

    /**
     * Finds the next consecutive move.
     * @return next move, or null while waiting for initialization or delivery
     */
    MovePayload pending() {
        return playerCount == 0 ? null : moves.get(next);
    }

    /** Advances after a move has been applied. */
    void advance() {
        next++;
    }

    /**
     * Reads the global sequence counter.
     * @return next expected sequence number
     */
    int next() {
        return next;
    }
}
