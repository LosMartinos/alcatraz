package com.alcatraz.domain;

import com.alcatraz.web.dto.PlayerInfo;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Immutable state; a change replaces the entire game in the registry. */
public record GameState(String gameId, GameStatus status, List<RegisteredPlayer> players) implements Serializable {
    public GameState {
        players = List.copyOf(players);
    }

    public static GameState waiting(String gameId) {
        return new GameState(gameId, GameStatus.WAITING, List.of());
    }

    public RegisteredPlayer player(String name) {
        return players.stream().filter(p -> p.name().equals(name)).findFirst().orElse(null);
    }

    public GameState add(RegisteredPlayer player) {
        var roster = new ArrayList<>(players);
        roster.add(player);
        return new GameState(gameId, status, roster);
    }

    public GameState remove(String name) {
        return new GameState(gameId, status, players.stream().filter(p -> !p.name().equals(name)).toList());
    }

    public GameState start() {
        var roster = new ArrayList<RegisteredPlayer>();
        for (int id = 0; id < players.size(); id++) {
            roster.add(players.get(id).withId(id));
        }
        return new GameState(gameId, GameStatus.STARTED, roster);
    }

    public List<PlayerInfo> publicPlayers() {
        return players.stream().map(RegisteredPlayer::publicInfo).toList();
    }
}
