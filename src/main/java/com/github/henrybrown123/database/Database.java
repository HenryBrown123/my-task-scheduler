package com.github.henrybrown123.database;

import java.sql.*;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class Database implements AutoCloseable {
    private static final String DB_PATH = "data/jobs.db";
    private Connection conn;

    public Database() throws SQLException {
        new java.io.File("data").mkdirs();
        conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
        //initSchema();
    }

    private void initSchema() throws SQLException {
        try {
            InputStream is = getClass().getClassLoader()
                    .getResourceAsStream("schema.sql");

            if (is == null) {
                throw new SQLException("schema.sql not found in resources");
            }

            String schema = new String(is.readAllBytes(), StandardCharsets.UTF_8);

            try (Statement stmt = conn.createStatement()) {
                for (String sql : schema.split(";")) {
                    if (!sql.trim().isEmpty()) {
                        stmt.execute(sql.trim());
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