package org.operaton.examples.expensereimbursement;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class ResponseParser {
    private static final Logger log = LoggerFactory.getLogger(ResponseParser.class);
    private static final Set<String> VALID_MATCH = Set.of("MATCH", "MISMATCH", "UNRELATED");

    private final ObjectMapper mapper;

    public ResponseParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String matchResult(String response) {
        try {
            String result = content(response).path("matchResult").asText("UNRELATED").toUpperCase();
            if (!VALID_MATCH.contains(result)) {
                log.warn("Unexpected matchResult '{}' from LLM — defaulting to UNRELATED", result);
                return "UNRELATED";
            }
            return result;
        } catch (Exception e) {
            log.warn("Failed to parse matchResult — defaulting to UNRELATED: {}", e.getMessage());
            return "UNRELATED";
        }
    }

    public String extractedName(String response) {
        try { return content(response).path("extractedName").asText(""); }
        catch (Exception e) { return ""; }
    }

    public double extractedCost(String response) {
        try { return content(response).path("extractedCost").asDouble(0.0); }
        catch (Exception e) { return 0.0; }
    }

    public String analysisNotes(String response) {
        try { return content(response).path("analysisNotes").asText(""); }
        catch (Exception e) { return ""; }
    }

    public String emailBody(String response) {
        try {
            return rawContent(response);
        } catch (Exception e) {
            log.warn("Failed to parse emailBody: {}", e.getMessage());
            return "";
        }
    }

    private JsonNode content(String response) throws Exception {
        return mapper.readTree(rawContent(response));
    }

    private String rawContent(String response) throws Exception {
        return mapper.readTree(response)
            .path("choices").path(0).path("message").path("content").asText();
    }
}
