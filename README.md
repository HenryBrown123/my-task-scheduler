# my-task-scheduler

A lightweight job scheduler written in Java that runs shell commands and scripts on configurable schedules. Jobs are defined in YAML, persisted in SQLite, and can receive secrets from HashiCorp Vault via stdin.

Included in the repository is a demo job that can retrieve smtp credentials and email a macOs health report written in python.

## How it works

The scheduler polls on a configurable interval, checks which jobs are due based on their schedule and last execution time, and submits them to a thread pool. Each job runs as a subprocess with stdout and stderr captured to log files. If a job declares credential requirements, those are resolved from Vault, serialised as JSON, and piped to the process via stdin. No secrets are persisted or passed as arguments. Jobs that need credentials but don't have them available are skipped until the credentials are stored.

On startup, the YAML config is synced to the database. Jobs removed from config are deactivated. Jobs added or changed are upserted. The database is the source of truth for execution history and scheduling state; the YAML file is the source of truth for job definitions.

## Requirements

- Java 21+
- Maven
- SQLite (bundled via JDBC — no external install needed)
- HashiCorp Vault (optional - for credential management)

Python and bash must be available locally if needed for automation scripts.

## Vault setup

The scheduler uses Vault's secrets engine to store and retrieve credentials. Vault can run in two modes depending on your environment.

**Managed mode** (`vault.managed=true`): The application starts, initialises, and unseals its own Vault server process. Suitable for local development. Requires the `vault` binary on your PATH.

**External mode** (`vault.managed=false`): Connects to an already-running Vault instance. Set the `VAULT_TOKEN` environment variable for authentication. This is the expected setup for production or shared environments.

Store credentials using the Vault CLI:

```bash
export VAULT_ADDR=http://127.0.0.1:8200

vault kv put secret/my-scheduler/smtp - <<EOF
{
  "host": "smtp.gmail.com",
  "port": "587",
  "username": "you@gmail.com",
  "password": "your-app-password"
}
EOF
```

The scheduler scans all jobs at startup and reports any credentials that are required but missing.

## Configuration

All configuration lives in a single `application.properties` file. The application loads `application.default.properties` from the classpath, then overlays any file-based or system property overrides.

```properties
# Database
db.path=data/jobs.db
db.in.memory=false

# Scheduling
scheduling.logs.dir=data/logs
scheduling.pool.size=4
scheduling.poll.interval.ms=60000
scheduler.jobs=demo/jobs.yaml

# Vault
vault.address=http://127.0.0.1:8200
vault.secret.path=secret/my-scheduler
vault.managed=false
vault.data.dir=vault-data
vault.listener.address=127.0.0.1:8200
vault.tls.disable=true
vault.ui=true
vault.disable.mlock=true
```

## Job definition

Jobs are defined in YAML. Each job has metadata, a schedule, a command, and optional credential requirements.

```yaml
jobs:
  - meta:
      id: mac-report
      name: "Monthly Mac Usage Report"
      description: "Generates and emails a Mac usage report"
      priority: medium
      tags:
        - reporting
        - macos
    schedule:
      type: monthly
      day_of_month: 1
      time: "09:00"
    command:
      type: cmd
      command: "python3 demo/my_mac_app_usage.py"
      interpreter: bash
      credentials:
        - name: gmail
          type: SMTP
```

Schedule types include `simple` (interval-based like `5m` or `1h`), `monthly` (day of month + time), and `cron` (standard cron expressions).

When a job declares credentials, the sche
duler resolves them from Vault before execution and pipes them to the process as JSON on stdin:

```json
{
  "credentials": {
    "smtp": {
      "host": "smtp.gmail.com",
      "port": "587",
      "username": "you@gmail.com",
      "password": "your-app-password"
    }
  }
}
```

Scripts read this from stdin however they like. The demo Python job uses `json.load(sys.stdin)`.

A possible enhancement would be creating python libraries for accessing these credentials with a better api.
## Project structure

```
com.github.henrybrown123
├── configuration/       # AppConfig, AppProperties, JobConfigLoader
├── database/            # Database connection, SqliteDataType utility
├── execution/           # JobExecutor — runs subprocesses, pipes credentials
├── model/               # Domain objects — JobData, JobMeta, schedules, commands
├── repository/          # JobDataRepository (aggregate), sql/ package with DAOs
│   └── sql/             # JobDao, ScheduleDao, ExecutionDao, CredentialDao
├── scheduling/          # JobScheduler (thread pool), SchedulerService (poll loop)
├── security/            # VaultLifecycle, CredentialService, AppCredential, SecretString
└── Main.java
```

The architecture follows a feature slice approach, with data access via a repository and data access objects.

Credentials are wrapped in `SecretString` to prevent accidental logging. The `toString()` method returns `***` — values are only accessible via an explicit `expose()` call.

## Scheduling

A `SchedulerService` runs a tick loop on a configurable interval (default 60 seconds). Each tick loads the current config, syncs it to the database, queries all active jobs, and checks which are due based on their schedule and last execution timestamp. Jobs are scheduled with a delay to ensure they run as close to the expected time as possible.  E.g. the job is submitted when they are within 5 minutes of the next execution time with the calculated delay added.

Due jobs are submitted to a `ScheduledThreadPoolExecutor` with a configurable pool size (default 4 threads). Each job runs as a separate OS process via `ProcessBuilder`, with stdout and stderr redirected to timestamped log files. The executor records the start time, waits for the process to complete, captures the exit code, and writes the result back to the database.

This means jobs run concurrently up to the pool size limit. If more jobs are due than threads available, they queue and execute as threads free up. Long-running jobs don't block the tick loop... the scheduler submits and moves on. Execution state is tracked in SQLite, so if the application restarts, it knows what ran and when.

## Testing

```bash
mvn test
```

Tests are split into unit tests and integration tests. Unit tests use Mockito to isolate components. Integration tests use an in-memory SQLite database and exercise the full pipeline from YAML config through to job execution with credentials.

Security integration tests connect to a running Vault instance using a separate secret path (`secret/my-scheduler/test`) to isolate test data from development credentials. These tests are skipped automatically if Vault is not available. Test properties can also be changed to use a managed vs non-managed vault instance as with the application.

## Running (dev or local)

```bash
# start vault (dev mode)
vault server -dev

# in another terminal
export VAULT_TOKEN=<your-root-token>
export VAULT_ADDR=http://127.0.0.1:8200

# store credentials
vault kv put secret/my-scheduler/smtp - <<EOF
{"host":"smtp.gmail.com","port":"587","username":"you@gmail.com","password":"your-app-password"}
EOF

# build and run
mvn clean package
java -jar target/my-task-scheduler.jar demo/jobs.yaml
```