package com.alcatraz.client;

import com.alcatraz.client.Messages.*;
import com.sun.net.httpserver.*;
import tools.jackson.core.JacksonException;
import javax.swing.SwingUtilities;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

final class CallbackServer implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(CallbackServer.class.getName());
    private final HttpServer server;
    private final ExecutorService workers = Executors.newFixedThreadPool(4);
    private final Supplier<GameSession> session;

    /**
     * Starts the callback listener before registration.
     * @param port local port, or zero to choose an available port
     * @param session current session, read on the Swing thread
     */
    CallbackServer(int port, Supplier<GameSession> session) throws IOException {
        this.session = session;
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(workers);
        server.createContext("/", this::handle);
        server.start();
    }

    /**
     * Reads the actual listener port.
     * @return bound TCP port
     */
    int port() {
        return server.getAddress().getPort();
    }

    /**
     * Dispatches callbacks and centrally translates errors to HTTP responses.
     * @param exchange incoming request and response
     */
    private void handle(HttpExchange exchange) throws IOException {
        int status = 200;
        String message = "";
        try {
            String path = exchange.getRequestURI().getPath();
            if (!path.equals("/move") && !path.equals("/gameStarted")) {
                throw new RequestException(404, "Endpoint not found");
            }
            if (!exchange.getRequestMethod().equals("POST")) throw new RequestException(405, "Use POST");
            byte[] body = exchange.getRequestBody().readAllBytes();
            Object payload = path.equals("/move") ? ClientHttp.JSON.readValue(body, MovePayload.class)
                    : ClientHttp.JSON.readValue(body, GameStartedPayload.class);
            var task = new FutureTask<Void>(() -> {
                var current = session.get();
                if (current == null) throw new RequestException(404, "Game not found");
                if (path.equals("/move")) current.receive((MovePayload) payload);
                else current.initialize((GameStartedPayload) payload);
                return null;
            });
            SwingUtilities.invokeLater(task);
            task.get();
        } catch (Exception e) {
            Throwable cause = e instanceof ExecutionException ? e.getCause() : e;
            if (cause instanceof RequestException failure) {
                status = failure.status;
                message = failure.getMessage();
            } else if (cause instanceof JacksonException || cause instanceof IllegalArgumentException) {
                status = 400;
                message = "Invalid callback payload";
            } else {
                if (cause instanceof InterruptedException) Thread.currentThread().interrupt();
                status = 500;
                message = "Callback could not be handled";
                LOG.log(Level.SEVERE, message, cause);
            }
        }
        try {
            if (status == 200) exchange.sendResponseHeaders(200, -1);
            else {
                byte[] response = message.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
                exchange.sendResponseHeaders(status, response.length);
                exchange.getResponseBody().write(response);
            }
        } finally {
            exchange.close();
        }
    }

    /** Stops the listener and callback workers. */
    @Override
    public void close() {
        server.stop(0);
        workers.shutdownNow();
    }
}
