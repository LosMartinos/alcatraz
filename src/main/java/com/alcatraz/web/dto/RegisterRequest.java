package com.alcatraz.web.dto;

// Incoming: POST /games/{gameId}/players
public record RegisterRequest(String playerName, String callbackUrl) {}