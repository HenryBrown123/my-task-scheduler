package com.github.henrybrown123.repository.sql;

import com.github.henrybrown123.model.job.Status;
import com.github.henrybrown123.model.job.execution.JobExecutionData;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.github.henrybrown123.repository.RepositoryException;

/**
 * Data access for the job_executions table. Tracks execution history,
 * status, and log file locations.
 */
public class ExecutionRepository {
    private final Connection conn;

    public ExecutionRepository(Connection conn) {
        this.conn = conn;
    }

    public synchronized long createExecutionRecord(String jobId, String triggeredBy, String stdoutFile, String stderrFile) {
        String sql = """
            INSERT INTO job_executions (job_id, start_time, status, triggered_by, stdout_file, stderr_file)
            VALUES (?, datetime('now'), 'running', ?, ?, ?)
            """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, jobId);
            stmt.setString(2, triggeredBy);
            stmt.setString(3, stdoutFile);
            stmt.setString(4, stderrFile);
            stmt.executeUpdate();

            try (Statement idStmt = conn.createStatement()) {
                ResultSet rs = idStmt.executeQuery("SELECT last_insert_rowid()");
                if (rs.next()) {
                    return rs.getLong(1);
                }
                throw new RepositoryException("Failed to get execution ID for job: " + jobId, null);
            }
        } catch (SQLException e) {
            throw new RepositoryException("Failed to start execution for job: " + jobId, e);
        }
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
}