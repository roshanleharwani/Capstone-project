package com.nexus.router.telemetry;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class KafkaTelemetryPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final String TOPIC = "nexus-telemetry";

    public KafkaTelemetryPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishEvent(String eventType, Map<String, Object> details) {
        details.put("eventType", eventType);
        details.put("timestamp", System.currentTimeMillis());
        kafkaTemplate.send(TOPIC, details);
    }
}
