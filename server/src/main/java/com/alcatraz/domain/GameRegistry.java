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
        public Snapshot {
            games = Map.copyOf(games);
        }
    }

    public synchronized GameState get(String gameId) {
        return games.get(gameId);
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(version, games);
    }

    public synchronized boolean initialized() {
        return initialized;
    }

    public synchronized void initializeEmpty() {
        initialized = true;
    }

    public synchronized ReplicationEvent change(String gameId, GameState state) {
        var event = new ReplicationEvent(Math.incrementExact(version), gameId, state);
        apply(event);
        return event;
    }

    public synchronized void apply(ReplicationEvent event) {
        if (!initialized) return;
        if (event.version() <= version) return;
        if (event.version() != version + 1) return;
        if (event.state() == null) games.remove(event.gameId());
        else games.put(event.gameId(), event.state());
        version = event.version();
    }

    public synchronized void install(Snapshot snapshot) {
        // State transfer can race with newer RPCs. Never roll a replica back.
        if (initialized && snapshot.version() < version) return;
        games.clear();
        games.putAll(snapshot.games());
        version = snapshot.version();
        initialized = true;
    }
}
