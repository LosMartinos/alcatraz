package com.alcatraz.client;

import com.alcatraz.client.Messages.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ClientNetworkTest {
    @Test
    void registrationFailsOverFromBackupAndReadsHeaderToken() throws Exception {
        var backup = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var primary = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var attempts = new AtomicInteger();
        backup.createContext("/", exchange -> {
            attempts.incrementAndGet();
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });
        primary.createContext("/", exchange -> {
            byte[] body = "{\"players\":[{\"id\":null,\"name\":\"Alice\",\"callbackUrl\":\"http://localhost:9001\"}]}".getBytes();
            exchange.getResponseHeaders().set("Authorization", "Bearer secret");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        backup.start();
        primary.start();
        try (var http = new ClientHttp()) {
            var registration = new RegistrationClient(http, List.of(url(backup), url(primary)), Duration.ofSeconds(2));
            var result = registration.register("demo", "Alice", "http://localhost:9001");
            assertEquals("Bearer secret", result.token());
            assertEquals("Alice", result.players().getFirst().name());
            assertEquals(1, attempts.get());
            registration.register("demo", "Alice", "http://localhost:9001");
            assertEquals(1, attempts.get(), "Reuse the last working server");
        } finally {
            backup.stop(0);
            primary.stop(0);
        }
    }

    @Test
    void moveRetriesDoNotBlockOtherPeersAndFourHundredsArePermanent() throws Exception {
        var flaky = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var healthy = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var flakyAttempts = new AtomicInteger();
        var healthyAttempts = new AtomicInteger();
        var recovered = new CountDownLatch(1);
        var received = new CountDownLatch(1);
        var followingMove = new CountDownLatch(1);
        flaky.createContext("/move", exchange -> {
            int attempt = flakyAttempts.incrementAndGet();
            exchange.sendResponseHeaders(attempt == 1 ? 503 : 200, -1);
            exchange.close();
            if (attempt == 2) recovered.countDown();
        });
        healthy.createContext("/move", exchange -> {
            int attempt = healthyAttempts.incrementAndGet();
            exchange.sendResponseHeaders(attempt == 1 ? 409 : 200, -1);
            exchange.close();
            if (attempt == 1) received.countDown();
            else followingMove.countDown();
        });
        flaky.start();
        healthy.start();
        try (var http = new ClientHttp();
             var sender = new MoveSender(http, List.of(new PlayerInfo(0, "Alice", "http://localhost:1"),
                     new PlayerInfo(1, "Bob", url(flaky)), new PlayerInfo(2, "Charlie", url(healthy))), 0)) {
            sender.broadcast(new MovePayload("demo", 0, 0, 0, 0, 1, 1));
            assertTrue(received.await(2, TimeUnit.SECONDS), "Other peer receives while one retries");
            assertTrue(recovered.await(6, TimeUnit.SECONDS), "Temporary server error is retried");
            assertEquals(1, healthyAttempts.get(), "409 must not be retried");
            sender.broadcast(new MovePayload("demo", 3, 0, 1, 1, 2, 2));
            assertTrue(followingMove.await(3, TimeUnit.SECONDS), "Permanent failure allows later moves");
        } finally {
            flaky.stop(0);
            healthy.stop(0);
        }
    }

    private static String url(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }
}
