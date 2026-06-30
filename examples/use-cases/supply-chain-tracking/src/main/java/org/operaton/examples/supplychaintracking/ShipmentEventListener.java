package org.operaton.examples.supplychaintracking;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.operaton.bpm.engine.MismatchingMessageCorrelationException;
import org.operaton.bpm.engine.RuntimeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ShipmentEventListener {

    private static final Logger log = LoggerFactory.getLogger(ShipmentEventListener.class);

    private final RuntimeService runtimeService;
    private final ObjectMapper objectMapper;

    public ShipmentEventListener(RuntimeService runtimeService, ObjectMapper objectMapper) {
        this.runtimeService = runtimeService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${kafka.topics.package-events}", groupId = "${spring.kafka.consumer.group-id}")
    public void onPackageDelivered(String payload) {
        String trackingNumber = extractTrackingNumber(payload);
        if (trackingNumber == null) return;
        try {
            runtimeService.createMessageCorrelation("PackageDelivered")
                .processInstanceBusinessKey(trackingNumber)
                .setVariable("deliveryPayload", payload)
                .correlate();
            log.info("Correlated PackageDelivered for tracking number {}", trackingNumber);
        } catch (MismatchingMessageCorrelationException e) {
            log.warn("No process instance waiting for PackageDelivered with tracking number: {}", trackingNumber);
        }
    }

    @KafkaListener(topics = "${kafka.topics.customs-events}", groupId = "${spring.kafka.consumer.group-id}")
    public void onCustomsDelay(String payload) {
        String trackingNumber = extractTrackingNumber(payload);
        if (trackingNumber == null) return;
        try {
            runtimeService.createMessageCorrelation("CustomsDelay")
                .processInstanceBusinessKey(trackingNumber)
                .setVariable("customsPayload", payload)
                .correlate();
            log.info("Correlated CustomsDelay for tracking number {}", trackingNumber);
        } catch (MismatchingMessageCorrelationException e) {
            log.warn("No process instance waiting for CustomsDelay with tracking number: {}", trackingNumber);
        }
    }

    private String extractTrackingNumber(String payload) {
        try {
            Map<String, Object> map = objectMapper.readValue(payload, new TypeReference<>() {});
            return (String) map.get("trackingNumber");
        } catch (Exception e) {
            log.error("Failed to parse payload: {}", payload, e);
            return null;
        }
    }
}
