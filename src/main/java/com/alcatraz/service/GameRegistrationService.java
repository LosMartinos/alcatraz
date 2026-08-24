package com.alcatraz.service;

import com.alcatraz.domain.GameState;
import com.alcatraz.domain.GameStatus;
import com.alcatraz.domain.RegisteredPlayer;
import com.alcatraz.exception.*;
import com.alcatraz.web.dto.RegisterRequest;
import com.alcatraz.web.dto.RegisterResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GameRegistrationService {

    // one GameState per active gameId; ConcurrentHashMap since multiple clients
    // can register concurrently against the same running server
    private final Map<String, GameState> games = new ConcurrentHashMap<>();

    public RegisterResponse register(String gameId, RegisterRequest req) {
        GameState game = games.computeIfAbsent(gameId, GameState::new);

        synchronized (game) {
            if (game.getStatus() == GameStatus.STARTED) {
                throw new GameAlreadyStartedException("registration closed for game " + gameId);
            }
            if (game.hasPlayerNamed(req.playerName())) {
                throw new PlayerNameTakenException("'" + req.playerName() + "' is already taken in game " + gameId);
            }

            int assignedId = game.nextPlayerId();
            game.addPlayer(new RegisteredPlayer(assignedId, req.playerName(), req.callbackUrl()));

            List<String> names = game.getPlayers().stream().map(RegisteredPlayer::name).toList();
            return new RegisterResponse(assignedId, names);
        }
    }

    public void unregister(String gameId, String playerName) {
        GameState game = games.get(gameId);
        if (game == null) {
            throw new GameNotFoundException("no game with id " + gameId);
        }

        synchronized (game) {
            if (game.getStatus() == GameStatus.STARTED) {
                throw new GameAlreadyStartedException("cannot unregister, game " + gameId + " already started");
            }
            if (!game.hasPlayerNamed(playerName)) {
                throw new GameNotFoundException("no player named '" + playerName + "' in game " + gameId);
            }
            game.removePlayer(playerName);
        }
    }

    // returns the frozen roster so the controller can hand it straight to ClientNotifierService
    public List<RegisteredPlayer> startGame(String gameId) {
        GameState game = games.get(gameId);
        if (game == null) {
            throw new GameNotFoundException("no game with id " + gameId);
        }

        synchronized (game) {
            if (game.getStatus() == GameStatus.STARTED) {
                throw new GameAlreadyStartedException("game " + gameId + " already started");
            }
            if (game.playerCount() < 2) {
                throw new NotEnoughPlayersException("need at least 2 players to start game " + gameId);
            }
            game.markStarted();
            return game.getPlayers();
        }
    }
}