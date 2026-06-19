package org.operaton.examples.candidatescreening;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class SlotFinder {

    private final ObjectMapper mapper;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    public SlotFinder(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String firstFreeWorkingDaySlot(String response) {
        try {
            JsonNode slots = mapper.readTree(response).path("freeBusy");
            LocalDateTime best = null;
            for (JsonNode slot : slots) {
                if (slot.path("busy").asBoolean(true)) {
                    continue;
                }
                LocalDateTime start = LocalDateTime.parse(slot.path("start").asText());
                DayOfWeek day = start.getDayOfWeek();
                if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
                    continue;
                }
                if (best == null || start.isBefore(best)) {
                    best = start;
                }
            }
            if (best == null) {
                throw new IllegalStateException("No free working-day slot found in calendar response");
            }
            return best.format(FORMATTER);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse calendar response: " + response, e);
        }
    }
}
