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

    public RegistrationController(GameRegistrationService registration) {
        this.registration = registration;
    }

    @PostMapping("/players")
    public ResponseEntity<RegisterResponse> register(@PathVariable String gameId,
            @RequestBody RegisterRequest request) {
        var result = registration.register(gameId, request);
        return ResponseEntity.ok().header(HttpHeaders.AUTHORIZATION, "Bearer " + result.token())
                .header(HttpHeaders.CACHE_CONTROL, "no-store").body(result.response());
    }

    @DeleteMapping("/players/{playerName}")
    public ResponseEntity<Void> unregister(@PathVariable String gameId, @PathVariable String playerName,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        registration.unregister(gameId, playerName, authorization);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/start")
    public ResponseEntity<Void> start(@PathVariable String gameId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        registration.start(gameId, authorization);
        return ResponseEntity.ok().build();
    }

    @GetMapping
    public GameStatusResponse status(@PathVariable String gameId) {
        return registration.status(gameId);
    }
}
