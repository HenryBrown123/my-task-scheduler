package com.github.henrybrown123.repository;

import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class JobRepository {
    private final Connection conn;

    public JobRepository(Connection conn) {
        this.conn = conn;
    }

    public void save(JobRecord job) {
        String sql = """
            INSERT INTO jobs (id, name, description, priority, tags, 
                             schedule_type, command_type, command, interpreter, status,
                             start_date, end_date)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                name = excluded.name,
                description = excluded.description,
                priority = excluded.priority,
                tags = excluded.tags,
                schedule_type = excluded.schedule_type,
                command_type = excluded.command_type,
                command = excluded.command,
                interpreter = excluded.interpreter,
                status = excluded.status,
                start_date = excluded.start_date,
                end_date = excluded.end_date,
                updated_at = CURRENT_TIMESTAMP
            """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, job.id());
            stmt.setString(2, job.name());
            stmt.setString(3, job.description());
            stmt.setString(4, job.priority());
            stmt.setString(5, String.join(",", job.tags()));
            stmt.setString(6, job.scheduleType());
            stmt.setString(7, job.commandType());
            stmt.setString(8, job.command());
            stmt.setString(9, job.interpreter());
            stmt.setString(10, job.status());
            stmt.setString(11, job.startDate());
            stmt.setString(12, job.endDate());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("Failed to save job: " + job.id(), e);
        }
    }

    public Optional<JobRecord> findById(String jobId) {
        String sql = "SELECT * FROM jobs WHERE id = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, jobId);
            ResultSet rs = stmt.executeQuery();

            if (!rs.next()) {
                return Optional.empty();
            }

            return Optional.of(mapToJobRecord(rs));
        } catch (SQLException e) {
            throw new RepositoryException("Failed to find job: " + jobId, e);
        }
    }

    public List<JobRecord> findAll() {
        String sql = "SELECT * FROM jobs ORDER BY name";
        List<JobRecord> jobs = new ArrayList<>();

        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(sql);

            while (rs.next()) {
                jobs.add(mapToJobRecord(rs));
            }
        } catch (SQLException e) {
            throw new RepositoryException("Failed to fetch all jobs", e);
        }

        return jobs;
    }

    public List<JobRecord> findByStatus(String status) {
        String sql = "SELECT * FROM jobs WHERE status = ? ORDER BY name";
        List<JobRecord> jobs = new ArrayList<>();

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                jobs.add(mapToJobRecord(rs));
            }
        } catch (SQLException e) {
            throw new RepositoryException("Failed to find jobs by status: " + status, e);
        }

        return jobs;
    }

    public void delete(String jobId) {
        String sql = "DELETE FROM jobs WHERE id = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, jobId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("Failed to delete job: " + jobId, e);
        }
    }

    private JobRecord mapToJobRecord(ResultSet rs) throws SQLException {
        String tagsStr = rs.getString("tags");
        List<String> tags = tagsStr != null && !tagsStr.isEmpty()
                ? Arrays.asList(tagsStr.split(","))
                : List.of();

        return new JobRecord(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("priority"),
                tags,
                rs.getString("schedule_type"),
                rs.getString("command_type"),
                rs.getString("command"),
                rs.getString("interpreter"),
                rs.getString("status"),
                rs.getString("start_date"),
                rs.getString("end_date")
        );
    }

    public record JobRecord(
            String id,
            String name,
            String description,
            String priority,
            List<String> tags,
            String scheduleType,
            String commandType,
            String command,
            String interpreter,
            String status,
            String startDate,
            String endDate
    ) {}
}