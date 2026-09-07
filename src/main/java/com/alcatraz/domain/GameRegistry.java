package com.alcatraz.domain;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// The single source of truth for game state on this node.
// Mutated by GameRegistrationService (primary path) AND ReplicationListener (backup path) --
// nothing else should ever touch this map.
@Component
public class GameRegistry {

    private final Map<String, GameState> games = new ConcurrentHashMap<>();

    public GameState getOrCreate(String gameId) {
        return games.computeIfAbsent(gameId, GameState::new);
    }

    public GameState get(String gameId) {
        return games.get(gameId);
    }
}