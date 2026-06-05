# Cross-Client Compatibility Issues

Tracking items where test utilities, naming, or structure differ across language clients and should be aligned in future work.

## Test Utilities

### `waitFor` / `waitForCondition`

A generic polling utility that waits until a condition is met or times out.

| Language | Name | Signature | Defaults |
|----------|------|-----------|----------|
| Java | `waitForCondition` | `waitForCondition(Callable<Boolean> condition, String failure)` | timeout=10000ms, interval=100ms (hardcoded) |
| Node.js | `waitFor` | `waitFor(condition: () => Promise<boolean>, failure: string, timeout?: number, interval?: number)` | timeout=10000ms, interval=100ms |
| Python | — | Not yet implemented | — |
| Go | `waitFor` | `waitFor(condition func() bool, failure string)` | timeout=10s, interval=100ms (hardcoded) |

**Checklist:**
- [ ] All implementations use 10s timeout and 100ms interval
- [ ] All implementations require a `failure` string argument
- [ ] All implementations take a condition lambda/callable as the first argument
- [ ] Naming is consistent: `waitFor` (Node.js, Go), `wait_for` / `sync_wait_for` (Python), `waitForCondition` (Java — rename pending)

### `waitForSaveNotInProgress` / `isSaveInProgress`

Waits until no background RDB save or AOF rewrite is in progress. Checks `INFO persistence` for `rdb_bgsave_in_progress:1` and `aof_rewrite_in_progress:1`.

| Language | Name | Location |
|----------|------|----------|
| Java | `isSaveInProgress` (used with `waitForCondition`) | `TestUtilities.java` |
| Node.js | `waitForSaveNotInProgress` | `TestUtilities.ts` |
| Python | `wait_for_save_not_in_progress` / `sync_wait_for_save_not_in_progress` | `tests/utils/utils.py` |
| Go | `waitForSaveNotInProgress` | `glide_test_suite_test.go` |

**Checklist:**
- [ ] All implementations check ALL node responses (not just the first)
- [ ] All implementations check both `rdb_bgsave_in_progress:1` and `aof_rewrite_in_progress:1`
- [ ] All implementations delegate to the generic `waitFor` helper
- [ ] Java should extract a `waitForSaveNotInProgress` wrapper for consistency

## API Documentation

### Method descriptions should match across languages

The Java PR establishes canonical one-line descriptions for each command. All language clients should use identical descriptions.

| Command | Canonical Description | Source |
|---------|----------------------|--------|
| `save` | Synchronously saves the dataset to disk. | Java `ServerManagementCommands.java` |
| `bgsave` | Asynchronously saves the dataset to disk in the background. | Java `ServerManagementCommands.java` |
| `bgsaveSchedule` | Schedules a background save of the database. | Java `ServerManagementCommands.java` |
| `bgsaveCancel` | Aborts all in-progress and scheduled background saves. | Java `ServerManagementCommands.java` |
| `bgrewriteaof` | Initiates a background rewrite of the append-only file (AOF). | Java `ServerManagementCommands.java` |

### Return value descriptions should be simple and consistent

| Return case | Canonical description |
|-------------|----------------------|
| Commands returning `"OK"` | `"OK"` |
| Commands returning a status string | A non-empty status string. |

**Action items:**
- Audit Python, Node.js, and Go docstrings to ensure they use the canonical descriptions above
- Avoid verbose return descriptions (e.g., "A string containing the status of the background save operation") — use "A non-empty status string." consistently
- Go cluster methods should not use per-method return descriptions like "A ClusterValue[string] containing the status string from each node" — use "A non-empty status string." uniformly


## SAVE with Explicit Multi-Node Route

When SAVE is sent with an explicit multi-node route (e.g., `AllPrimaries`) via `executeCommandWithRoute`, the Rust core bypasses the AllSucceeded response policy and returns per-node responses as a Map. This differs from FlushAll/ConfigResetStat which have their response policies applied even with explicit routing.

**Current behavior:**
- `Save()` (no route) → core aggregates via AllSucceeded → returns "OK"
- `SaveWithOptions(route: nil)` → same as above
- `SaveWithOptions(route: AllPrimaries)` → core returns Map (no aggregation)

**Implication:** Go's `SaveWithOptions` should not be called with explicit multi-node routes. Tests use `Route: nil` to exercise the API path safely. This is consistent with `ConfigResetStatWithOptions` and `LastSaveWithOptions` tests.
