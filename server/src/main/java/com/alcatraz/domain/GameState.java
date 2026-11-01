package com.alcatraz.domain;

import com.alcatraz.web.dto.PlayerInfo;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Immutable state; a change replaces the entire game in the registry. */
public record GameState(String gameId, GameStatus status, List<RegisteredPlayer> players) implements Serializable {
    /**
     * Creates an immutable copy of the player list.
     *
     * @param gameId game ID
     * @param status game status
     * @param players registered players
     */
    public GameState {
        players = List.copyOf(players);
    }

    /**
     * Creates an empty waiting game.
     *
     * @param gameId game ID
     * @return empty waiting game
     */
    public static GameState waiting(String gameId) {
        return new GameState(gameId, GameStatus.WAITING, List.of());
    }

    /**
     * Finds a player by exact name, or returns null.
     *
     * @param name player name
     * @return matching player, or null if absent
     */
    public RegisteredPlayer player(String name) {
        return players.stream().filter(p -> p.name().equals(name)).findFirst().orElse(null);
    }

    /**
     * Returns a game copy with the player appended.
     *
     * @param player player to add
     * @return game copy containing the player
     */
    public GameState add(RegisteredPlayer player) {
        var roster = new ArrayList<>(players);
        roster.add(player);
        return new GameState(gameId, status, roster);
    }

    /**
     * Returns a game copy with the named player removed.
     *
     * @param name player name
     * @return game copy with the player removed
     */
    public GameState remove(String name) {
        return new GameState(gameId, status, players.stream().filter(p -> !p.name().equals(name)).toList());
    }

    /**
     * Returns a started game copy with IDs assigned in registration order.
     *
     * @return started game copy with assigned player IDs
     */
    public GameState start() {
        var roster = new ArrayList<RegisteredPlayer>();
        for (int id = 0; id < players.size(); id++) {
            roster.add(players.get(id).withId(id));
        }
        return new GameState(gameId, GameStatus.STARTED, roster);
    }

    /**
     * Returns the public player details for clients.
     *
     * @return public player details
     */
    public List<PlayerInfo> publicPlayers() {
        return players.stream().map(RegisteredPlayer::publicInfo).toList();
    }
}
