package org.operaton.examples.expensereimbursement;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class PromptBuilderTest {

    private PromptBuilder builder;
    private ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        LlmProperties props = new LlmProperties();
        props.setModel("llama3.2-vision");
        props.setBaseUrl("http://localhost:11434");
        props.setApiKey("ollama");
        builder = new PromptBuilder(props, mapper);
    }

    @Test
    void receiptAnalysisRequest_containsRequesterNameInUserText() throws Exception {
        String base64 = Base64.getEncoder().encodeToString("fake-image".getBytes());
        String json = builder.receiptAnalysisRequest(base64, "Alice Berger", 35.0, "MEALS");
        JsonNode root = mapper.readTree(json);
        assertThat(root.path("model").asText()).isEqualTo("llama3.2-vision");
        // user message content is an array (vision format)
        JsonNode userContent = root.path("messages").path(1).path("content");
        assertThat(userContent.isArray()).isTrue();
        boolean hasText = false;
        for (JsonNode el : userContent) {
            if ("text".equals(el.path("type").asText())) {
                assertThat(el.path("text").asText()).contains("Requester: Alice Berger");
                hasText = true;
            }
        }
        assertThat(hasText).isTrue();
    }

    @Test
    void approvalEmailRequest_systemMentionsApprovedForReimbursement() throws Exception {
        String json = builder.approvalEmailRequest("Alice Berger", 35.0, "MEALS", "Team lunch", "PAY-001");
        JsonNode root = mapper.readTree(json);
        String systemContent = root.path("messages").path(0).path("content").asText();
        assertThat(systemContent).contains("approved for reimbursement");
    }

    @Test
    void rejectionEmailRequest_systemMentionsRejected() throws Exception {
        String json = builder.rejectionEmailRequest("Bob Richter", 150.0, "TRAVEL", "Conference trip");
        JsonNode root = mapper.readTree(json);
        String systemContent = root.path("messages").path(0).path("content").asText();
        assertThat(systemContent).contains("rejected");
    }
}
