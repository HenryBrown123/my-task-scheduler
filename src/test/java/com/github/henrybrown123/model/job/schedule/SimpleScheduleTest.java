package com.github.henrybrown123.model.job.schedule;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class SimpleScheduleTest {

    @Test
    void shouldRejectInvalidIntervalFormat() {
        assertThrows(RuntimeException.class, () ->
            new SimpleSchedule("invalid", null, null)
        );
    }

    @Test
    void shouldReturnNextTimeBasedOnLastExecution() {
        SimpleSchedule schedule = new SimpleSchedule("5m", null, null);
        LocalDateTime lastExecution = LocalDateTime.now().minusMinutes(10);

        LocalDateTime next = schedule.getNextExecutionDate(lastExecution);

        assertNotNull(next);
        assertTrue(next.isAfter(lastExecution));
    }

    @Test
    void shouldReturnTypeAsSimple() {
        SimpleSchedule schedule = new SimpleSchedule("1h", null, null);
        assertEquals("simple", schedule.type());
    }

    @Test
    void shouldStoreStartAndEndDates() {
        LocalDate start = LocalDate.of(2025, 1, 1);
        LocalDate end = LocalDate.of(2025, 12, 31);
        SimpleSchedule schedule = new SimpleSchedule("1h", start, end);

        assertEquals(start, schedule.startDate());
        assertEquals(end, schedule.endDate());
    }
}
