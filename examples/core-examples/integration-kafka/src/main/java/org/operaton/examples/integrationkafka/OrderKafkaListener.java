package org.operaton.examples.integrationkafka;

import org.operaton.bpm.engine.RuntimeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class OrderKafkaListener {

    private static final Logger log = LoggerFactory.getLogger(OrderKafkaListener.class);

    private final RuntimeService runtimeService;

    public OrderKafkaListener(RuntimeService runtimeService) {
        this.runtimeService = runtimeService;
    }

    @KafkaListener(topics = "${kafka.topics.orders}", groupId = "${spring.kafka.consumer.group-id}")
    public void onOrderReceived(String orderId) {
        log.info("Received order: {}", orderId);
        runtimeService.startProcessInstanceByKey(
            "order-processing",
            orderId,
            Map.of("orderId", orderId, "status", "received")
        );
    }
}
