package com.alcatraz.web.dto;

import java.util.List;

// Outgoing: pushed to each client's POST /gameStarted.
// yourPlayerId differs per recipient — same 'players' list, built once per client.
public record GameStartedPayload(int yourPlayerId, List<PlayerInfo> players) {}