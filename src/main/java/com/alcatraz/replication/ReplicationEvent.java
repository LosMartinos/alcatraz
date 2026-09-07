package com.alcatraz.replication;

import com.alcatraz.domain.RegisteredPlayer;

import java.io.Serializable;
import java.util.List;

public sealed interface ReplicationEvent extends Serializable {

    record PlayerRegistered(String gameId, RegisteredPlayer player) implements ReplicationEvent {}

    record PlayerUnregistered(String gameId, String playerName) implements ReplicationEvent {}

    record GameStarted(String gameId, List<RegisteredPlayer> finalRoster) implements ReplicationEvent {}
}