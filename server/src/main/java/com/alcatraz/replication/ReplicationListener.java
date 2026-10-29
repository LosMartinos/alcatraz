package com.alcatraz.replication;

import com.alcatraz.domain.GameRegistry;
import org.springframework.stereotype.Component;

/** RPC methods never wait for state transfer; the recovery worker performs it. */
@Component
public class ReplicationListener {
    private final GameRegistry registry;
    private long requiredVersion;

    public ReplicationListener(GameRegistry registry) {
        this.registry = registry;
    }

    public synchronized long applyChange(ReplicationEvent event) {
        requiredVersion = Math.max(requiredVersion, event.version());
        registry.apply(event);
        return ready() ? registry.snapshot().version() : -1;
    }

    public synchronized long acknowledge(long version) {
        requiredVersion = Math.max(requiredVersion, version);
        return ready() ? registry.snapshot().version() : -1;
    }

    public synchronized void preparePromotion() {
        requiredVersion = registry.snapshot().version();
    }

    public synchronized boolean ready() {
        return registry.initialized() && registry.snapshot().version() >= requiredVersion;
    }
}
