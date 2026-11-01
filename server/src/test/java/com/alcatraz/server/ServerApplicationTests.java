package com.alcatraz.server;

import com.alcatraz.replication.ClusterMembershipService;
import com.alcatraz.web.dto.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.ObjectMapper;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "alcatraz.cluster.name=game-flow-test-${random.uuid}"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ServerApplicationTests {
    @DynamicPropertySource
    static void clusterProperties(DynamicPropertyRegistry properties) throws IOException {
        try (var socket = new ServerSocket(0)) {
            int clusterPort = socket.getLocalPort();
            properties.add("alcatraz.cluster.port", () -> clusterPort);
            properties.add("alcatraz.cluster.peers", () -> "127.0.0.1[" + clusterPort + "]");
        }
    }

    @Value("${local.server.port}")
    private int port;
    @Autowired
    private ClusterMembershipService cluster;
    @Autowired
    private ObjectMapper mapper;

    private RestClient http;
    private HttpServer callbacks;
    private String callbackUrl;
    private String game;
    private final LinkedBlockingQueue<GameStartedPayload> notifications = new LinkedBlockingQueue<>();

    @BeforeEach
    void setUp() throws Exception {
        http = RestClient.builder().baseUrl("http://127.0.0.1:" + port).build();
        game = "/games/" + UUID.randomUUID().toString().replace("-", "");
        callbacks = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        callbacks.createContext("/", exchange -> {
            try {
                if (!exchange.getRequestMethod().equals("POST")
                        || !exchange.getRequestURI().getPath().endsWith("/gameStarted")) {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }
                var payload = mapper.readValue(exchange.getRequestBody().readAllBytes(), GameStartedPayload.class);
                exchange.sendResponseHeaders(200, -1);
                notifications.add(payload);
            } finally {
                exchange.close();
            }
        });
        callbacks.start();
        callbackUrl = "http://127.0.0.1:" + callbacks.getAddress().getPort();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (!cluster.canNotify() && System.nanoTime() < deadline) Thread.sleep(50);
        assertTrue(cluster.canNotify(), "Server should become primary");
    }

    @AfterEach
    void tearDown() {
        if (callbacks != null) callbacks.stop(0);
    }

    @Test
    void playersCanRegisterCancelAndStartAGame() throws Exception {
        var alice = register(" Alice ", callbackUrl + "/alice/");
        String aliceToken = alice.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        assertNotNull(aliceToken);
        assertTrue(aliceToken.startsWith("Bearer "));
        assertEquals(List.of(new PlayerInfo(null, "Alice", callbackUrl + "/alice")), alice.getBody().players());

        var retry = register("Alice", callbackUrl + "/alice");
        assertEquals(aliceToken, retry.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        assertEquals(alice.getBody(), retry.getBody());

        var bob = register("Bob", callbackUrl + "/bob");
        String bobToken = bob.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        assertNotEquals(aliceToken, bobToken);
        assertEquals("WAITING", status().status());
        assertEquals(List.of("Alice", "Bob"), status().players().stream().map(PlayerInfo::name).toList());

        cancel("Bob", bobToken);
        assertEquals(List.of("Alice"), status().players().stream().map(PlayerInfo::name).toList());
        assertEquals(2, register("Bob", callbackUrl + "/bob").getBody().players().size());
        start(aliceToken);

        var started = status();
        assertEquals("STARTED", started.status());
        assertEquals(List.of(new PlayerInfo(0, "Alice", callbackUrl + "/alice"),
                new PlayerInfo(1, "Bob", callbackUrl + "/bob")), started.players());

        var first = notifications.poll(10, TimeUnit.SECONDS);
        var second = notifications.poll(10, TimeUnit.SECONDS);
        assertNotNull(first, "First player should receive gameStarted");
        assertNotNull(second, "Second player should receive gameStarted");
        assertNotEquals(first.yourPlayerId(), second.yourPlayerId());
        for (var notification : List.of(first, second)) {
            assertEquals(game.substring("/games/".length()), notification.gameId());
            assertTrue(notification.yourPlayerId() == 0 || notification.yourPlayerId() == 1);
            assertEquals(started.players(), notification.players());
        }

        start(aliceToken);
        assertEquals(started, status());
        assertEquals(aliceToken, register("Alice", callbackUrl + "/alice")
                .getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        var closed = assertThrows(RestClientResponseException.class,
                () -> register("Charlie", callbackUrl + "/charlie"));
        assertEquals(409, closed.getStatusCode().value());
    }

    @Test
    void cancellingTheLastPlayerRemovesTheWaitingGame() {
        var registration = register("Alice", callbackUrl + "/alice");
        String token = registration.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        cancel("Alice", token);
        cancel("Alice", token);
        var missing = assertThrows(RestClientResponseException.class, this::status);
        assertEquals(404, missing.getStatusCode().value());
        assertTrue(missing.getResponseBodyAsString().contains("Game not found"));
        register("Bob", callbackUrl + "/bob");
        assertEquals("WAITING", status().status());
        assertEquals(List.of("Bob"), status().players().stream().map(PlayerInfo::name).toList());
    }

    private ResponseEntity<RegisterResponse> register(String name, String callback) {
        var response = http.post().uri(game + "/players").body(new RegisterRequest(name, callback))
                .retrieve().toEntity(RegisterResponse.class);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        return response;
    }

    private void cancel(String name, String token) {
        assertTrue(http.delete().uri(game + "/players/" + name).header(HttpHeaders.AUTHORIZATION, token)
                .retrieve().toBodilessEntity().getStatusCode().is2xxSuccessful());
    }

    private void start(String token) {
        assertEquals(200, http.post().uri(game + "/start").header(HttpHeaders.AUTHORIZATION, token)
                .retrieve().toBodilessEntity().getStatusCode().value());
    }

    private GameStatusResponse status() {
        return http.get().uri(game).retrieve().body(GameStatusResponse.class);
    }
}
