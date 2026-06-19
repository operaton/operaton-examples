package org.operaton.examples.candidatescreening;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PromptBuilderTest {
    private final ObjectMapper mapper = new ObjectMapper();

    private PromptBuilder newBuilder() {
        LlmProperties p = new LlmProperties();
        p.setModel("llama3.2");
        return new PromptBuilder(p, mapper);
    }

    @Test
    void scoreRequestIsJsonModeWithMarkerAndName() throws Exception {
        JsonNode req = mapper.readTree(
            newBuilder().scoreRequest("Ada Lindqvist", "Senior Java Engineer", "10 years Spring"));
        assertThat(req.get("model").asText()).isEqualTo("llama3.2");
        assertThat(req.get("response_format").get("type").asText()).isEqualTo("json_object");
        String system = req.get("messages").get(0).get("content").asText();
        String user = req.get("messages").get(1).get("content").asText();
        assertThat(system).contains("Score the candidate");
        assertThat(user).contains("Ada Lindqvist").contains("Senior Java Engineer").contains("10 years Spring");
    }

    @Test
    void invitationRequestContainsMarkerAndSlot() throws Exception {
        JsonNode req = mapper.readTree(
            newBuilder().invitationRequest("Ada Lindqvist", "Senior Java Engineer", "2026-06-22T10:00:00"));
        assertThat(req.get("messages").get(0).get("content").asText()).contains("interview invitation email");
        assertThat(req.get("messages").get(1).get("content").asText()).contains("2026-06-22T10:00:00");
    }

    @Test
    void rejectionRequestContainsMarker() throws Exception {
        JsonNode req = mapper.readTree(
            newBuilder().rejectionRequest("Wes Park", "Senior Java Engineer"));
        assertThat(req.get("messages").get(0).get("content").asText()).contains("rejection email");
    }

    @Test
    void summaryRequestContainsMarkerScoreAndAssessment() throws Exception {
        JsonNode req = mapper.readTree(
            newBuilder().recruiterSummaryRequest("Ada Lindqvist", "Senior Java Engineer", 88, "Strong fit"));
        assertThat(req.get("messages").get(0).get("content").asText()).contains("internal summary email");
        String user = req.get("messages").get(1).get("content").asText();
        assertThat(user).contains("88").contains("Strong fit");
    }
}
