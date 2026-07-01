package org.operaton.examples.expensereimbursement;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class LlmPropertiesTest {

    @Test
    void chatCompletionsUrl_appendsPath() {
        LlmProperties props = new LlmProperties();
        props.setBaseUrl("http://localhost:11434");
        assertThat(props.getChatCompletionsUrl()).isEqualTo("http://localhost:11434/v1/chat/completions");
    }

    @Test
    void authorizationHeader_usesBearerPrefix() {
        LlmProperties props = new LlmProperties();
        props.setApiKey("my-key");
        assertThat(props.getAuthorizationHeader()).isEqualTo("Bearer my-key");
    }
}
