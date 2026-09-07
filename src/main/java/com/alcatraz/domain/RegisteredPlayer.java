package com.alcatraz.domain;

import java.io.Serializable;

// Immutable — once a player is registered with an id, that id never changes.
public record RegisteredPlayer(int playerId, String name, String callbackUrl) implements Serializable {

}