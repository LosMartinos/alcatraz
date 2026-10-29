package com.alcatraz.replication;

import com.alcatraz.domain.GameState;
import java.util.List;

public record StartNotification(List<GameState> games) {}
