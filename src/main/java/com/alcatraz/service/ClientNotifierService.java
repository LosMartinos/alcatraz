package com.alcatraz.service;

import com.alcatraz.domain.RegisteredPlayer;
import com.alcatraz.web.dto.GameStartedPayload;
import com.alcatraz.web.dto.PlayerInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

@Service
public class ClientNotifierService {

    private static final Logger logger = LoggerFactory.getLogger(ClientNotifierService.class);

    private final RestClient restClient;

    public ClientNotifierService(RestClient.Builder builder) {
        this.restClient = builder.build(); // RestClient.Builder is auto-configured by Spring Boot, no extra config needed
    }

    public void notifyGameStarted(List<RegisteredPlayer> roster) {
        List<PlayerInfo> playerInfos = roster.stream()
                .map(p -> new PlayerInfo(p.playerId(), p.name(), p.callbackUrl()))
                .toList();

        for (RegisteredPlayer recipient : roster) {
            GameStartedPayload payload = new GameStartedPayload(recipient.playerId(), playerInfos);
            try {
                restClient.post()
                        .uri(recipient.callbackUrl() + "/gameStarted")
                        .body(payload)
                        .retrieve()
                        .toBodilessEntity();
            } catch (Exception e) {
                // one unreachable client must not stop the rest of the roster from
                // being notified — log and keep going
                logger.error("Failed to notify '{}' at {}: {}", recipient.name(), recipient.callbackUrl(), e.getMessage());
            }
        }
    }
}