#!/usr/bin/env bash
# Generate a large jobs.yaml for concurrency testing.
#
# Usage:
#   ./generate-jobs.sh 50 5       # 50 jobs, each sleeps 5 seconds
#   ./generate-jobs.sh 200 10     # 200 jobs, each sleeps 10 seconds

set -euo pipefail

NUM_JOBS=${1:?Usage: $0 <num_jobs> <sleep_seconds>}
SLEEP_SECS=${2:?Usage: $0 <num_jobs> <sleep_seconds>}
OUTPUT="loads_of_jobs.yaml"

echo "Generating ${NUM_JOBS} jobs (sleep ${SLEEP_SECS}s each) → ${OUTPUT}"

echo "jobs:" > "$OUTPUT"

for i in $(seq 1 "$NUM_JOBS"); do
  cat >> "$OUTPUT" <<EOF
  - meta:
      id: load-test-${i}
      name: "Load Test Job ${i}"
      description: "Sleeps ${SLEEP_SECS}s for concurrency testing"
      priority: medium
      tags:
        - load-test
    schedule:
      type: simple
      interval: 1m
      start_date: "2025-01-01"
    command:
      type: cmd
      command: "echo 'Job ${i} started' && sleep ${SLEEP_SECS} && echo 'Job ${i} done'"
      interpreter: bash
EOF
done

echo "Done — ${OUTPUT} ($(wc -l < "$OUTPUT") lines)"