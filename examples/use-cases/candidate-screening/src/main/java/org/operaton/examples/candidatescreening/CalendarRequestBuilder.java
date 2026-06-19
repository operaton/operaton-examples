package org.operaton.examples.candidatescreening;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

@Component
public class CalendarRequestBuilder {

    private final ObjectMapper mapper;

    public CalendarRequestBuilder(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String freeBusyRequest(String recruiterEmail) {
        ObjectNode root = mapper.createObjectNode();
        root.put("calendarId", recruiterEmail);
        root.put("windowDays", 14);
        try {
            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build calendar request JSON", e);
        }
    }
}
