package com.alcatraz.replication;

import com.alcatraz.domain.GameRegistry;
import com.alcatraz.domain.GameState;
import org.jgroups.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ReplicationListener {

    private static final Logger logger = LoggerFactory.getLogger(ReplicationListener.class);

    private final GameRegistry gameRegistry;

    public ReplicationListener(GameRegistry gameRegistry) {
        this.gameRegistry = gameRegistry;
    }

    public void onMessage(Message msg) {
        Object payload = msg.getObject();
        if (!(payload instanceof ReplicationEvent event)) {
            logger.warn("Received non-ReplicationEvent message, ignoring: {}", payload);
            return;
        }

        switch (event) {
            case ReplicationEvent.PlayerRegistered e -> apply(e);
            case ReplicationEvent.PlayerUnregistered e -> apply(e);
            case ReplicationEvent.GameStarted e -> apply(e);
        }
    }

    private void apply(ReplicationEvent.PlayerRegistered e) {
        GameState game = gameRegistry.getOrCreate(e.gameId());
        synchronized (game) {
            if (!game.hasPlayerNamed(e.player().name())) {
                game.addPlayer(e.player());
            }
            logger.info("Replicated: {} joined {}", e.player().name(), e.gameId());
        }
    }

    private void apply(ReplicationEvent.PlayerUnregistered e) {
        GameState game = gameRegistry.get(e.gameId());
        if (game == null) return;
        synchronized (game) {
            game.removePlayer(e.playerName());
            logger.info("Replicated: {} left {}", e.playerName(), e.gameId());
        }
    }

    private void apply(ReplicationEvent.GameStarted e) {
        GameState game = gameRegistry.getOrCreate(e.gameId());
        synchronized (game) {
            game.markStarted();
            logger.info("Replicated: game {} started with {} players", e.gameId(), e.finalRoster().size());
        }
    }
}