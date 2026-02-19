package com.github.henrybrown123.database;

import com.github.henrybrown123.configuration.AppConfig;

import java.sql.*;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Simple database connection wrapper with sqlite database creation on startup if not already present at the configured
 * location.
 *
 */
public class Database implements AutoCloseable {
    private static final String DB_PATH = AppConfig.db().path();
    private final Connection conn;

    public Database() throws SQLException {
        new java.io.File("data");
        this.conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
        initSchema();
    }

    private void initSchema() throws SQLException {
        try {
            InputStream is = getClass().getClassLoader()
                    .getResourceAsStream("schema.sql");

            if (is == null) {
                throw new SQLException("schema.sql not found in resources");
            }

            String schema = new String(is.readAllBytes(), StandardCharsets.UTF_8);

            for (String sql : schema.split(";")) {
                if (!sql.trim().isEmpty()) {
                    try (Statement stmt = conn.createStatement()) {
                        stmt.execute(sql.trim());
                    } catch (SQLException e) {
                     throw new RuntimeException("Failed to initialize schema database executing " + sql.trim(), e);
                }
            }
        }
        } catch (Exception e) {
            throw new SQLException("Failed to initialize schema", e);
        }
    }

    public Connection getConnection() {
        return conn;
    }

    @Override
    public void close() throws SQLException {
        if (conn != null && !conn.isClosed()) {
            conn.close();
        }
    }
}