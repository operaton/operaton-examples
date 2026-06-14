package org.operaton.examples.integrationkafka.delegate;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component("publishOrderResultDelegate")
public class PublishOrderResultDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(PublishOrderResultDelegate.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String resultsTopic;

    public PublishOrderResultDelegate(
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${kafka.topics.results}") String resultsTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.resultsTopic = resultsTopic;
    }

    @Override
    public void execute(DelegateExecution execution) {
        String orderId = (String) execution.getVariable("orderId");
        String result = "PROCESSED:" + orderId;
        log.info("Publishing result for order {}: {}", orderId, result);
        kafkaTemplate.send(resultsTopic, orderId, result);
        execution.setVariable("orderResult", result);
    }
}
