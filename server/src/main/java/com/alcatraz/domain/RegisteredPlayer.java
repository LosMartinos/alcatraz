package com.alcatraz.domain;

import com.alcatraz.web.dto.PlayerInfo;
import java.io.Serializable;

public record RegisteredPlayer(Integer id, String name, String callbackUrl, String token) implements Serializable {
    /**
     * Returns a player copy with the assigned ID.
     *
     * @param assignedId assigned player ID
     * @return player copy with the assigned ID
     */
    public RegisteredPlayer withId(int assignedId) {
        return new RegisteredPlayer(assignedId, name, callbackUrl, token);
    }

    /**
     * Returns the player's ID, name and callback URL.
     *
     * @return public player details
     */
    public PlayerInfo publicInfo() {
        return new PlayerInfo(id, name, callbackUrl);
    }

}
