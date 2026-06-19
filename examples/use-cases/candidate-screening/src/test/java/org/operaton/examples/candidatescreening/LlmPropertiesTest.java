package org.operaton.examples.candidatescreening;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class LlmPropertiesTest {
    @Test
    void derivesUrlAndAuthHeader() {
        LlmProperties p = new LlmProperties();
        p.setBaseUrl("http://localhost:11434");
        p.setApiKey("secret");
        p.setModel("llama3.2");
        assertThat(p.getChatCompletionsUrl()).isEqualTo("http://localhost:11434/v1/chat/completions");
        assertThat(p.getAuthorizationHeader()).isEqualTo("Bearer secret");
    }
}
