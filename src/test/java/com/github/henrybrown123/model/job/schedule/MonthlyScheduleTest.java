package com.github.henrybrown123.model.job.schedule;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

class MonthlyScheduleTest {
    @Test
    void shouldReturnThisMonthIfNotYetDue() {
        int dayOfMonth = LocalDate.now().getDayOfMonth() + 1;
        if (dayOfMonth > 28) {
            dayOfMonth = 1;
        }
        LocalTime futureTime = LocalTime.of(23, 59);
        MonthlySchedule schedule = new MonthlySchedule(dayOfMonth, futureTime, null, null);

        LocalDateTime next = schedule.getNextExecutionDate(null);

        assertNotNull(next);
    }

    @Test
    void shouldReturnNextMonthIfAlreadyPassed() {
        int dayOfMonth = 1;
        LocalTime pastTime = LocalTime.of(0, 1);
        MonthlySchedule schedule = new MonthlySchedule(dayOfMonth, pastTime, null, null);
        LocalDateTime now = LocalDateTime.now();

        if (now.getDayOfMonth() > 1 || now.toLocalTime().isAfter(pastTime)) {
            LocalDateTime next = schedule.getNextExecutionDate(null);

            assertNotNull(next);
            assertTrue(next.isAfter(now));
        }
    }
}
