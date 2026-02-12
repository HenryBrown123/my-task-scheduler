package com.github.henrybrown123.repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ExecutionRepository {
    // todo: add to config file
    private static final String LOGS_DIR = "data/logs";
    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final Connection conn;

    public ExecutionRepository(Connection conn) {
        this.conn = conn;
        ensureLogsDirectory();
    }

    private void ensureLogsDirectory() {
        try {
            Files.createDirectories(Paths.get(LOGS_DIR));
        } catch (IOException e) {
            throw new RepositoryException("Failed to create logs directory", e);
        }
    }

    public ExecutionContext startExecution(String jobId, String triggeredBy) {
        String sql = """
            INSERT INTO job_executions (job_id, start_time, status, triggered_by, stdout_file, stderr_file)
            VALUES (?, datetime('now'), 'running', ?, ?, ?)
            """;

        String timestamp = LocalDateTime.now().format(FILE_DATE_FORMAT);
        String stdoutFile = String.format("%s/%s_%s_stdout.log", LOGS_DIR, jobId, timestamp);
        String stderrFile = String.format("%s/%s_%s_stderr.log", LOGS_DIR, jobId, timestamp);

        try {
            Files.createFile(Paths.get(stdoutFile));
            Files.createFile(Paths.get(stderrFile));
        } catch (IOException e) {
            throw new RepositoryException("Failed to create log files for job: " + jobId, e);
        }

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, jobId);
            stmt.setString(2, triggeredBy);
            stmt.setString(3, stdoutFile);
            stmt.setString(4, stderrFile);
            stmt.executeUpdate();

            try (Statement idStmt = conn.createStatement()) {
                ResultSet rs = idStmt.executeQuery("SELECT last_insert_rowid()");
                if (rs.next()) {
                    long executionId = rs.getLong(1);
                    return new ExecutionContext(executionId, stdoutFile, stderrFile);
                }
                throw new RepositoryException("Failed to get execution ID for job: " + jobId, null);
            }
        } catch (SQLException e) {
            throw new RepositoryException("Failed to start execution for job: " + jobId, e);
        }
    }

    public void completeExecution(long executionId, String status, int exitCode) {
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

    public Optional<ExecutionSummary> getLastExecution(String jobId) {
        String sql = """
            SELECT start_time, end_time, status, exit_code
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

            Timestamp startTs = rs.getTimestamp("start_time");
            Timestamp endTs = rs.getTimestamp("end_time");

            return Optional.of(new ExecutionSummary(
                    startTs != null ? startTs.toLocalDateTime() : null,
                    endTs != null ? endTs.toLocalDateTime() : null,
                    rs.getString("status"),
                    rs.getInt("exit_code")
            ));
        } catch (SQLException e) {
            throw new RepositoryException("Failed to get last execution for job: " + jobId, e);
        }
    }

    public List<ExecutionRecord> getHistory(String jobId, int limit) {
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
                        rs.getTimestamp("start_time").toLocalDateTime(),
                        rs.getTimestamp("end_time") != null ? rs.getTimestamp("end_time").toLocalDateTime() : null,
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

    public Optional<String> getStdoutPath(long executionId) {
        return getFilePath(executionId, "stdout_file");
    }

    public Optional<String> getStderrPath(long executionId) {
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

    // Records
    public record ExecutionContext(
            long executionId,
            String stdoutFile,
            String stderrFile
    ) {}

    public record ExecutionSummary(
            LocalDateTime lastExecution,
            LocalDateTime endTime,
            String status,
            int exitCode
    ) {}

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