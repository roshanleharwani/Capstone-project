package com.nexus.router;

import com.nexus.router.connectors.Tier1Connector;
import com.nexus.router.connectors.Tier2Connector;
import com.nexus.router.guard.CascadeGuard;
import com.nexus.router.stage1.CompressionPredictor;
import com.nexus.router.stage2.ModelClassifier;
import com.nexus.router.telemetry.KafkaTelemetryPublisher;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/v1/router")
public class RouterController {

    private final CompressionPredictor compressionPredictor;
    private final ModelClassifier modelClassifier;
    private final Tier1Connector tier1Connector;
    private final Tier2Connector tier2Connector;
    private final CascadeGuard cascadeGuard;
    private final KafkaTelemetryPublisher telemetryPublisher;

    public RouterController(CompressionPredictor compressionPredictor,
                            ModelClassifier modelClassifier,
                            Tier1Connector tier1Connector,
                            Tier2Connector tier2Connector,
                            CascadeGuard cascadeGuard,
                            KafkaTelemetryPublisher telemetryPublisher) {
        this.compressionPredictor = compressionPredictor;
        this.modelClassifier = modelClassifier;
        this.tier1Connector = tier1Connector;
        this.tier2Connector = tier2Connector;
        this.cascadeGuard = cascadeGuard;
        this.telemetryPublisher = telemetryPublisher;
    }

    @PostMapping("/dispatch")
    public Mono<Map> dispatchRequest(@RequestBody Map<String, Object> requestPayload) {
        long startTime = System.currentTimeMillis();
        
        String modelPref = (String) requestPayload.getOrDefault("model_pref", "auto");
        boolean isHardRoutedTier1 = Boolean.TRUE.equals(requestPayload.get("hard_route_tier1"));

        // 0. Manual Overrides
        if (isHardRoutedTier1 || "tier1".equalsIgnoreCase(modelPref)) {
            return processAndMonitor(tier1Connector.executeInference(requestPayload), "TIER1", 1.0, startTime, requestPayload, false);
        }
        if ("tier2".equalsIgnoreCase(modelPref)) {
            return processAndMonitor(tier2Connector.executeInference(requestPayload), "TIER2", 1.0, startTime, requestPayload, false);
        }

        // 1. Fast-Path Bypass Check
        if (cascadeGuard.shouldFastPath(requestPayload)) {
            telemetryPublisher.publishEvent("FAST_PATH_ROUTED", new HashMap<>(requestPayload));
            return processAndMonitor(tier1Connector.executeInference(requestPayload), "TIER1", 1.0, startTime, requestPayload, true);
        }

        // 2. Stage 1: Predict Compression Ratio
        double rho = compressionPredictor.predictCompressionRatio(requestPayload);
        
        // 3. Stage 2: Classify Model Tier
        String selectedTier = modelClassifier.classifyModelTier(requestPayload, rho);

        // 4. Execution
        Mono<Map> responseMono = "TIER1".equals(selectedTier) ? tier1Connector.executeInference(requestPayload) : tier2Connector.executeInference(requestPayload);

        // 5. Cascade Guard Post-Hoc Check
        return processAndMonitor(responseMono, selectedTier, rho, startTime, requestPayload, true);
    }

    private Mono<Map> processAndMonitor(Mono<Map> responseMono, String selectedTier, double rho, long startTime, Map<String, Object> requestPayload, boolean evaluateCascade) {
        return responseMono.flatMap(response -> {
            boolean useCascadeGuard = Boolean.parseBoolean(System.getProperty("nexus.settings.cascadeGuard", "true"));
            
            if (evaluateCascade && "TIER1".equals(selectedTier) && useCascadeGuard && !cascadeGuard.evaluateReliability(response)) {
                // Escalate: Re-route to a larger model (e.g., Tier 2 fallback)
                telemetryPublisher.publishEvent("CASCADE_GUARD_ESCALATION", new HashMap<>());
                return processAndMonitor(tier2Connector.executeInference(requestPayload), "TIER2", rho, startTime, requestPayload, false);
            }
            
            // Publish Telemetry Success
            Map<String, Object> telemetryData = new HashMap<>();
            telemetryData.put("tier", selectedTier);
            telemetryData.put("compressionRatio", rho);
            telemetryData.put("latencyMs", System.currentTimeMillis() - startTime);
            telemetryPublisher.publishEvent("REQUEST_COMPLETED", telemetryData);
            
            // Add metrics to HTTP response for the proxy to read
            try {
                response.put("router_metrics", telemetryData);
            } catch (Exception e) {
                // In case it's an immutable map
            }

            return Mono.just(response);
        });
    }
}
