package com.alcatraz.client;

import at.falb.games.alcatraz.api.*;
import com.alcatraz.client.Messages.*;
import javax.swing.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.*;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Owns one game's state on the Swing event thread. */
final class GameSession implements MoveListener, AutoCloseable {
    private static final Logger LOG = Logger.getLogger(GameSession.class.getName());
    final String gameId;
    final String name;
    final String callbackUrl;
    private final ClientHttp http;
    private final Consumer<String> status;
    private final Runnable started;
    private final MoveSequence sequence;
    private Alcatraz board;
    private MoveSender sender;
    private List<PlayerInfo> players;
    private int localId;
    private boolean finished;
    private boolean halted;

    /**
     * Prepares a session before registration so early callbacks can be buffered.
     * @param gameId selected game
     * @param name local name
     * @param callbackUrl local callback address
     * @param http HTTP transport
     * @param status displays game progress
     * @param started updates the lobby after initialization
     */
    GameSession(String gameId, String name, String callbackUrl, ClientHttp http,
                Consumer<String> status, Runnable started) {
        this.gameId = gameId;
        this.name = name;
        this.callbackUrl = callbackUrl;
        this.http = http;
        this.status = status;
        this.started = started;
        sequence = new MoveSequence(gameId);
    }

    /**
     * Initializes the board once from the frozen roster.
     * @param payload start notification
     */
    void initialize(GameStartedPayload payload) {
        if (payload == null || !gameId.equals(payload.gameId())) throw new RequestException(404, "Game not found");
        if (board != null) return;
        var roster = payload.players();
        if (roster == null || roster.size() < 2 || roster.size() > 4
                || payload.yourPlayerId() < 0 || payload.yourPlayerId() >= roster.size()) {
            throw new RequestException(400, "Invalid game roster");
        }
        for (int id = 0; id < roster.size(); id++) {
            var player = roster.get(id);
            if (player == null || player.id() == null || player.id() != id || player.name() == null
                    || player.name().isBlank() || player.callbackUrl() == null
                    || !ClientHttp.baseUrl(player.callbackUrl()).equals(player.callbackUrl())) {
                throw new RequestException(400, "Invalid player");
            }
        }
        var local = roster.get(payload.yourPlayerId());
        if (!name.equals(local.name()) || !callbackUrl.equals(local.callbackUrl())) {
            throw new RequestException(409, "Start notification belongs to a different player");
        }
        players = List.copyOf(roster);
        localId = payload.yourPlayerId();
        sequence.initialize(players.size());
        board = new Alcatraz();
        board.init(players.size(), localId);
        players.forEach(player -> board.getPlayer(player.id()).setName(player.name()));
        board.addMoveListener(this);
        sender = new MoveSender(http, players, localId);
        board.getWindow().setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        board.getWindow().addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                board.closeWindow();
                status.accept("Game window hidden. Use Show game to reopen it.");
            }
        });
        board.showWindow();
        board.start();
        started.run();
        drain();
        showTurn();
    }

    /**
     * Uses a polled roster as a fallback for a missed start callback.
     * @param state primary's current game state
     */
    void observe(GameStatusResponse state) {
        if (!"STARTED".equals(state.status()) || initialized()) return;
        var local = state.players().stream()
                .filter(p -> name.equals(p.name()) && callbackUrl.equals(p.callbackUrl()))
                .findFirst().orElseThrow(() -> new RequestException(409, "Local player is absent from the roster"));
        if (local.id() == null) throw new RequestException(400, "Started player has no ID");
        initialize(new GameStartedPayload(gameId, local.id(), state.players()));
    }

    /**
     * Buffers a received move and applies consecutive moves.
     * @param move peer's move
     */
    void receive(MovePayload move) {
        sequence.accept(move);
        drain();
    }

    /** Applies available remote moves in sequence on the Swing thread. */
    private void drain() {
        if (!initialized() || halted || finished) return;
        MovePayload move;
        while ((move = sequence.pending()) != null && !finished) {
            try {
                board.doMove(board.getPlayer(move.playerId()), board.getPrisoner(move.prisonerId()),
                        move.rowOrCol(), move.row(), move.col());
                sequence.advance();
            } catch (IllegalMoveException | IllegalArgumentException e) {
                halted = true;
                LOG.log(Level.SEVERE, "Cannot apply move " + move.sequenceNumber(), e);
                status.accept("Game stopped: a received move could not be applied.");
                return;
            }
        }
        showTurn();
    }

    /**
     * Queues a completed local move for the other players.
     * @param player moving player
     * @param prisoner moving prisoner
     * @param rowOrCol library move type
     * @param row unchanged row coordinate
     * @param col unchanged column coordinate
     */
    @Override
    public void moveDone(Player player, Prisoner prisoner, int rowOrCol, int row, int col) {
        if (player.getId() != localId || halted) return;
        if (sequence.next() % players.size() != localId) {
            halted = true;
            status.accept("Game stopped: local turn does not match the move sequence.");
            return;
        }
        var move = new MovePayload(gameId, sequence.next(), localId, prisoner.getId(), rowOrCol, row, col);
        sequence.accept(move);
        sequence.advance();
        sender.broadcast(move);
        SwingUtilities.invokeLater(this::drain);
    }

    /**
     * Displays the winner.
     * @param player winning player
     */
    @Override
    public void gameWon(Player player) {
        finished = true;
        status.accept(player.getName() + " won!");
        board.getWindow().setTitle("Alcatraz - " + player.getName() + " won");
    }

    /** Displays whose move is awaited. */
    private void showTurn() {
        if (finished || halted) return;
        String message = sequence.next() % players.size() == localId ? "Your turn"
                : "Waiting for " + players.get(sequence.next() % players.size()).name();
        status.accept(message);
        board.getWindow().setTitle("Alcatraz - " + name + " - " + message);
    }

    /**
     * Checks whether the board has been created.
     * @return true after initialization
     */
    boolean initialized() {
        return board != null;
    }

    /** Brings the game window back into view. */
    void showWindow() {
        if (board != null) board.showWindow();
    }

    /** Stops outgoing delivery and disposes the game window. */
    @Override
    public void close() {
        if (sender != null) sender.close();
        if (board != null) board.disposeWindow();
    }
}
