package org.operaton.examples.externaltaskworker;

import org.operaton.bpm.client.ExternalTaskClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "operaton.worker.enabled", havingValue = "true", matchIfMissing = true)
public class ExternalWorkerConfig {

    @Value("${operaton.worker.base-url:http://localhost:8080/engine-rest}")
    private String baseUrl;

    // destroyMethod = "stop" terminates the background polling threads cleanly
    @Bean(destroyMethod = "stop")
    public ExternalTaskClient externalTaskClient(
            InventoryCheckHandler inventoryCheckHandler,
            ShippingHandler shippingHandler) {
        ExternalTaskClient client = ExternalTaskClient.create()
            .baseUrl(baseUrl)
            .lockDuration(10_000)
            .build();
        client.subscribe("inventory-check").handler(inventoryCheckHandler).open();
        client.subscribe("arrange-shipping").handler(shippingHandler).open();
        return client;
    }
}
