package com.alcatraz.web.dto;

// Reusable shape — one entry in a game's roster
public record PlayerInfo(int playerId, String name, String callbackUrl) {}