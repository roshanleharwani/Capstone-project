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

        // 1. Fast-Path Bypass Check
        if (cascadeGuard.shouldFastPath(requestPayload)) {
            telemetryPublisher.publishEvent("FAST_PATH_ROUTED", new HashMap<>(requestPayload));
            return tier1Connector.executeInference(requestPayload); // Send to cheap default model
        }

        // 2. Stage 1: Predict Compression Ratio
        double rho = compressionPredictor.predictCompressionRatio(requestPayload);
        
        // 3. Stage 2: Classify Model Tier
        String selectedTier = modelClassifier.classifyModelTier(requestPayload, rho);

        // 4. Execution
        Mono<Map> responseMono;
        if ("TIER1".equals(selectedTier)) {
            responseMono = tier1Connector.executeInference(requestPayload);
        } else {
            responseMono = tier2Connector.executeInference(requestPayload);
        }

        // 5. Cascade Guard Post-Hoc Check
        return responseMono.flatMap(response -> {
            if (!cascadeGuard.evaluateReliability(response)) {
                // Escalate: Re-route to a larger model (e.g., Tier 2 fallback)
                telemetryPublisher.publishEvent("CASCADE_GUARD_ESCALATION", new HashMap<>());
                return tier2Connector.executeInference(requestPayload);
            }
            
            // Publish Telemetry Success
            Map<String, Object> telemetryData = new HashMap<>();
            telemetryData.put("tier", selectedTier);
            telemetryData.put("compressionRatio", rho);
            telemetryData.put("latencyMs", System.currentTimeMillis() - startTime);
            telemetryPublisher.publishEvent("REQUEST_COMPLETED", telemetryData);
            
            return Mono.just(response);
        });
    }
}
