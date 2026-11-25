package com.alcatraz.client;

import java.util.List;

/** JSON messages exchanged with registration servers and game peers. */
public final class Messages {
    private Messages() {}

    public record PlayerInfo(Integer id, String name, String callbackUrl) {}
    public record RegisterRequest(String name, String callbackUrl) {}
    public record RegisterResponse(List<PlayerInfo> players) {}
    public record GameStatusResponse(String status, List<PlayerInfo> players) {}
    public record GameStartedPayload(String gameId, int yourPlayerId, List<PlayerInfo> players) {}
    public record MovePayload(String gameId, int sequenceNumber, int playerId, int prisonerId,
                              int rowOrCol, int row, int col) {}
}
