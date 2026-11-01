package com.alcatraz.replication;

import com.alcatraz.domain.GameRegistry;
import org.springframework.stereotype.Component;

/** Receives replication calls and tracks the required state version. */
@Component
public class ReplicationListener {
    private final GameRegistry registry;
    private long requiredVersion;

    /**
     * Stores the local game registry.
     *
     * @param registry game registry
     */
    public ReplicationListener(GameRegistry registry) {
        this.registry = registry;
    }

    /**
     * Applies a change and returns the local version, or -1 if state is missing.
     *
     * @param event replication event
     * @return local version, or -1 if required state is missing
     */
    public synchronized long applyChange(ReplicationEvent event) {
        requiredVersion = Math.max(requiredVersion, event.version());
        registry.apply(event);
        return ready() ? registry.snapshot().version() : -1;
    }

    /**
     * Returns the local version, or -1 if the requested version is missing.
     *
     * @param version state version
     * @return local version, or -1 if required state is missing
     */
    public synchronized long acknowledge(long version) {
        requiredVersion = Math.max(requiredVersion, version);
        return ready() ? registry.snapshot().version() : -1;
    }

    /**
     * Sets the last complete local state as the starting point after takeover.
     */
    public synchronized void preparePromotion() {
        requiredVersion = registry.snapshot().version();
    }

    /**
     * Checks whether the registry has reached the required version.
     *
     * @return true when the required state is available
     */
    public synchronized boolean ready() {
        return registry.initialized() && registry.snapshot().version() >= requiredVersion;
    }
}
