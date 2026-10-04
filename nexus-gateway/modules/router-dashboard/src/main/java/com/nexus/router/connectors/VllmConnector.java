package com.nexus.router.connectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Alternative Tier 1 Connector — Self-hosted vLLM (Llama 2)
 *
 * Use this connector instead of Tier1Connector to route requests to a local
 * vLLM instance running on GPU nodes within the cluster.
 */
@Service
public class VllmConnector {

    private final WebClient webClient;
    private final String modelId;

    public VllmConnector(
            WebClient.Builder webClientBuilder,
            @Value("${VLLM_INFERENCE_URL:http://nexus-vllm.nexus-gateway.svc.cluster.local:8000}") String vllmUrl,
            @Value("${nexus.vllm.model-id:meta-llama/Llama-2-7b-chat-hf}") String modelId) {
        this.webClient = webClientBuilder.baseUrl(vllmUrl).build();
        this.modelId = modelId;
    }

    public Mono<Map> executeInference(Map<String, Object> payload) {
        String prompt = (String) payload.getOrDefault("prompt", "");

        Map<String, Object> openAiCompatRequest = Map.of(
                "model", modelId,
                "messages", List.of(Map.of("role", "user", "content", prompt)),
                "temperature", payload.getOrDefault("temperature", 0.7),
                "max_tokens", payload.getOrDefault("max_tokens", 512)
        );

        return webClient.post()
                .uri("/v1/chat/completions")
                .bodyValue(openAiCompatRequest)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    // Normalize response
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
                        err.put("error", "Failed to parse vLLM response: " + e.getMessage());
                        return (Map) err;
                    }
                    Map<String, Object> err = new java.util.HashMap<>();
                    err.put("error", "No choices returned from vLLM.");
                    return (Map) err;
                })
                .onErrorResume(e -> {
                    Map<String, Object> err = new java.util.HashMap<>();
                    err.put("error", "Local vLLM failure: " + e.getMessage());
                    return Mono.just((Map) err);
                });
    }
}
