package com.nexus.router.connectors;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

@Service
public class Tier2Connector {

    private final WebClient webClient;

    public Tier2Connector(WebClient.Builder webClientBuilder) {
        // Example with OpenAI compatible API
        this.webClient = webClientBuilder.baseUrl("https://api.openai.com").build();
    }

    public Mono<Map> executeInference(Map<String, Object> payload) {
        // Extract original prompt or messages if payload is compressed
        return webClient.post()
                .uri("/v1/chat/completions")
                // Typically you'd inject an API key here via headers
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(Map.class)
                .onErrorResume(e -> Mono.just(Map.of("error", "Tier 2 failure: " + e.getMessage())));
    }
}
