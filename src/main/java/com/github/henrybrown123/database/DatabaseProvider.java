package com.github.henrybrown123.database;

import com.github.henrybrown123.configuration.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Creates and holds the application database connection.
 * Constructed once at startup, passed into the persistence layer.
 */
public class DatabaseProvider implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(DatabaseProvider.class);

    private final Connection connection;

    public DatabaseProvider() {
        this.connection = createConnection();
        initSchema();
    }

    public Connection getConnection() {
        return connection;
    }

    @Override
    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to close database", e);
            }
        }
    }

    private Connection createConnection() {
        var dbConfig = AppConfig.db();
        String path = dbConfig.path();
        String url = (dbConfig.inMemory() || ":memory:".equals(path))
                ? "jdbc:sqlite::memory:"
                : "jdbc:sqlite:" + path;

        try {
            Connection conn = DriverManager.getConnection(url);
            conn.setAutoCommit(true);
            log.info("Database connected: {}", url);
            return conn;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to connect to database: " + url, e);
        }
    }

    private void initSchema() {
        try {
            InputStream is = getClass().getClassLoader()
                    .getResourceAsStream("schema.sql");

            if (is == null) {
                throw new RuntimeException("schema.sql not found in resources");
            }

            String schema = new String(is.readAllBytes(), StandardCharsets.UTF_8);

            for (String sql : schema.split(";")) {
                if (!sql.trim().isEmpty()) {
                    try (Statement stmt = connection.createStatement()) {
                        stmt.execute(sql.trim());
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialise schema", e);
        }
    }
}
