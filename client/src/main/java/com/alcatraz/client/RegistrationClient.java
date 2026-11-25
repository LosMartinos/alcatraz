package com.alcatraz.client;

import com.alcatraz.client.Messages.*;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

final class RegistrationClient {
    record Registration(String token, List<PlayerInfo> players) {}
    private final ClientHttp http;
    private final List<String> servers;
    private final Duration timeout;
    private int preferred;

    /**
     * Stores registration server addresses and the request timeout.
     * @param http shared HTTP transport
     * @param servers registration server base URLs
     * @param timeout response timeout
     */
    RegistrationClient(ClientHttp http, List<String> servers, Duration timeout) {
        this.http = http;
        this.servers = List.copyOf(servers);
        this.timeout = timeout;
    }

    /**
     * Registers a player and reads the token from the response header.
     * @param gameId game ID
     * @param name player name
     * @param callbackUrl client address
     * @return token and current player list
     */
    Registration register(String gameId, String name, String callbackUrl) throws IOException, InterruptedException {
        var response = request("POST", path(gameId) + "/players", new RegisterRequest(name, callbackUrl), null);
        String token = response.headers().firstValue("Authorization").orElse("");
        if (!token.startsWith("Bearer ") || token.substring(7).isBlank()) {
            throw new IOException("Registration response has no Bearer token; retrying");
        }
        return new Registration(token, ClientHttp.JSON.readValue(response.body(), RegisterResponse.class).players());
    }

    /**
     * Cancels the player's registration.
     * @param gameId game ID
     * @param name registered name
     * @param token Authorization header
     */
    void cancel(String gameId, String name, String token) throws IOException, InterruptedException {
        request("DELETE", path(gameId) + "/players/" + ClientHttp.segment(name), null, token);
    }

    /**
     * Requests the game start.
     * @param gameId game ID
     * @param token Authorization header
     */
    void start(String gameId, String token) throws IOException, InterruptedException {
        request("POST", path(gameId) + "/start", null, token);
    }

    /**
     * Reads the primary's game state.
     * @param gameId game ID
     * @return status and player list
     */
    GameStatusResponse status(String gameId) throws IOException, InterruptedException {
        return ClientHttp.JSON.readValue(request("GET", path(gameId), null, null).body(), GameStatusResponse.class);
    }

    /**
     * Tries another server after a network failure or 503.
     * @param method HTTP method
     * @param path endpoint path
     * @param body request body
     * @param token Authorization header
     * @return successful response
     */
    private HttpResponse<String> request(String method, String path, Object body, String token)
            throws IOException, InterruptedException {
        IOException failure = null;
        for (int offset = 0; offset < servers.size(); offset++) {
            int index = (preferred + offset) % servers.size();
            try {
                var response = http.send(method, servers.get(index) + path, body, token, timeout);
                if (response.statusCode() == 503) {
                    failure = new IOException("Registration servers are synchronizing; retrying");
                    continue;
                }
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    String message = "Registration request failed (HTTP " + response.statusCode() + ")";
                    try {
                        message = ClientHttp.JSON.readTree(response.body()).path("error").asText(message);
                    } catch (RuntimeException ignored) {
                        // Keep the HTTP status when the response has no JSON error message.
                    }
                    throw new RequestException(response.statusCode(), message);
                }
                preferred = index;
                return response;
            } catch (IOException e) {
                failure = e;
            }
        }
        throw new IOException("No registration server is available; retrying", failure);
    }

    /**
     * Builds the game endpoint path.
     * @param gameId game ID
     * @return encoded game path
     */
    private static String path(String gameId) {
        return "/games/" + ClientHttp.segment(gameId);
    }
}
