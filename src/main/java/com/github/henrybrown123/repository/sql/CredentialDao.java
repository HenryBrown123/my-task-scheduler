package com.github.henrybrown123.repository.sql;

import com.github.henrybrown123.model.job.command.JobCredential;
import com.github.henrybrown123.repository.RepositoryException;
import com.github.henrybrown123.security.ESecretType;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data access for the job_credentials table.
 * Stores which credentials each job requires.
 */
public class CredentialDao {
    private final Connection conn;

    public CredentialDao(Connection conn) {
        this.conn = conn;
    }

    public void save(String jobId, JobCredential credential) {
        String sql = """
            INSERT INTO job_credentials (job_id, credential_name, credential_type)
            VALUES (?, ?, ?)
            ON CONFLICT(job_id, credential_name) DO UPDATE SET
                credential_type = excluded.credential_type
            """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, jobId);
            stmt.setString(2, credential.name());
            stmt.setString(3, credential.type().name());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException(
                    "Failed to save credential: " + credential.name() + " for job: " + jobId, e);
        }
    }

    public List<JobCredential> findByJobId(String jobId) {
        String sql = "SELECT * FROM job_credentials WHERE job_id = ?";
        List<JobCredential> credentials = new ArrayList<>();

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, jobId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                credentials.add(new JobCredential(
                        rs.getString("credential_name"),
                        ESecretType.valueOf(rs.getString("credential_type"))
                ));
            }
        } catch (SQLException e) {
            throw new RepositoryException("Failed to find credentials for job: " + jobId, e);
        }

        return credentials;
    }

    public void deleteByJobId(String jobId) {
        String sql = "DELETE FROM job_credentials WHERE job_id = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, jobId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("Failed to delete credentials for job: " + jobId, e);
        }
    }
}