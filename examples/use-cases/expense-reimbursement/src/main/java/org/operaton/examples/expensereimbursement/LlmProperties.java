package org.operaton.examples.expensereimbursement;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("llm")
public class LlmProperties {
    private String baseUrl;
    private String apiKey;
    private String model;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getChatCompletionsUrl() {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("llm.base-url must be configured");
        }
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return normalized + "/v1/chat/completions";
    }
    public String getAuthorizationHeader() { return "Bearer " + apiKey; }
}
