package com.alcatraz.client;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

final class ClientHttp implements AutoCloseable {
    static final JsonMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).build();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    /**
     * Sends a JSON request with a bounded response wait.
     * @param method HTTP method
     * @param url destination URL
     * @param body JSON payload, or null for an empty body
     * @param token Authorization header, or null
     * @param timeout response timeout
     * @return HTTP response
     */
    HttpResponse<String> send(String method, String url, Object body, String token, Duration timeout)
            throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(URI.create(url)).timeout(timeout)
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
        if (token != null) request.header("Authorization", token);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Encodes one URL path segment.
     * @param value unencoded segment
     * @return encoded segment
     */
    static String segment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /**
     * Validates and normalizes a server or callback base URL.
     * @param value supplied URL
     * @return base URL with trailing slashes removed
     */
    static String baseUrl(String value) {
        var uri = URI.create(value.trim());
        if (uri.getHost() == null || !("http".equalsIgnoreCase(uri.getScheme())
                || "https".equalsIgnoreCase(uri.getScheme())) || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null || uri.getPort() == 0
                || uri.getPort() > 65535) {
            throw new IllegalArgumentException("Use an HTTP or HTTPS base URL");
        }
        return uri.toString().replaceAll("/+$", "");
    }

    /** Stops outstanding HTTP requests. */
    @Override
    public void close() {
        http.shutdownNow();
    }
}
