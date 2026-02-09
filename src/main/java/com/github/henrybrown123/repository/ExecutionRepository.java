package com.github.henrybrown123.repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class ExecutionRepository {
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
            throw new RuntimeException("Failed to create logs directory", e);
        }
    }

    public ExecutionContext startExecution(String jobId, String triggeredBy) throws SQLException, IOException {
        String sql = """
            INSERT INTO job_executions (job_id, start_time, status, triggered_by, stdout_file, stderr_file)
            VALUES (?, datetime('now'), 'running', ?, ?, ?)
            """;

        // Create log file paths
        String timestamp = LocalDateTime.now().format(FILE_DATE_FORMAT);
        String stdoutFile = String.format("%s/%s_%s_stdout.log", LOGS_DIR, jobId, timestamp);
        String stderrFile = String.format("%s/%s_%s_stderr.log", LOGS_DIR, jobId, timestamp);

        // Create the files
        Files.createFile(Paths.get(stdoutFile));
        Files.createFile(Paths.get(stderrFile));

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
                throw new SQLException("Failed to get execution ID");
            }
        }
    }

    public void completeExecution(long executionId, String status, int exitCode) throws SQLException {
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
        }

    }

    public ExecutionSummary getLastExecution(String jobId) throws SQLException {
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

            if (rs.next()) {
                Timestamp startTs = rs.getTimestamp("start_time");
                Timestamp endTs = rs.getTimestamp("end_time");

                return new ExecutionSummary(
                        startTs != null ? startTs.toLocalDateTime() : null,
                        endTs != null ? endTs.toLocalDateTime() : null,
                        rs.getString("status"),
                        rs.getInt("exit_code")
                );
            }
        }

        return null;
    }

    public List<ExecutionRecord> getHistory(String jobId, int limit) throws SQLException {
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
        }

        return executions;
    }

    public String getStdoutPath(long executionId) throws SQLException {
        return getFilePath(executionId, "stdout_file");
    }

    public String getStderrPath(long executionId) throws SQLException {
        return getFilePath(executionId, "stderr_file");
    }

    private String getFilePath(long executionId, String column) throws SQLException {
        String sql = "SELECT " + column + " FROM job_executions WHERE id = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, executionId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getString(column);
            }
        }

        return null;
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