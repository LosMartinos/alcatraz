package com.alcatraz.web;

import com.alcatraz.service.ClientNotifierService;
import com.alcatraz.service.GameRegistrationService;
import com.alcatraz.web.dto.RegisterRequest;
import com.alcatraz.web.dto.RegisterResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/games/{gameId}")
public class RegistrationController {

    private final GameRegistrationService registrationService;
    private final ClientNotifierService clientNotifierService;

    public RegistrationController(GameRegistrationService registrationService,
                                  ClientNotifierService clientNotifierService) {
        this.registrationService = registrationService;
        this.clientNotifierService = clientNotifierService;
    }

    @PostMapping("/players")
    public ResponseEntity<RegisterResponse> register(
            @PathVariable String gameId,
            @RequestBody RegisterRequest req) {
        RegisterResponse resp = registrationService.register(gameId, req);
        return ResponseEntity.ok(resp);
    }

    @DeleteMapping("/players/{playerName}")
    public ResponseEntity<Void> unregister(
            @PathVariable String gameId,
            @PathVariable String playerName) {
        registrationService.unregister(gameId, playerName);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/start")
    public ResponseEntity<Void> start(@PathVariable String gameId) {
        var roster = registrationService.startGame(gameId);
        clientNotifierService.notifyGameStarted(roster);
        return ResponseEntity.accepted().build();
    }
}