package org.operaton.examples.candidatescreening;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResponseParserTest {
    private final ResponseParser parser = new ResponseParser(new ObjectMapper());

    private static String completion(String content) {
        // content is embedded as a JSON string value
        return "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":"
            + new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(content)
            + "}}]}";
    }

    @Test
    void parsesScoreAndReasoningFromJsonContent() {
        String resp = completion("{\"score\": 88, \"reasoning\": \"Excellent fit\"}");
        assertThat(parser.score(resp)).isEqualTo(88);
        assertThat(parser.reasoning(resp)).isEqualTo("Excellent fit");
    }

    @Test
    void contentReturnsAssistantMessageVerbatim() {
        String resp = completion("Dear Ada, we would like to invite you...");
        assertThat(parser.content(resp)).isEqualTo("Dear Ada, we would like to invite you...");
    }
}
