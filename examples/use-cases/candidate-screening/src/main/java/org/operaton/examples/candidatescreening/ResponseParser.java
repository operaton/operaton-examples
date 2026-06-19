package org.operaton.examples.candidatescreening;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class ResponseParser {

    private final ObjectMapper mapper;

    public ResponseParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public int score(String response) {
        return innerContent(response).path("score").asInt();
    }

    public String reasoning(String response) {
        return innerContent(response).path("reasoning").asText();
    }

    public String content(String response) {
        return messageContent(response);
    }

    private String messageContent(String response) {
        try {
            JsonNode root = mapper.readTree(response);
            return root.path("choices").path(0).path("message").path("content").asText();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse LLM response: " + response, e);
        }
    }

    private JsonNode innerContent(String response) {
        try {
            return mapper.readTree(messageContent(response));
        } catch (Exception e) {
            throw new IllegalStateException("LLM message content was not valid JSON", e);
        }
    }
}
