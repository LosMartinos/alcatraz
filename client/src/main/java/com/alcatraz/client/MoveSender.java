package com.alcatraz.client;

import com.alcatraz.client.Messages.*;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.logging.Logger;

/** Gives each peer its own outgoing queue and worker. */
final class MoveSender implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(MoveSender.class.getName());
    private final ClientHttp http;
    private final List<Peer> peers;
    private final ExecutorService workers = Executors.newCachedThreadPool(Thread.ofPlatform().daemon().factory());

    /**
     * Starts a sender for each remote player.
     * @param http shared HTTP transport
     * @param players frozen roster
     * @param localId local player ID
     */
    MoveSender(ClientHttp http, List<PlayerInfo> players, int localId) {
        this.http = http;
        peers = players.stream().filter(p -> p.id() != localId).map(Peer::new).toList();
        peers.forEach(peer -> workers.submit(() -> deliver(peer)));
    }

    /**
     * Queues the local move for every peer.
     * @param move completed local move
     */
    void broadcast(MovePayload move) {
        peers.forEach(peer -> peer.queue.add(move));
    }

    /**
     * Sends queued moves, retrying temporary failures.
     * @param peer recipient and its queue
     */
    private void deliver(Peer peer) {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                var move = peer.queue.take();
                boolean retry;
                do {
                    retry = false;
                    try {
                        int status = http.send("POST", peer.player.callbackUrl() + "/move", move, null,
                                Duration.ofSeconds(3)).statusCode();
                        if (status >= 500 && status <= 599) retry = true;
                        else if (status != 200) LOG.warning("Dropped move " + move.sequenceNumber()
                                + " for " + peer.player.name() + ": HTTP " + status);
                    } catch (IOException e) {
                        retry = true;
                    }
                    if (retry) {
                        LOG.info("Waiting to deliver move " + move.sequenceNumber() + " to " + peer.player.name());
                        Thread.sleep(2000);
                    }
                } while (retry);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Stops all peer sender workers. */
    @Override
    public void close() {
        workers.shutdownNow();
    }

    private static final class Peer {
        final PlayerInfo player;
        final BlockingQueue<MovePayload> queue = new LinkedBlockingQueue<>();

        Peer(PlayerInfo player) {
            this.player = player;
        }
    }
}
