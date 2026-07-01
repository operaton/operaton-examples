package org.operaton.examples.expensereimbursement;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResponseParserTest {

    private ResponseParser parser;

    @BeforeEach
    void setUp() {
        parser = new ResponseParser(new ObjectMapper());
    }

    private String wrap(String content) {
        return "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":" +
            new ObjectMapper().createObjectNode().put("x", content).get("x").toString() + "}}]}";
    }

    @Test
    void matchResult_parsesMatchCorrectly() {
        String response = wrap("{\\\"matchResult\\\": \\\"MATCH\\\", \\\"extractedName\\\": \\\"Cafe\\\", \\\"extractedCost\\\": 12.5, \\\"analysisNotes\\\": \\\"ok\\\"}");
        // Build proper nested JSON
        String inner = "{\"matchResult\": \"MATCH\", \"extractedName\": \"Cafe\", \"extractedCost\": 12.5, \"analysisNotes\": \"ok\"}";
        String outer = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":" +
            new ObjectMapper().createObjectNode().put("v", inner).get("v").toString() + "}}]}";
        assertThat(parser.matchResult(outer)).isEqualTo("MATCH");
        assertThat(parser.extractedName(outer)).isEqualTo("Cafe");
        assertThat(parser.extractedCost(outer)).isEqualTo(12.5);
    }

    @Test
    void matchResult_defaultsToUnrelatedOnInvalidJson() {
        assertThat(parser.matchResult("not-json")).isEqualTo("UNRELATED");
    }

    @Test
    void matchResult_defaultsToUnrelatedOnUnknownValue() {
        String inner = "{\"matchResult\": \"UNKNOWN\"}";
        String outer = buildResponse(inner);
        assertThat(parser.matchResult(outer)).isEqualTo("UNRELATED");
    }

    @Test
    void emailBody_returnsRawContent() {
        String outer = buildResponse("Hello, your expense is approved.");
        assertThat(parser.emailBody(outer)).isEqualTo("Hello, your expense is approved.");
    }

    private String buildResponse(String content) {
        try {
            String escaped = new ObjectMapper().createObjectNode().put("v", content).get("v").toString();
            return "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":" + escaped + "}}]}";
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
