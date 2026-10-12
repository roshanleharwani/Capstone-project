package com.nexus.router.connectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Tier-3 connector — self-hosted vLLM (Llama 3 8B Instruct) on AWS GPU nodes
 * ({@code g4dn.xlarge} in {@code ap-south-1}, service {@code nexus-vllm:8000}).
 *
 * <p>Used as cascade fallback when Bedrock / OpenAI are unavailable or the
 * router elects the local model. Requires the GPU node group to be scaled up
 * (see {@code deploy/scripts/llm-gpu-scale.ps1}).
 */
@Service
public class VllmConnector {

    private final WebClient webClient;
    private final String modelId;

    public VllmConnector(
            WebClient.Builder webClientBuilder,
            @Value("${VLLM_INFERENCE_URL:http://nexus-vllm.nexus-gateway.svc.cluster.local:8000}") String vllmUrl,
            @Value("${nexus.vllm.model-id:llama3-8b-instruct}") String modelId) {
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
