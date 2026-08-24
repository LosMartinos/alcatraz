package com.alcatraz.web.dto;

import java.util.List;

// Outgoing: response to a successful registration
public record RegisterResponse(int assignedPlayerId, List<String> currentPlayers) {}