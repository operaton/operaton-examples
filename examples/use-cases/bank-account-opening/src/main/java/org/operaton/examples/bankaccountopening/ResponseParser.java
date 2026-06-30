package org.operaton.examples.bankaccountopening;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class ResponseParser {

    private static final Logger log = LoggerFactory.getLogger(ResponseParser.class);
    private static final Set<String> VALID_RISK = Set.of("LOW", "MEDIUM", "HIGH");

    private final ObjectMapper mapper;

    public ResponseParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    // Called from BPMN connector output mapping: ${responseParser.risk(response)}
    public String risk(String response) {
        try {
            String content = messageContent(response);
            String risk = mapper.readTree(content).path("risk").asText("HIGH").toUpperCase();
            if (!VALID_RISK.contains(risk)) {
                log.warn("Unexpected risk value '{}' from LLM — defaulting to HIGH", risk);
                return "HIGH";
            }
            return risk;
        } catch (Exception e) {
            log.warn("Failed to parse LLM background-check response — defaulting to HIGH: {}", e.getMessage());
            return "HIGH";
        }
    }

    // Called from BPMN connector output mapping: ${responseParser.rationale(response)}
    public String rationale(String response) {
        try {
            String content = messageContent(response);
            return mapper.readTree(content).path("rationale").asText("No rationale provided");
        } catch (Exception e) {
            return "Unable to extract rationale";
        }
    }

    private String messageContent(String response) throws Exception {
        JsonNode root = mapper.readTree(response);
        return root.path("choices").path(0).path("message").path("content").asText();
    }
}
