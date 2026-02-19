package com.github.henrybrown123.repository.sql;

import com.github.henrybrown123.model.job.schedule.*;
import com.github.henrybrown123.repository.RepositoryException;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

/**
 * Data access for schedule tables. Each schedule type has its own table
 * with type-specific fields.
 */
public class ScheduleDao {
    private final Connection conn;

    public ScheduleDao(Connection conn) {
        this.conn = conn;
    }

    public void save(String jobId, IJobScheduleData schedule) {
        delete(jobId);

        switch (schedule) {
            case SimpleSchedule s -> {
                String sql = "INSERT INTO schedule_simple (job_id, interval) VALUES (?, ?)";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, jobId);
                    stmt.setString(2, s.interval());
                    stmt.executeUpdate();
                } catch (SQLException e) {
                    throw new RepositoryException("Failed to save simple schedule for job: " + jobId, e);
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
                } catch (SQLException e) {
                    throw new RepositoryException("Failed to save monthly schedule for job: " + jobId, e);
                }
            }
            case CronSchedule c -> {
                String sql = "INSERT INTO schedule_cron (job_id, expression, timezone) VALUES (?, ?, ?)";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, jobId);
                    stmt.setString(2, c.expression());
                    stmt.setString(3, null);
                    stmt.executeUpdate();
                } catch (SQLException e) {
                    throw new RepositoryException("Failed to save cron schedule for job: " + jobId, e);
                }
            }
        }
    }

    public Optional<IJobScheduleData> findByJobId(String jobId, String scheduleType, LocalDate startDate, LocalDate endDate) {
        return switch (scheduleType) {
            case "simple" -> findSimpleSchedule(jobId, startDate, endDate).map(s -> s);
            case "monthly" -> findMonthlySchedule(jobId, startDate, endDate).map(s -> s);
            case "cron" -> findCronSchedule(jobId, startDate, endDate).map(s -> s);
            default -> throw new RepositoryException(
                    "Invalid schedule type: " + scheduleType, new IllegalArgumentException());
        };
    }

    public void delete(String jobId) {
        String[] tables = {"schedule_simple", "schedule_monthly", "schedule_weekly", "schedule_cron"};
        for (String table : tables) {
            String sql = "DELETE FROM " + table + " WHERE job_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, jobId);
                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new RepositoryException("Failed to delete schedule from " + table + " for job: " + jobId, e);
            }
        }
    }

    private Optional<SimpleSchedule> findSimpleSchedule(String jobId, LocalDate startDate, LocalDate endDate) {
        String sql = "SELECT * FROM schedule_simple WHERE job_id = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, jobId);
            ResultSet rs = stmt.executeQuery();

            if (!rs.next()) {
                return Optional.empty();
            }

            return Optional.of(new SimpleSchedule(
                    rs.getString("interval"),
                    startDate,
                    endDate
            ));
        } catch (SQLException e) {
            throw new RepositoryException("Failed to find simple schedule for job: " + jobId, e);
        }
    }

    private Optional<MonthlySchedule> findMonthlySchedule(String jobId, LocalDate startDate, LocalDate endDate) {
        String sql = "SELECT * FROM schedule_monthly WHERE job_id = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, jobId);
            ResultSet rs = stmt.executeQuery();

            if (!rs.next()) {
                return Optional.empty();
            }

            return Optional.of(new MonthlySchedule(
                    rs.getInt("day_of_month"),
                    LocalTime.parse(rs.getString("time")),
                    startDate,
                    endDate
            ));
        } catch (SQLException e) {
            throw new RepositoryException("Failed to find monthly schedule for job: " + jobId, e);
        }
    }

    private Optional<CronSchedule> findCronSchedule(String jobId, LocalDate startDate, LocalDate endDate) {
        String sql = "SELECT * FROM schedule_cron WHERE job_id = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, jobId);
            ResultSet rs = stmt.executeQuery();

            if (!rs.next()) {
                return Optional.empty();
            }

            return Optional.of(new CronSchedule(
                    rs.getString("expression"),
                    startDate,
                    endDate
            ));
        } catch (SQLException e) {
            throw new RepositoryException("Failed to find cron schedule for job: " + jobId, e);
        }
    }
}