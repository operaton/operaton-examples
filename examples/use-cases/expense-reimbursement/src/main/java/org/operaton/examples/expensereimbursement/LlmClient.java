package org.operaton.examples.expensereimbursement;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class LlmClient {
    private final LlmProperties llm;
    private final RestTemplate rest = new RestTemplate();

    public LlmClient(LlmProperties llm) {
        this.llm = llm;
    }

    public String call(String requestBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", llm.getAuthorizationHeader());
        return rest.postForObject(
            llm.getChatCompletionsUrl(),
            new HttpEntity<>(requestBody, headers),
            String.class);
    }
}
