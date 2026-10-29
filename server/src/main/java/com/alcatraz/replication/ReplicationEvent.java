package com.alcatraz.replication;

import com.alcatraz.domain.GameState;
import java.io.Serializable;

/** Null state means that the empty waiting game was removed. */
public record ReplicationEvent(long version, String gameId, GameState state) implements Serializable {}
