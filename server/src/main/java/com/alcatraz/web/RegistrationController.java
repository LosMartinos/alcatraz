package com.alcatraz.web;

import com.alcatraz.service.GameRegistrationService;
import com.alcatraz.web.dto.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/games/{gameId}")
public class RegistrationController {
    private final GameRegistrationService registration;

    /**
     * Stores the registration service.
     *
     * @param registration registration service
     */
    public RegistrationController(GameRegistrationService registration) {
        this.registration = registration;
    }

    /**
     * Registers the player and returns the roster with the token in the response header.
     *
     * @param gameId game ID
     * @param request player name and callback URL
     * @return player list with the token in the response header
     */
    @PostMapping("/players")
    public ResponseEntity<RegisterResponse> register(@PathVariable String gameId,
            @RequestBody RegisterRequest request) {
        var result = registration.register(gameId, request);
        return ResponseEntity.ok().header(HttpHeaders.AUTHORIZATION, "Bearer " + result.token())
                .header(HttpHeaders.CACHE_CONTROL, "no-store").body(result.response());
    }

    /**
     * Cancels a registration and returns an empty success response.
     *
     * @param gameId game ID
     * @param playerName registered player name
     * @param authorization Authorization header containing the Bearer token
     * @return empty success response
     */
    @DeleteMapping("/players/{playerName}")
    public ResponseEntity<Void> unregister(@PathVariable String gameId, @PathVariable String playerName,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        registration.unregister(gameId, playerName, authorization);
        return ResponseEntity.ok().build();
    }

    /**
     * Requests the game start and returns an empty success response.
     *
     * @param gameId game ID
     * @param authorization Authorization header containing the Bearer token
     * @return empty success response
     */
    @PostMapping("/start")
    public ResponseEntity<Void> start(@PathVariable String gameId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        registration.start(gameId, authorization);
        return ResponseEntity.ok().build();
    }

    /**
     * Returns the current game status and roster.
     *
     * @param gameId game ID
     * @return current game status and player list
     */
    @GetMapping
    public GameStatusResponse status(@PathVariable String gameId) {
        return registration.status(gameId);
    }
}
