package org.operaton.examples.orderfulfillment.delegate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component("inventoryDelegate")
public class InventoryDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(InventoryDelegate.class);

    @Value("${inventory.service.url:http://localhost:8089}")
    private String inventoryServiceUrl;

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        Object orderId = execution.getVariable("orderId");
        String id = orderId != null ? orderId.toString() : "unknown";
        String url = inventoryServiceUrl + "/inventory/" + id;

        String response = new RestTemplate().getForObject(url, String.class);
        JsonNode json = new ObjectMapper().readTree(response);
        boolean available = json.path("available").asBoolean(true);

        execution.setVariable("available", available);
        execution.setVariable("orderStatus", "RECEIVED");
        log.info("Inventory check for order {}: available={}", id, available);
    }
}
