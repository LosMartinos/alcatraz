package com.alcatraz.service;

import com.alcatraz.domain.*;
import com.alcatraz.exception.*;
import com.alcatraz.replication.ClusterMembershipService;
import com.alcatraz.web.dto.*;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class GameRegistrationService {
    private final GameRegistry registry;
    private final ClusterMembershipService cluster;
    private final SecureRandom random = new SecureRandom();

    /**
     * Carries the token and response body to the controller.
     */
    public record RegistrationResult(String token, RegisterResponse response) {
    }

    /**
     * Stores the registry and cluster service.
     *
     * @param registry game registry
     * @param cluster cluster service
     */
    public GameRegistrationService(GameRegistry registry, ClusterMembershipService cluster) {
        this.registry = registry;
        this.cluster = cluster;
    }

    /**
     * Registers and replicates a player; creates missing games and reuses matching registrations.
     *
     * @param gameId game ID
     * @param request player name and callback URL
     * @return registration token and player list
     */
    public RegistrationResult register(String gameId, RegisterRequest request) {
        synchronized (cluster) {
            cluster.synchronizeState();
            if (request == null) throw new InvalidRequestException("A registration body is required");
            String name = normalizeName(request.name());
            String callbackUrl = normalizeCallback(request.callbackUrl());
            GameState game = registry.get(gameId);
            if (game == null) game = GameState.waiting(gameId); // ???
            RegisteredPlayer existing = game.player(name);
            if (existing != null && existing.callbackUrl().equals(callbackUrl)) {
                return new RegistrationResult(existing.token(), new RegisterResponse(game.publicPlayers()));
            }
            requireGameNotStarted(game);
            if (existing != null) throw new PlayerNameTakenException("Name is already registered in this game");
            if (game.players().size() == 4) throw new GameFullException("A game can have at most four players");
            byte[] secret = new byte[32];
            random.nextBytes(secret);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
            GameState updated = game.add(new RegisteredPlayer(null, name, callbackUrl, token));
            cluster.change(gameId, updated);
            return new RegistrationResult(token, new RegisterResponse(updated.publicPlayers()));
        }
    }

    /**
     * Checks the token and removes a waiting player, deleting the game when empty.
     *
     * @param gameId game ID
     * @param playerName registered player name
     * @param authorization Authorization header containing the Bearer token
     */
    public void unregister(String gameId, String playerName, String authorization) {
        synchronized (cluster) {
            cluster.synchronizeState();
            String name = normalizeName(playerName);
            String token = bearerToken(authorization);
            GameState game = registry.get(gameId);
            if (game == null) {
                return;
            }
            requireGameNotStarted(game);
            RegisteredPlayer player = game.player(name);
            if (player == null) {
                return;
            }
            if (!player.token().equals(token)) throw new InvalidTokenException("Token does not belong to this player");
            GameState updated = game.remove(name);
            cluster.change(gameId, updated.players().isEmpty() ? null : updated);
        }
    }

    /**
     * Checks the token and starts the game once enough players have registered.
     *
     * @param gameId game ID
     * @param authorization Authorization header containing the Bearer token
     */
    public void start(String gameId, String authorization) {
        synchronized (cluster) {
            cluster.synchronizeState();
            String token = bearerToken(authorization);
            GameState game = requiredGame(gameId);
            if (game.players().stream().noneMatch(player -> player.token().equals(token))) {
                throw new InvalidTokenException("Token does not belong to a registered player");
            }
            if (game.status() == GameStatus.STARTED) {
                return;
            }
            if (game.players().size() < 2) throw new NotEnoughPlayersException("At least two players are required");
            cluster.change(gameId, game.start());
        }
    }

    /**
     * Returns the primary's current game status and public player list.
     *
     * @param gameId game ID
     * @return current game status and player list
     */
    public GameStatusResponse status(String gameId) {
        synchronized (cluster) {
            cluster.synchronizeState();
            GameState game = requiredGame(gameId);
            return new GameStatusResponse(game.status().name(), game.publicPlayers());
        }
    }

    /**
     * Finds the game with the given ID.
     *
     * @param gameId game ID
     * @return game with the given ID
     */
    private GameState requiredGame(String gameId) {
        GameState game = registry.get(gameId);
        if (game == null) throw new GameNotFoundException("Game not found");
        return game;
    }

    /**
     * Checks that the game is waiting.
     *
     * @param game game to check
     */
    private static void requireGameNotStarted(GameState game) {
        if (game.status() == GameStatus.STARTED) throw new GameAlreadyStartedException("The player list is frozen");
    }

    /**
     * Trims the name and checks its length.
     *
     * @param name player name
     * @return trimmed player name
     */
    private static String normalizeName(String name) {
        if (name == null || name.trim().isEmpty() || name.trim().length() > 20) {
            throw new InvalidRequestException("Name must contain 1 to 20 characters");
        }
        return name.trim();
    }

    /**
     * Validates the callback base URL and removes trailing slashes.
     *
     * @param callbackUrl client callback base URL
     * @return validated base URL with trailing slashes removed
     */
    private static String normalizeCallback(String callbackUrl) {
        if (callbackUrl == null) throw new InvalidRequestException("Callback URL is required");
        final URI uri;
        try {
            uri = URI.create(callbackUrl);
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("Callback URL must be an absolute HTTP or HTTPS base URL");
        }
        if (uri.getHost() == null || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null || uri.getPort() > 65535 || uri.getPort() == 0) {
            throw new InvalidRequestException("Callback URL must be an absolute HTTP or HTTPS base URL without credentials, query or fragment");
        }
        return callbackUrl.replaceAll("/+$", "");
    }

    /**
     * Extracts the token from a valid Bearer header.
     *
     * @param authorization Authorization header containing the Bearer token
     * @return token extracted from the header
     */
    private static String bearerToken(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7) || authorization.substring(7).isBlank()) {
            throw new MissingTokenException("A Bearer token is required");
        }
        String token = authorization.substring(7).trim();
        if (token.chars().anyMatch(Character::isWhitespace)) throw new InvalidTokenException("Malformed Bearer token");
        return token;
    }
}
