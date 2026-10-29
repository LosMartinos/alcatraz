package com.alcatraz.web.dto;

import java.util.List;

public record GameStartedPayload(String gameId, int yourPlayerId, List<PlayerInfo> players) {}
