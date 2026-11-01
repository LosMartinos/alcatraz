package com.alcatraz.replication;

import com.alcatraz.domain.GameRegistry;
import com.alcatraz.domain.GameState;
import com.alcatraz.domain.GameStatus;
import com.alcatraz.exception.NotPrimaryException;
import com.alcatraz.exception.ReplicationUnavailableException;
import jakarta.annotation.PreDestroy;
import org.jgroups.*;
import org.jgroups.blocks.RequestOptions;
import org.jgroups.blocks.ResponseMode;
import org.jgroups.blocks.RpcDispatcher;
import org.jgroups.util.RspList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

@Service
public class ClusterMembershipService implements Receiver {
    private static final Logger log = LoggerFactory.getLogger(ClusterMembershipService.class);
    private final JChannel channel;
    private final GameRegistry registry;
    private final ReplicationListener replica;
    private final ApplicationEventPublisher events;
    private final ScheduledExecutorService recovery = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("cluster-recovery").factory());
    private final Set<String> pendingStarts = new HashSet<>();
    private final long timeout;
    private final String clusterName;
    private RpcDispatcher dispatcher;
    private volatile View view;
    private volatile boolean connected;
    private volatile boolean primaryReady;
    private volatile boolean promotionPending;
    private long confirmedVersion = -1;
    private ViewId confirmedView;

    /**
     * Stores the cluster services and connection settings.
     *
     * @param channel cluster channel
     * @param registry game registry
     * @param replica replication receiver
     * @param events application event publisher
     * @param clusterName cluster name
     * @param timeout timeout in milliseconds
     */
    public ClusterMembershipService(JChannel channel, GameRegistry registry, ReplicationListener replica,
            ApplicationEventPublisher events,
            @Value("${alcatraz.cluster.name}") String clusterName,
            @Value("${alcatraz.cluster.timeout-ms}") long timeout) {
        this.channel = channel;
        this.registry = registry;
        this.replica = replica;
        this.events = events;
        this.clusterName = clusterName;
        this.timeout = timeout;
    }

    /**
     * Joins the cluster and starts the recovery worker.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void connect() throws Exception {
        dispatcher = new RpcDispatcher(channel, replica).setReceiver(this);
        channel.connect(clusterName);
        connected = true;
        recovery.scheduleWithFixedDelay(this::recover, 0, 1, TimeUnit.SECONDS);
    }

    /**
     * Updates membership and marks primary-role changes.
     *
     * @param newView updated cluster membership
     */
    @Override
    public void viewAccepted(View newView) {
        // The first server starts empty; joining servers must receive a snapshot.
        if (view == null && newView.size() == 1) registry.initializeEmpty();
        boolean wasPrimary = isPrimary();
        view = newView;
        if (wasPrimary != isPrimary()) {
            primaryReady = false;
            promotionPending = isPrimary();
        }
        events.publishEvent(newView);
        log.info("Cluster view {}, primary {}", newView.getViewId(), newView.getMembers().getFirst());
        // Do not block JGroups' delivery thread on RPCs or application locks.
    }

    /**
     * Checks whether this node is first in the current membership view.
     *
     * @return true when this node is the primary
     */
    public boolean isPrimary() {
        View current = view;
        return current != null && channel.getAddress() != null
                && current.getMembers().getFirst().equals(channel.getAddress());
    }

    /**
     * Checks whether this node is ready to send start callbacks.
     *
     * @return true when this node can send start callbacks
     */
    public boolean canNotify() {
        return connected && isPrimary() && primaryReady;
    }

    /**
     * Confirms replication before a service operation.
     */
    public synchronized void synchronizeState() {
        ensureReplicated(null);
    }

    /**
     * Applies and replicates a change; null removes the game.
     *
     * @param gameId game ID
     * @param state updated game, or null to remove it
     */
    public synchronized void change(String gameId, GameState state) {
        requirePrimary();
        ReplicationEvent change = registry.change(gameId, state);
        if (state != null && state.status() == GameStatus.STARTED) pendingStarts.add(gameId);
        ensureReplicated(change);
    }

    /**
     * Checks that this node is a ready primary.
     */
    public void requirePrimary() {
        if (!connected || !isPrimary() || !primaryReady || !replica.ready()) {
            throw new NotPrimaryException("Use the current primary; this server is a backup or is synchronizing");
        }
    }

    /**
     * Waits for current peers to confirm the local version, up to the configured timeout.
     *
     * @param change change to replicate, or null to confirm the current version
     */
    private void ensureReplicated(ReplicationEvent change) {
        requirePrimary();
        long version = registry.snapshot().version();
        if (confirmedVersion == version && view.getViewId().equals(confirmedView)) {
            publishStarts();
            return;
        }
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeout);
        boolean sendChange = change != null;
        try {
            while (true) {
                requirePrimary();
                View targetView = view;
                List<Address> peers = targetView.getMembers().stream()
                        .filter(address -> !address.equals(channel.getAddress())).toList();
                long remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
                if (remaining <= 0) throw new ReplicationUnavailableException("Replication timed out; retry the request");
                RspList<Long> responses = peers.isEmpty() ? new RspList<>()
                        : dispatcher.callRemoteMethods(peers, sendChange ? "applyChange" : "acknowledge",
                        new Object[]{sendChange ? change : version},
                        new Class<?>[]{sendChange ? ReplicationEvent.class : long.class},
                        new RequestOptions(ResponseMode.GET_ALL, remaining));
                sendChange = false;
                requirePrimary();
                View after = view;
                boolean complete = true;
                for (Address peer : after.getMembers()) {
                    if (peer.equals(channel.getAddress())) continue;
                    var response = responses.get(peer);
                    if (response == null || !response.wasReceived() || response.hasException()
                            || response.getValue() == null || response.getValue() != version) {
                        complete = false;
                    }
                }
                if (complete) {
                    confirmedVersion = version;
                    confirmedView = after.getViewId();
                    publishStarts();
                    return;
                }
                // GAP responses trigger the backup worker's getState, not a nested RPC.
                // Missing responses from departed members are no longer required.
                Thread.sleep(100);
            }
        } catch (NotPrimaryException | ReplicationUnavailableException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ReplicationUnavailableException("Replication interrupted; retry the request", e);
        } catch (Exception e) {
            throw new ReplicationUnavailableException("Replication failed; retry the request", e);
        }
    }

    /**
     * Queues start notifications after replication or promotion.
     */
    private void publishStarts() {
        if (promotionPending) {
            promotionPending = false;
            pendingStarts.clear();
            events.publishEvent(new StartNotification(registry.snapshot().games().values().stream()
                    .filter(game -> game.status() == GameStatus.STARTED).toList()));
        } else if (!pendingStarts.isEmpty()) {
            var games = pendingStarts.stream().map(registry::get).filter(Objects::nonNull).toList();
            pendingStarts.clear();
            events.publishEvent(new StartNotification(games));
        }
    }

    /**
     * Fetches missing state or finishes promotion in the background.
     */
    private void recover() {
        try {
            if (!connected) return;
            if (!isPrimary()) {
                if (!replica.ready()) channel.getState(view.getMembers().getFirst(), timeout);
            } else if (registry.initialized()) {
                synchronized (this) {
                    if (!isPrimary()) return;
                    if (!primaryReady) {
                        // Keep the last complete state; callers retry unacknowledged changes.
                        replica.preparePromotion();
                        primaryReady = true;
                        confirmedView = null;
                    }
                    ensureReplicated(null);
                }
            }
        } catch (Exception e) {
            // Recovery runs outside HTTP requests. Retry on the next scheduled pass.
            log.warn("Cluster synchronization will retry: {}", e.getMessage());
        }
    }

    /**
     * Writes a complete local snapshot for JGroups state transfer.
     *
     * @param output destination for the snapshot
     */
    @Override
    public void getState(OutputStream output) throws Exception {
        if (!replica.ready()) throw new IllegalStateException("State is not synchronized");
        var objects = new ObjectOutputStream(output);
        objects.writeObject(registry.snapshot());
        objects.flush();
    }

    /**
     * Loads the snapshot received through JGroups.
     *
     * @param input stream containing the snapshot
     */
    @Override
    public void setState(InputStream input) throws Exception {
        var objects = new ObjectInputStream(input);
        registry.install((GameRegistry.Snapshot) objects.readObject());
        log.info("Installed registration state at version {}", registry.snapshot().version());
    }

    /**
     * Stops recovery and closes the cluster connection.
     */
    @PreDestroy
    public void disconnect() {
        connected = false;
        recovery.shutdownNow();
        channel.close();
        if (dispatcher != null) dispatcher.stop();
    }
}
