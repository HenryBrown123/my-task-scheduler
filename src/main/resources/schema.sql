-- TODO: Basic schema for job tracking, better database constraints could be implemented to enforce data integratity.
CREATE TABLE IF NOT EXISTS config_files(
                                           id INTEGER PRIMARY KEY AUTOINCREMENT,
                                           file_path TEXT,
                                           status TEXT DEFAULT 'active',
                                           created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                           updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Job definitions table, loaded from config file
CREATE TABLE IF NOT EXISTS jobs (
                                    id TEXT PRIMARY KEY,
                                    name TEXT NOT NULL,
                                    description TEXT,
                                    priority TEXT,
                                    tags TEXT,
                                    schedule_type TEXT NOT NULL,
                                    command_type TEXT,
                                    command TEXT,
                                    interpreter TEXT,
                                    status TEXT DEFAULT 'active',
                                    start_date TEXT,
                                    end_date TEXT,
                                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Schedule tables (schedule-specific data only)
CREATE TABLE IF NOT EXISTS schedule_simple (
                                               job_id TEXT PRIMARY KEY,
                                               interval TEXT NOT NULL,
                                               FOREIGN KEY(job_id) REFERENCES jobs(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS schedule_monthly (
                                                job_id TEXT PRIMARY KEY,
                                                day_of_month INTEGER NOT NULL,
                                                time TEXT NOT NULL,
                                                months TEXT,
                                                FOREIGN KEY(job_id) REFERENCES jobs(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS schedule_weekly (
                                               job_id TEXT PRIMARY KEY,
                                               days TEXT NOT NULL,
                                               time TEXT NOT NULL,
                                               FOREIGN KEY(job_id) REFERENCES jobs(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS schedule_cron (
                                             job_id TEXT PRIMARY KEY,
                                             expression TEXT NOT NULL,
                                             timezone TEXT,
                                             FOREIGN KEY(job_id) REFERENCES jobs(id) ON DELETE CASCADE
);

-- Execution history
-- Execution logs
CREATE TABLE IF NOT EXISTS job_logs (
                                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                                        execution_id INTEGER NOT NULL,
                                        timestamp TIMESTAMP NOT NULL,
                                        line TEXT NOT NULL,
                                        FOREIGN KEY(execution_id) REFERENCES job_executions(id) ON DELETE CASCADE
);

-- Update job_executions table to include file paths
CREATE TABLE IF NOT EXISTS job_executions (
                                              id INTEGER PRIMARY KEY AUTOINCREMENT,
                                              job_id TEXT NOT NULL,
                                              start_time TIMESTAMP NOT NULL,
                                              end_time TIMESTAMP,
                                              status TEXT NOT NULL,
                                              exit_code INTEGER,
                                              duration_ms INTEGER,
                                              triggered_by TEXT,
                                              stdout_file TEXT,
                                              stderr_file TEXT,
                                              created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                              FOREIGN KEY(job_id) REFERENCES jobs(id)
);

-- job_logs table can be removed or kept for backward compatibility
-- If you want to remove it, just delete this section:
-- DROP TABLE IF EXISTS job_logs;

-- Indexes remain the same
CREATE INDEX IF NOT EXISTS idx_job_id ON job_executions(job_id);
CREATE INDEX IF NOT EXISTS idx_status ON job_executions(status);
CREATE INDEX IF NOT EXISTS idx_start_time ON job_executions(start_time DESC);
CREATE INDEX IF NOT EXISTS idx_job_start ON job_executions(job_id, start_time DESC);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_job_id ON job_executions(job_id);
CREATE INDEX IF NOT EXISTS idx_status ON job_executions(status);
CREATE INDEX IF NOT EXISTS idx_start_time ON job_executions(start_time DESC);
CREATE INDEX IF NOT EXISTS idx_job_start ON job_executions(job_id, start_time DESC);