package com.alcatraz.domain;

import com.alcatraz.web.dto.PlayerInfo;
import java.io.Serializable;

public record RegisteredPlayer(Integer id, String name, String callbackUrl, String token) implements Serializable {
    public RegisteredPlayer withId(int assignedId) {
        return new RegisteredPlayer(assignedId, name, callbackUrl, token);
    }

    public PlayerInfo publicInfo() {
        return new PlayerInfo(id, name, callbackUrl);
    }

}
