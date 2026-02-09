package com.github.henrybrown123.repository;

import com.github.henrybrown123.model.job.schedule.*;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class ScheduleRepository {
    private final Connection conn;

    public ScheduleRepository(Connection conn) {
        this.conn = conn;
    }

    public void save(String jobId, IJobScheduleData schedule) throws SQLException {
        delete(jobId);

        switch (schedule) {
            case SimpleSchedule s -> {
                String sql = "INSERT INTO schedule_simple (job_id, interval) VALUES (?, ?)";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, jobId);
                    stmt.setString(2, s.interval());
                    stmt.executeUpdate();
                }
            }
            case MonthlySchedule m -> {
                String sql = "INSERT INTO schedule_monthly (job_id, day_of_month, time, months) VALUES (?, ?, ?, ?)";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, jobId);
                    stmt.setInt(2, m.dayOfMonth());
                    stmt.setString(3, m.time().toString());
                    stmt.setString(4, null);
                    stmt.executeUpdate();
                }
            }
            case CronSchedule c -> {
                String sql = "INSERT INTO schedule_cron (job_id, expression, timezone) VALUES (?, ?, ?)";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, jobId);
                    stmt.setString(2, c.expression());
                    stmt.setString(3, null);
                    stmt.executeUpdate();
                }
            }
        }
    }

    public IJobScheduleData findByJobId(String jobId, String scheduleType, String startDate, String endDate) throws SQLException {
        return switch(scheduleType) {
            case "simple" -> findSimpleSchedule(jobId, startDate, endDate);
            case "monthly" -> findMonthlySchedule(jobId, startDate, endDate);
            case "cron" -> findCronSchedule(jobId, startDate, endDate);
            default -> throw new IllegalArgumentException("Unknown schedule type: " + scheduleType);
        };
    }

    public void delete(String jobId) throws SQLException {
        String[] tables = {"schedule_simple", "schedule_monthly", "schedule_weekly", "schedule_cron"};
        for (String table : tables) {
            String sql = "DELETE FROM " + table + " WHERE job_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, jobId);
                stmt.executeUpdate();
            }
        }
    }

    private SimpleSchedule findSimpleSchedule(String jobId, String startDate, String endDate) throws SQLException {
        String sql = "SELECT * FROM schedule_simple WHERE job_id = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, jobId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return new SimpleSchedule(
                        rs.getString("interval"),
                        startDate != null ? LocalDate.parse(startDate) : null,
                        endDate != null ? LocalDate.parse(endDate) : null
                );
            }
        }

        throw new SQLException("Simple schedule not found for job: " + jobId);
    }

    private MonthlySchedule findMonthlySchedule(String jobId, String startDate, String endDate) throws SQLException {
        String sql = "SELECT * FROM schedule_monthly WHERE job_id = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, jobId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return new MonthlySchedule(
                        rs.getInt("day_of_month"),
                        LocalTime.parse(rs.getString("time")),
                        startDate != null ? LocalDate.parse(startDate) : null,
                        endDate != null ? LocalDate.parse(endDate) : null
                );
            }
        }

        throw new SQLException("Monthly schedule not found for job: " + jobId);
    }

    private CronSchedule findCronSchedule(String jobId, String startDate, String endDate) throws SQLException {
        String sql = "SELECT * FROM schedule_cron WHERE job_id = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, jobId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return new CronSchedule(
                        rs.getString("expression"),
                        startDate != null ? LocalDate.parse(startDate) : null,
                        endDate != null ? LocalDate.parse(endDate) : null
                );
            }
        }

        throw new SQLException("Cron schedule not found for job: " + jobId);
    }
}