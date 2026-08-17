package com.nexus.router.connectors;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

@Service
public class Tier1Connector {

    private final WebClient webClient;

    public Tier1Connector(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl("http://vllm-service:8000").build();
    }

    public Mono<Map> executeInference(Map<String, Object> payload) {
        // Simulating the call to the self-hosted vLLM API
        return webClient.post()
                .uri("/v1/completions")
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(Map.class)
                .onErrorResume(e -> Mono.just(Map.of("error", "Tier 1 failure: " + e.getMessage())));
    }
}
