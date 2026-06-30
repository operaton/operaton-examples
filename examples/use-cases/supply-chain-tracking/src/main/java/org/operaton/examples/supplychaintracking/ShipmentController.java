package org.operaton.examples.supplychaintracking;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.operaton.bpm.engine.RuntimeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/shipments")
public class ShipmentController {

    private static final Logger log = LoggerFactory.getLogger(ShipmentController.class);

    private final RuntimeService runtimeService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${kafka.topics.package-events}")
    private String packageEventsTopic;

    @Value("${kafka.topics.customs-events}")
    private String customsEventsTopic;

    public ShipmentController(RuntimeService runtimeService,
                               KafkaTemplate<String, String> kafkaTemplate,
                               ObjectMapper objectMapper) {
        this.runtimeService = runtimeService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> dispatch(@RequestBody ShipmentRequest req) {
        var instance = runtimeService.startProcessInstanceByKey(
            "supply-chain-tracking",
            req.trackingNumber(),
            Map.of(
                "orderId", req.orderId(),
                "trackingNumber", req.trackingNumber(),
                "destination", req.destination()
            )
        );
        log.info("Dispatched shipment {} — process instance {}", req.trackingNumber(), instance.getId());
        return ResponseEntity.ok(Map.of(
            "trackingNumber", req.trackingNumber(),
            "instanceId", instance.getId()
        ));
    }

    @PostMapping("/{trackingNumber}/events/delivered")
    public ResponseEntity<Void> recordDelivered(@PathVariable String trackingNumber) throws JsonProcessingException {
        String payload = objectMapper.writeValueAsString(Map.of("trackingNumber", trackingNumber));
        kafkaTemplate.send(packageEventsTopic, trackingNumber, payload);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{trackingNumber}/events/customs-delay")
    public ResponseEntity<Void> recordCustomsDelay(@PathVariable String trackingNumber,
                                                    @RequestBody(required = false) Map<String, String> body) throws JsonProcessingException {
        Map<String, String> payload = Map.of(
            "trackingNumber", trackingNumber,
            "eta", body != null && body.containsKey("eta") ? body.get("eta") : ""
        );
        kafkaTemplate.send(customsEventsTopic, trackingNumber, objectMapper.writeValueAsString(payload));
        return ResponseEntity.accepted().build();
    }

    public record ShipmentRequest(String orderId, String trackingNumber, String destination) {}
}
