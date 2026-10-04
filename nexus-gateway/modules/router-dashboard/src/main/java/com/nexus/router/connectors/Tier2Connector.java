package com.nexus.router.connectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Service
public class Tier2Connector {

    private final WebClient webClient;
    private final String apiKey;

    public Tier2Connector(WebClient.Builder webClientBuilder, @Value("${openai.api.key}") String apiKey) {
        this.webClient = webClientBuilder.baseUrl("https://api.openai.com").build();
        this.apiKey = apiKey;
    }

    public Mono<Map> executeInference(Map<String, Object> payload) {
        String prompt = (String) payload.getOrDefault("prompt", "");

        Map<String, Object> openAiRequest = Map.of(
                "model", "gpt-4o",
                "messages", List.of(Map.of("role", "user", "content", prompt)),
                "temperature", payload.getOrDefault("temperature", 0.7)
        );

        return webClient.post()
                .uri("/v1/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .bodyValue(openAiRequest)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    // Normalize response to match the Bedrock JSON structure (which OpenAIChatController expects)
                    try {
                        List<?> choices = (List<?>) response.get("choices");
                        if (choices != null && !choices.isEmpty()) {
                            Map<String, Object> choice = (Map<String, Object>) choices.get(0);
                            Map<String, Object> message = (Map<String, Object>) choice.get("message");
                            String content = (String) message.get("content");
                            Map<String, Object> res = new java.util.HashMap<>();
                            res.put("generation", content);
                            return (Map) res;
                        }
                    } catch (Exception e) {
                        Map<String, Object> err = new java.util.HashMap<>();
                        err.put("error", "Failed to parse OpenAI response: " + e.getMessage());
                        return (Map) err;
                    }
                    Map<String, Object> err = new java.util.HashMap<>();
                    err.put("error", "No choices returned from OpenAI.");
                    return (Map) err;
                })
                .onErrorResume(e -> {
                    Map<String, Object> err = new java.util.HashMap<>();
                    err.put("error", "Tier 2 failure: " + e.getMessage());
                    return Mono.just((Map) err);
                });
    }
}
