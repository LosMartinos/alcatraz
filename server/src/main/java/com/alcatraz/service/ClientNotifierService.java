package com.alcatraz.service;

import com.alcatraz.replication.ClusterMembershipService;
import com.alcatraz.replication.StartNotification;
import com.alcatraz.web.dto.GameStartedPayload;
import jakarta.annotation.PreDestroy;
import org.jgroups.View;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import java.util.concurrent.*;

@Service
public class ClientNotifierService {
    private static final Logger log = LoggerFactory.getLogger(ClientNotifierService.class);
    private final RestClient client;
    private final ClusterMembershipService cluster;
    private ScheduledExecutorService sender = Executors.newScheduledThreadPool(4);
    private final long retryMs;
    private final long windowMs;

    public ClientNotifierService(RestClient client, ClusterMembershipService cluster,
            @Value("${alcatraz.notification.retry-ms}") long retryMs,
            @Value("${alcatraz.notification.window-ms}") long windowMs) {
        if (retryMs <= 0 || windowMs <= 0) throw new IllegalArgumentException("Retry times must be positive");
        this.client = client;
        this.cluster = cluster;
        this.retryMs = retryMs;
        this.windowMs = windowMs;
    }

    @EventListener
    public synchronized void membershipChanged(View view) {
        if (!cluster.isPrimary()) sender.shutdownNow();
    }

    @EventListener
    public synchronized void notifyStarted(StartNotification event) {
        if (!cluster.canNotify()) return;
        if (sender.isShutdown()) sender = Executors.newScheduledThreadPool(4);
        // Jobs keep their own executor. Cancelled jobs cannot restart after a later promotion.
        ScheduledExecutorService jobs = sender;
        for (var game : event.games()) {
            for (var player : game.players()) {
                var payload = new GameStartedPayload(game.gameId(), player.id(), game.publicPlayers());
                long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(windowMs);
                jobs.execute(() -> deliver(jobs, player.callbackUrl(), payload, deadline));
            }
        }
    }

    private void deliver(ScheduledExecutorService jobs, String callbackUrl,
            GameStartedPayload payload, long deadline) {
        if (jobs.isShutdown() || !cluster.canNotify()) return;
        if (System.nanoTime() >= deadline) {
            log.warn("Start notification expired for game {}, player {}", payload.gameId(), payload.yourPlayerId());
            return;
        }
        try {
            var response = client.post().uri(callbackUrl + "/gameStarted").body(payload)
                    .retrieve().toBodilessEntity();
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RestClientException("Start callback was not acknowledged");
            }
        } catch (RestClientException e) {
            log.warn("Start notification failed for game {}, player {}; retrying",
                    payload.gameId(), payload.yourPlayerId());
            synchronized (this) {
                if (!jobs.isShutdown() && cluster.canNotify()) {
                    jobs.schedule(() -> deliver(jobs, callbackUrl, payload, deadline), retryMs, TimeUnit.MILLISECONDS);
                }
            }
        }
    }

    @PreDestroy
    public synchronized void close() {
        sender.shutdownNow();
    }
}
