package com.github.henrybrown123.shared.model.job.schedule;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class SimpleScheduleTest {

    @Test
    void shouldHandleInvalidInterval() {
        assertThrows(RuntimeException.class, () -> {
            new SimpleSchedule("invalid", LocalDate.now(), null);
        }, "Should throw exception for invalid interval format");

        assertThrows(RuntimeException.class, () -> {
            new SimpleSchedule("0m", LocalDate.now(), null);
        }, "Should throw exception for interval less than 1");
    }
}
