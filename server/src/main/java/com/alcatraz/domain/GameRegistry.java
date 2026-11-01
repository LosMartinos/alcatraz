package com.alcatraz.domain;

import com.alcatraz.replication.ReplicationEvent;
import org.springframework.stereotype.Component;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class GameRegistry {
    private final Map<String, GameState> games = new LinkedHashMap<>();
    private long version;
    private boolean initialized;

    public record Snapshot(long version, Map<String, GameState> games) implements Serializable {
        /**
         * Creates an immutable copy of the game map.
         *
         * @param version state version
         * @param games games indexed by ID
         */
        public Snapshot {
            games = Map.copyOf(games);
        }
    }

    /**
     * Returns the local game, or null if absent.
     *
     * @param gameId game ID
     * @return game, or null if absent
     */
    public synchronized GameState get(String gameId) {
        return games.get(gameId);
    }

    /**
     * Copies all local games and their current version.
     *
     * @return copy of the games and their version
     */
    public synchronized Snapshot snapshot() {
        return new Snapshot(version, games);
    }

    /**
     * Checks whether initial state is available.
     *
     * @return true when initial state is available
     */
    public synchronized boolean initialized() {
        return initialized;
    }

    /**
     * Marks the first server's empty registry as initialized.
     */
    public synchronized void initializeEmpty() {
        initialized = true;
    }

    /**
     * Applies a local change and returns its versioned replication event.
     *
     * @param gameId game ID
     * @param state updated game, or null to remove it
     * @return versioned replication event
     */
    public synchronized ReplicationEvent change(String gameId, GameState state) {
        var event = new ReplicationEvent(Math.incrementExact(version), gameId, state);
        apply(event);
        return event;
    }

    /**
     * Applies the next version when the registry is initialized.
     *
     * @param event replication event
     */
    public synchronized void apply(ReplicationEvent event) {
        if (!initialized) return;
        if (event.version() <= version) return;
        if (event.version() != version + 1) return;
        if (event.state() == null) games.remove(event.gameId());
        else games.put(event.gameId(), event.state());
        version = event.version();
    }

    /**
     * Loads a snapshot at least as recent as the local state.
     *
     * @param snapshot registry snapshot
     */
    public synchronized void install(Snapshot snapshot) {
        // State transfer can race with newer RPCs. Never roll a replica back.
        if (initialized && snapshot.version() < version) return;
        games.clear();
        games.putAll(snapshot.games());
        version = snapshot.version();
        initialized = true;
    }
}
