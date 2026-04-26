#!/usr/bin/env bash
# flaky_agent.sh — Orchestrates the flaky test investigation agent.
#
# Usage:
#   flaky_agent.sh --issue <github_issue_number>
#   flaky_agent.sh --csv <path_to_csv>
#   flaky_agent.sh --test <test_id> --language <lang>
#   flaky_agent.sh --from-db [--language <lang>] [--limit <N>]
#
# Options:
#   --issue <N>          GitHub issue number to investigate
#   --csv <path>         CSV file with flaky test entries
#   --test <id>          Single test ID (e.g. tests/async_tests/test_async_client.py::TestClass::test_method)
#   --from-db            Query postgres for top flaky tests
#   --language <lang>    Language filter (default: python)
#   --iterations <N>     Reproduction iterations (default: 1000)
#   --limit <N>          Max tests to investigate in --from-db mode (default: 5)
#   --build              Build the client before running (default: no build)
set -euo pipefail

REPO_ROOT="/home/ec2-user/valkey-glide"
AGENT_DIR="$REPO_ROOT/flaky/agent"
SCRIPTS_DIR="$REPO_ROOT/flaky/scripts"
RESULTS_DIR="$REPO_ROOT/flaky/results"

# Defaults
ISSUE=""
CSV_FILE=""
TEST_ID=""
LANGUAGE="python"
ITERATIONS=1000
FROM_DB=false
DB_LIMIT=5
BUILD=false

usage() {
  sed -n '2,/^set /{ /^#/s/^# \?//p }' "$0"
  exit 1
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --issue)      ISSUE="$2"; shift 2 ;;
    --csv)        CSV_FILE="$2"; shift 2 ;;
    --test)       TEST_ID="$2"; shift 2 ;;
    --from-db)    FROM_DB=true; shift ;;
    --language)   LANGUAGE="$2"; shift 2 ;;
    --iterations) ITERATIONS="$2"; shift 2 ;;
    --limit)      DB_LIMIT="$2"; shift 2 ;;
    --build)         BUILD=true; shift ;;
    -h|--help)    usage ;;
    *)            echo "Unknown option: $1"; usage ;;
  esac
done

# Validate input — exactly one of --issue, --csv, --test, or --from-db must be provided
INPUT_COUNT=0
[[ -n "$ISSUE" ]] && INPUT_COUNT=$((INPUT_COUNT + 1))
[[ -n "$CSV_FILE" ]] && INPUT_COUNT=$((INPUT_COUNT + 1))
[[ -n "$TEST_ID" ]] && INPUT_COUNT=$((INPUT_COUNT + 1))
[[ "$FROM_DB" == true ]] && INPUT_COUNT=$((INPUT_COUNT + 1))
if [[ $INPUT_COUNT -ne 1 ]]; then
  echo "Error: Provide exactly one of --issue, --csv, --test, or --from-db"
  usage
fi

# Determine a run ID for this investigation
if [[ -n "$ISSUE" ]]; then
  RUN_ID="issue-${ISSUE}"
elif [[ -n "$CSV_FILE" ]]; then
  RUN_ID="csv-$(date +%Y%m%d_%H%M%S)"
elif [[ "$FROM_DB" == true ]]; then
  RUN_ID="db-${LANGUAGE}-$(date +%Y%m%d_%H%M%S)"
else
  # Derive a short name from the test ID
  SHORT_TEST=$(echo "$TEST_ID" | sed 's|.*/||; s|\.py.*||')
  RUN_ID="test-${SHORT_TEST}-$(date +%s)"
fi

WORK_DIR="$REPO_ROOT"
BRANCH_NAME="flaky/$RUN_ID"

# --- Step 1: Checkout branch from alexl/flaky-agent ---
cd "$REPO_ROOT"
echo "==> Checking out branch $BRANCH_NAME from alexl/flaky-agent"
git checkout -B "$BRANCH_NAME" alexl/flaky-agent

# --- Step 2: Build (Python) ---
if [[ "$BUILD" == true && "$LANGUAGE" == "python" ]]; then
  echo "==> Building Python client..."
  cd "$WORK_DIR/python"
  python3 dev.py build --client all --mode release
fi

# --- Step 3: Build the agent prompt ---
build_agent_prompt() {
  local system_prompt
  system_prompt=$(cat "$AGENT_DIR/system_prompt.md")

  local task_block=""
  if [[ -n "$ISSUE" ]]; then
    task_block="Investigate flaky test from GitHub issue #${ISSUE} in the valkey-io/valkey-glide repository.

Steps:
1. Fetch the issue details using GitHub MCP (owner: valkey-io, repo: valkey-glide, issue: ${ISSUE})
2. Read the issue body and comments to understand the flaky behavior
3. Identify the test ID, language, and failure pattern
4. Read the test source code
5. Reproduce by running ${ITERATIONS} iterations using the scripts in flaky/scripts/
6. Analyze failures, identify root cause, propose fix(es)
7. Write findings to flaky/results/${RUN_ID}/findings.md
8. Commit, push to origin, and open a draft PR on Aryex/valkey-glide"

  elif [[ -n "$CSV_FILE" ]]; then
    local csv_content
    csv_content=$(cat "$CSV_FILE")
    task_block="Investigate all flaky tests listed in this CSV:

\`\`\`csv
${csv_content}
\`\`\`

For each entry, follow the full investigation workflow: understand, reproduce (${ITERATIONS} iterations), root-cause, fix, record.
Write findings for each test to flaky/results/<issue_id>/findings.md"

  elif [[ "$FROM_DB" == true ]]; then
    local db_results
    db_results=$(PGPASSWORD=ci_pass psql -h localhost -U ci_user -d ci_failures -t -A -F'|' -c "
      SELECT tf.test_suite, tf.test_name, tf.parameters, tf.failure_type, COUNT(*) as occurrences
      FROM test_failures tf JOIN jobs j ON tf.job_id = j.job_id
      WHERE j.language = '${LANGUAGE}' AND tf.test_suite != 'N/A' AND tf.failure_type NOT LIKE 'infra%'
      GROUP BY 1,2,3,4 ORDER BY 5 DESC LIMIT ${DB_LIMIT};
    " 2>/dev/null)
    task_block="Investigate the top ${DB_LIMIT} flaky ${LANGUAGE} tests from the CI failures database.

Database query results (test_suite|test_name|parameters|failure_type|occurrences):
\`\`\`
${db_results}
\`\`\`

Database connection: PGPASSWORD=ci_pass psql -h localhost -U ci_user -d ci_failures
Local CI logs: /home/ec2-user/valkey-glide-logs/
Parsed failures CSV: /home/ec2-user/valkey-glide-logs/failed-${LANGUAGE}-tests.csv

For each test above:
1. Query the DB for full failure history (platforms, engines, dates)
2. Read the raw CI logs from /home/ec2-user/valkey-glide-logs/runs/ for stack traces
3. Read the test source code
4. Reproduce with ${ITERATIONS} iterations using flaky/scripts/run_python_test.sh
5. Identify root cause and propose fix(es)
6. Write findings to flaky/results/${RUN_ID}/<test_name>/findings.md"

  elif [[ -n "$TEST_ID" ]]; then
    task_block="Investigate flaky test: ${TEST_ID} (language: ${LANGUAGE})

Steps:
1. Read the test source code and understand what it tests
2. Reproduce by running ${ITERATIONS} iterations:
   cd ${WORK_DIR}/python
   bash ${SCRIPTS_DIR}/run_python_test.sh \"${TEST_ID}\" ${ITERATIONS} ${RESULTS_DIR}/${RUN_ID}
3. Analyze any failures from the iteration logs
4. Identify root cause and propose fix(es)
5. Write findings to flaky/results/${RUN_ID}/findings.md"
  fi

  echo "Read the investigation workflow from ${AGENT_DIR}/system_prompt.md, then execute the following task:

${task_block}"
}

AGENT_PROMPT=$(build_agent_prompt)
OUT_DIR="$RESULTS_DIR/$RUN_ID"
mkdir -p "$OUT_DIR"

# Save the prompt for debugging/auditing
echo "$AGENT_PROMPT" > "$OUT_DIR/agent_prompt.txt"

echo ""
echo "============================================"
echo "Flaky Test Agent — $RUN_ID"
echo "Work dir:    $WORK_DIR"
echo "Results dir: $OUT_DIR"
echo "Iterations:  $ITERATIONS"
echo "============================================"
echo ""
echo "Agent prompt saved to $OUT_DIR/agent_prompt.txt"

echo "==> Launching agent..."
cd "$WORK_DIR"
exec kiro-cli chat --no-interactive --trust-all-tools --model claude-opus-4.6 "Read and follow the instructions in $OUT_DIR/agent_prompt.txt"
