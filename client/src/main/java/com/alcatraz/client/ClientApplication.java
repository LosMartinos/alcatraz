package com.alcatraz.client;

import javax.swing.*;
import java.net.*;
import java.time.Duration;
import java.util.*;

public final class ClientApplication {
    /**
     * Starts the Swing client using command-line settings.
     * @param args server list, callback host/port and registration timeout overrides
     */
    public static void main(String[] args) throws Exception {
        var options = new HashMap<String, String>();
        for (String arg : args) {
            if (arg.equals("--help")) {
                System.out.println("Options: --servers=http://localhost:8080,http://localhost:8081 "
                        + "--callback-host=LAN-IP --callback-port=9001 --registration-timeout-ms=20000");
                return;
            }
            int separator = arg.indexOf('=');
            if (!arg.startsWith("--") || separator < 3) throw new IllegalArgumentException("Use --option=value");
            String key = arg.substring(2, separator);
            if (!Set.of("servers", "callback-host", "callback-port", "registration-timeout-ms").contains(key)) {
                throw new IllegalArgumentException("Unknown option: " + key);
            }
            options.put(key, arg.substring(separator + 1));
        }
        var servers = Arrays.stream(options.getOrDefault("servers",
                "http://127.0.0.1:8080,http://127.0.0.1:8081").split(",")).map(ClientHttp::baseUrl).toList();
        int port = Integer.parseInt(options.getOrDefault("callback-port", "0"));
        long timeout = Long.parseLong(options.getOrDefault("registration-timeout-ms", "20000"));
        if (port < 0 || port > 65535 || timeout <= 0) throw new IllegalArgumentException("Invalid port or timeout");
        String host = options.get("callback-host");
        if (host == null) host = detectHost(URI.create(servers.getFirst()));
        host = InetAddress.getByName(host).getHostAddress();
        if (InetAddress.getByName(host).isAnyLocalAddress()) {
            throw new IllegalArgumentException("Callback host must be a reachable address");
        }
        String callbackHost = host;
        var http = new ClientHttp();
        var registration = new RegistrationClient(http, servers, Duration.ofMillis(timeout));
        SwingUtilities.invokeAndWait(() -> {
            try {
                new LobbyWindow(http, registration, callbackHost, port).setVisible(true);
            } catch (Exception e) {
                http.close();
                JOptionPane.showMessageDialog(null, e.getMessage(), "Cannot start Alcatraz", JOptionPane.ERROR_MESSAGE);
                throw new IllegalStateException("Client startup failed", e);
            }
        });
    }

    /**
     * Finds the local address used to reach the first registration server.
     * @param server first configured server
     * @return local interface address
     */
    private static String detectHost(URI server) throws Exception {
        var address = InetAddress.getByName(server.getHost());
        if (address.isLoopbackAddress()) return address.getHostAddress();
        try (var socket = new DatagramSocket()) {
            socket.connect(address, server.getPort() > 0 ? server.getPort() : 80);
            var local = socket.getLocalAddress();
            if (local.isAnyLocalAddress()) {
                throw new IllegalArgumentException("Cannot detect callback host; set --callback-host=LAN-IP");
            }
            return local.getHostAddress();
        }
    }
}
