package com.nexus.router.connectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

import java.util.List;
import java.util.Map;

/**
 * Tier 1 Connector — AWS Bedrock (Llama 3 8B Instruct)
 *
 * Replaces the self-hosted vLLM deployment. Uses the EKS node's IAM instance
 * profile for authentication — no API keys required in configuration.
 *
 * Model: meta.llama3-8b-instruct-v1:0
 * Region: configured via nexus.bedrock.region (defaults to us-east-1)
 */
@Service
public class Tier1Connector {

    private final BedrockRuntimeClient bedrockClient;
    private final ObjectMapper objectMapper;
    private final String modelId;
    private final Tier2Connector tier2Connector;

    public Tier1Connector(
            @Value("${nexus.bedrock.region:us-east-1}") String region,
            @Value("${nexus.bedrock.model-id:meta.llama3-8b-instruct-v1:0}") String modelId,
            Tier2Connector tier2Connector) {
        this.modelId = modelId;
        this.tier2Connector = tier2Connector;
        this.objectMapper = new ObjectMapper();
        this.bedrockClient = BedrockRuntimeClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create()) // Uses EKS instance profile
                .build();
    }

    /**
     * Executes inference against Llama 3 on AWS Bedrock.
     *
     * @param payload expects a "prompt" key with the user's input string
     * @return Mono wrapping the Bedrock response map
     */
    public Mono<Map> executeInference(Map<String, Object> payload) {
        return Mono.fromCallable(() -> {
            String prompt = (String) payload.getOrDefault("prompt", "");

            // Llama 3 Instruct prompt format
            Map<String, Object> requestBody = Map.of(
                    "prompt", "<|begin_of_text|><|start_header_id|>user<|end_header_id|>\n" + prompt + "<|eot_id|><|start_header_id|>assistant<|end_header_id|>",
                    "max_gen_len", payload.getOrDefault("max_tokens", 512),
                    "temperature", payload.getOrDefault("temperature", 0.7),
                    "top_p", 0.9
            );

            String requestJson = objectMapper.writeValueAsString(requestBody);

            InvokeModelRequest request = InvokeModelRequest.builder()
                    .modelId(modelId)
                    .body(SdkBytes.fromUtf8String(requestJson))
                    .contentType("application/json")
                    .accept("application/json")
                    .build();

            InvokeModelResponse response = bedrockClient.invokeModel(request);
            String responseJson = response.body().asUtf8String();
            return objectMapper.readValue(responseJson, Map.class);

        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(e -> {
              System.err.println("Bedrock Inference Failed: " + e.getMessage());
              e.printStackTrace();
              // Bedrock unavailable — fall back to Tier 2 (OpenAI) transparently for the demo.
              // The model field is tagged so the proxy can correctly label the routing decision.
              return tier2Connector.executeInference(payload)
                      .map(response -> {
                          Map<String, Object> fallbackResponse = new java.util.HashMap<>(response);
                          fallbackResponse.put("bedrock_fallback", true);
                          // Tag with gpt model name so the proxy UI shows Tier 2 correctly
                          fallbackResponse.putIfAbsent("model", "gpt-4o");
                          return fallbackResponse;
                      })
                      .onErrorReturn(Map.of(
                          "generation", "Both Tier 1 (Bedrock) and Tier 2 (OpenAI) are currently unreachable.",
                          "model", "unavailable",
                          "error", true
                      ));
          });
    }
}
