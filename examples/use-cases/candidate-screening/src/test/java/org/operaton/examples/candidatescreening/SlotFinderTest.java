package org.operaton.examples.candidatescreening;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlotFinderTest {
    private final SlotFinder finder = new SlotFinder(new ObjectMapper());

    @Test
    void picksEarliestFreeWeekdaySlot() {
        // 2026-06-21 is a Sunday (must be skipped); 2026-06-22 is a Monday.
        String resp = "{\"freeBusy\":["
            + "{\"start\":\"2026-06-22T09:00:00\",\"end\":\"2026-06-22T09:30:00\",\"busy\":true},"
            + "{\"start\":\"2026-06-21T10:00:00\",\"end\":\"2026-06-21T10:30:00\",\"busy\":false},"
            + "{\"start\":\"2026-06-22T10:00:00\",\"end\":\"2026-06-22T10:30:00\",\"busy\":false}"
            + "]}";
        assertThat(finder.firstFreeWorkingDaySlot(resp)).isEqualTo("2026-06-22T10:00:00");
    }
}
