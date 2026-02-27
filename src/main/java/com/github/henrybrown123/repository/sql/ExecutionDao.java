package com.github.henrybrown123.repository.sql;

import com.github.henrybrown123.model.job.Status;
import com.github.henrybrown123.model.job.execution.JobExecutionData;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.github.henrybrown123.repository.RepositoryException;

/**
 * Data access for the job_executions table. Tracks execution history,
 * status, and log file locations.
 */
public class ExecutionDao {
    private final Connection conn;

    public ExecutionDao(Connection conn) {
        this.conn = conn;
    }

    public synchronized long createQueuedExecution(String jobId, String triggeredBy) {
        String sql = """
            INSERT INTO job_executions (job_id, start_time, status, triggered_by)
            VALUES (?, datetime('now'), 'queued', ?)
            """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, jobId);
            stmt.setString(2, triggeredBy);
            stmt.executeUpdate();

            try (Statement idStmt = conn.createStatement()) {
                ResultSet rs = idStmt.executeQuery("SELECT last_insert_rowid()");
                if (rs.next()) {
                    return rs.getLong(1);
                }
                throw new RepositoryException("Failed to get execution ID for job: " + jobId, null);
            }
        } catch (SQLException e) {
            throw new RepositoryException("Failed to queue execution for job: " + jobId, e);
        }
    }

    public synchronized void updateExecutionStatus(long executionId, String status) {
        String sql = """
            UPDATE job_executions
            SET status = ?, start_time = datetime('now')
            WHERE id = ?
            """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status);
            stmt.setLong(2, executionId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("Failed to update execution status: " + executionId, e);
        }
    }

    public synchronized void attachLogFiles(long executionId, String stdoutFile, String stderrFile) {
        String sql = """
            UPDATE job_executions
            SET stdout_file = ?, stderr_file = ?
            WHERE id = ?
            """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, stdoutFile);
            stmt.setString(2, stderrFile);
            stmt.setLong(3, executionId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("Failed to attach log files to execution: " + executionId, e);
        }
    }

    /**
     * Fetches the latest execution for every job in a single query.
     * Used by the tick to merge fresh execution state with cached config.
     */
    public synchronized Map<String, JobExecutionData> getAllLatestExecutions() {
        String sql = """
            SELECT e.id, e.job_id, e.start_time, e.end_time, e.status,
                   e.exit_code, e.stdout_file, e.stderr_file
            FROM job_executions e
            INNER JOIN (
                SELECT job_id, MAX(id) AS max_id
                FROM job_executions
                GROUP BY job_id
            ) latest ON e.id = latest.max_id
            """;

        Map<String, JobExecutionData> results = new HashMap<>();

        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                String jobId = rs.getString("job_id");
                results.put(jobId, new JobExecutionData(
                        rs.getLong("id"),
                        Status.fromString(rs.getString("status")),
                        SqliteDataType.toLocalDateTime(rs.getTimestamp("start_time")),
                        SqliteDataType.toLocalDateTime(rs.getTimestamp("end_time")),
                        rs.getString("status"),
                        rs.getString("stdout_file"),
                        rs.getString("stderr_file"),
                        null
                ));
            }
        } catch (SQLException e) {
            throw new RepositoryException("Failed to fetch latest executions", e);
        }

        return results;
    }

    public synchronized void setExecutionAsCompleted(long executionId, String status, int exitCode) {
        String sql = """
            UPDATE job_executions
            SET status = ?,
                end_time = datetime('now'),
                exit_code = ?,
                duration_ms = (julianday(datetime('now')) - julianday(start_time)) * 86400000
            WHERE id = ?
            """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status);
            stmt.setInt(2, exitCode);
            stmt.setLong(3, executionId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("Failed to complete execution: " + executionId, e);
        }
    }

    public synchronized Optional<JobExecutionData> getLastExecution(String jobId) {
        String sql = """
            SELECT id, start_time, end_time, status, exit_code, stdout_file, stderr_file
            FROM job_executions
            WHERE job_id = ?
            ORDER BY start_time DESC
            LIMIT 1
            """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, jobId);
            ResultSet rs = stmt.executeQuery();

            if (!rs.next()) {
                return Optional.empty();
            }

            return Optional.of(new JobExecutionData(
                    rs.getLong("id"),
                    Status.fromString(rs.getString("status")),
                    SqliteDataType.toLocalDateTime(rs.getTimestamp("start_time")),
                    SqliteDataType.toLocalDateTime(rs.getTimestamp("end_time")),
                    rs.getString("status"),
                    rs.getString("stdout_file"),
                    rs.getString("stderr_file"),
                    null
            ));
        } catch (SQLException e) {
            throw new RepositoryException("Failed to get last execution for job: " + jobId, e);
        }
    }

    public synchronized List<ExecutionRecord> getHistory(String jobId, int limit) {
        String sql = """
            SELECT *
            FROM job_executions
            WHERE job_id = ?
            ORDER BY start_time DESC
            LIMIT ?
            """;

        List<ExecutionRecord> executions = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, jobId);
            stmt.setInt(2, limit);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                executions.add(new ExecutionRecord(
                        rs.getLong("id"),
                        rs.getString("job_id"),
                        SqliteDataType.toLocalDateTime(rs.getTimestamp("start_time")),
                        SqliteDataType.toLocalDateTime(rs.getTimestamp("end_time")),
                        rs.getString("status"),
                        rs.getInt("exit_code"),
                        rs.getLong("duration_ms"),
                        rs.getString("triggered_by"),
                        rs.getString("stdout_file"),
                        rs.getString("stderr_file")
                ));
            }
        } catch (SQLException e) {
            throw new RepositoryException("Failed to get execution history for job: " + jobId, e);
        }

        return executions;
    }

    public synchronized Optional<String> getStdoutPath(long executionId) {
        return getFilePath(executionId, "stdout_file");
    }

    public synchronized Optional<String> getStderrPath(long executionId) {
        return getFilePath(executionId, "stderr_file");
    }

    private Optional<String> getFilePath(long executionId, String column) {
        String sql = "SELECT " + column + " FROM job_executions WHERE id = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, executionId);
            ResultSet rs = stmt.executeQuery();

            if (!rs.next()) {
                return Optional.empty();
            }

            return Optional.ofNullable(rs.getString(column));
        } catch (SQLException e) {
            throw new RepositoryException("Failed to get file path for execution: " + executionId, e);
        }
    }

    public record ExecutionRecord(
            long id,
            String jobId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String status,
            int exitCode,
            long durationMs,
            String triggeredBy,
            String stdoutFile,
            String stderrFile
    ) {}

    /**
     * Counts of active (non-terminal) executions, for monitoring.
     */
    public record ActiveCounts(long queued, long running) {}

    /**
     * Returns counts of queued and running executions in a single query.
     * Lightweight — used by the monitoring daemon every 30 seconds.
     */
    public synchronized ActiveCounts getActiveExecutionCounts() {
        String sql = """
            SELECT
                COALESCE(SUM(CASE WHEN status = 'queued' THEN 1 ELSE 0 END), 0) AS queued,
                COALESCE(SUM(CASE WHEN status = 'running' THEN 1 ELSE 0 END), 0) AS running
            FROM job_executions
            WHERE status IN ('queued', 'running')
            """;

        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(sql);
            if (rs.next()) {
                return new ActiveCounts(rs.getLong("queued"), rs.getLong("running"));
            }
            return new ActiveCounts(0, 0);
        } catch (SQLException e) {
            throw new RepositoryException("Failed to count active executions", e);
        }
    }
}