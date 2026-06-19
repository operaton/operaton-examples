package org.operaton.examples.candidatescreening;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("calendar")
public class CalendarProperties {
    private String baseUrl;
    private String apiKey;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getFreeBusyUrl() { return baseUrl + "/v1/freebusy"; }
    public String getAuthorizationHeader() { return "Bearer " + apiKey; }
}
