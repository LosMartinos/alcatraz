package com.alcatraz.domain;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// One instance per active game. Mutable — the service layer is the only thing
// allowed to touch it, and only while holding the "am I primary" check.
@Data
public class GameState {

    private final String gameId;
    // LinkedHashMap: preserves insertion order, so join-order == playerId order,
    // which is exactly what Alcatraz.init(noPlayers, myPlayerId) needs consistently
    // across every client.
    private final Map<String, RegisteredPlayer> players = new LinkedHashMap<>();
    private GameStatus status = GameStatus.WAITING;

    public GameState(String gameId) {
        this.gameId = gameId;
    }

    public boolean hasPlayerNamed(String name) {
        return players.containsKey(name);
    }

    public int nextPlayerId() {
        return players.size(); // 0-indexed, matches Alcatraz's expected id range
    }

    public void addPlayer(RegisteredPlayer player) {
        players.put(player.name(), player);
    }

    public void removePlayer(String name) {
        players.remove(name);
    }

    public void markStarted() {
        this.status = GameStatus.STARTED;
    }

    public List<RegisteredPlayer> getPlayers() {
        return List.copyOf(players.values());
    }

    public int playerCount() {
        return players.size();
    }
}