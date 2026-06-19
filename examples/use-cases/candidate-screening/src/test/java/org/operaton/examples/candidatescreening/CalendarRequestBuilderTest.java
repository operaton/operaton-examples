package org.operaton.examples.candidatescreening;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CalendarRequestBuilderTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void buildsFreeBusyQueryWithEmail() throws Exception {
        String json = new CalendarRequestBuilder(mapper).freeBusyRequest("rachel@example.com");
        JsonNode req = mapper.readTree(json);
        assertThat(req.get("calendarId").asText()).isEqualTo("rachel@example.com");
    }
}
