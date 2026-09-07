package com.alcatraz.service;

import com.alcatraz.domain.*;
import com.alcatraz.exception.*;
import com.alcatraz.replication.ClusterMembershipService;
import com.alcatraz.replication.ReplicationEvent;
import com.alcatraz.web.dto.RegisterRequest;
import com.alcatraz.web.dto.RegisterResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GameRegistrationService {

    private final GameRegistry gameRegistry;
    private final ClusterMembershipService clusterMembershipService;

    public GameRegistrationService(GameRegistry gameRegistry, ClusterMembershipService clusterMembershipService) {
        this.gameRegistry = gameRegistry;
        this.clusterMembershipService = clusterMembershipService;
    }

    public RegisterResponse register(String gameId, RegisterRequest req) {
        if (!clusterMembershipService.isPrimary()) {
            throw new NotPrimaryException("this node is not primary, retry against another server");
        }

        GameState game = gameRegistry.getOrCreate(gameId);
        RegisteredPlayer newPlayer;

        synchronized (game) {
            if (game.getStatus() == GameStatus.STARTED) {
                throw new GameAlreadyStartedException("registration closed for game " + gameId);
            }
            if (game.hasPlayerNamed(req.playerName())) {
                throw new PlayerNameTakenException("'" + req.playerName() + "' is already taken in game " + gameId);
            }

            newPlayer = new RegisteredPlayer(game.nextPlayerId(), req.playerName(), req.callbackUrl());
            game.addPlayer(newPlayer);
        }

        clusterMembershipService.broadcast(new ReplicationEvent.PlayerRegistered(gameId, newPlayer));

        List<String> names = game.getPlayers().stream().map(RegisteredPlayer::name).toList();
        return new RegisterResponse(newPlayer.playerId(), names);
    }

    public void unregister(String gameId, String playerName) {
        if (!clusterMembershipService.isPrimary()) {
            throw new NotPrimaryException("this node is not primary, retry against another server");
        }

        GameState game = gameRegistry.get(gameId);
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

        clusterMembershipService.broadcast(new ReplicationEvent.PlayerUnregistered(gameId, playerName));
    }

    public List<RegisteredPlayer> startGame(String gameId) {
        if (!clusterMembershipService.isPrimary()) {
            throw new NotPrimaryException("this node is not primary, retry against another server");
        }

        GameState game = gameRegistry.get(gameId);
        if (game == null) {
            throw new GameNotFoundException("no game with id " + gameId);
        }

        List<RegisteredPlayer> finalRoster;

        synchronized (game) {
            if (game.getStatus() == GameStatus.STARTED) {
                throw new GameAlreadyStartedException("game " + gameId + " already started");
            }
            if (game.playerCount() < 2) {
                throw new NotEnoughPlayersException("need at least 2 players to start game " + gameId);
            }
            game.markStarted();
            finalRoster = game.getPlayers();
        }

        clusterMembershipService.broadcast(new ReplicationEvent.GameStarted(gameId, finalRoster));

        return finalRoster;
    }
}