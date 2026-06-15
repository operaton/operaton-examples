package org.operaton.examples.integrationrest.delegate;

import org.operaton.bpm.engine.delegate.BpmnError;
import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component("checkInventoryDelegate")
public class CheckInventoryDelegate implements JavaDelegate {

    private final RestTemplate restTemplate;
    private final String inventoryServiceUrl;

    public CheckInventoryDelegate(
            RestTemplate restTemplate,
            @Value("${inventory.service.url}") String inventoryServiceUrl) {
        this.restTemplate = restTemplate;
        this.inventoryServiceUrl = inventoryServiceUrl;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void execute(DelegateExecution execution) {
        String productId = (String) execution.getVariable("productId");
        Integer quantity = (Integer) execution.getVariable("quantity");

        String url = inventoryServiceUrl + "/inventory/" + productId;
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            Map<String, Object> body = response.getBody();
            boolean available = Boolean.TRUE.equals(body != null ? body.get("available") : false);
            execution.setVariable("inventoryAvailable", available);
        } catch (HttpClientErrorException e) {
            // 4xx: product not found or invalid request — model as BPMN error
            throw new BpmnError("INVENTORY_ERROR", "Inventory check failed: " + e.getMessage());
        } catch (HttpServerErrorException e) {
            // 5xx: transient failure — let engine retry
            throw new RuntimeException("Inventory service unavailable: " + e.getMessage(), e);
        }
    }
}
