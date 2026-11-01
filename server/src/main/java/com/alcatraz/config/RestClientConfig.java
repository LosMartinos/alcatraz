package com.alcatraz.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {
    /**
     * Creates the HTTP client with connection and response timeouts.
     *
     * @param builder HTTP client builder
     * @param timeout timeout in milliseconds
     * @return configured HTTP client
     */
    @Bean
    public RestClient restClient(RestClient.Builder builder,
            @Value("${alcatraz.notification.http-timeout-ms}") int timeout) {
        if (timeout <= 0) throw new IllegalArgumentException("HTTP timeouts must be positive");
        var requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(timeout);
        requests.setReadTimeout(timeout);
        return builder.requestFactory(requests).build();
    }
}
