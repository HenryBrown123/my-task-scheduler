package com.github.henrybrown123.model.job.schedule;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class CronScheduleTest {

    @Test
    void shouldReturnNextExecutionDate() {
        CronSchedule schedule = new CronSchedule("0 0 * * *", null, null);

        LocalDateTime next = schedule.getNextExecutionDate(null);

        assertNotNull(next);
    }

}
