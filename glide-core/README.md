# Rust core

## Tests

To run all tests:

```bash
cargo test
```

To run specific tests:

```bash
cargo test <pattern>
```

For example:

```bash
cargo test <module_name>          # Filter test(s) by module name
cargo test <test_name>            # Filter test(s) by function name
```

### IAM Authentication Tests

To run [IAM authentication tests](tests/test_client.rs) locally with mock credentials:

```bash
AWS_ACCESS_KEY_ID=test_access_key \
AWS_SECRET_ACCESS_KEY=test_secret_key \
AWS_SESSION_TOKEN=test_session_token \
cargo test test_iam_authentication
```

If any of these environment variables are not set, IAM authentication tests will be skipped.

**Note:** The credential values shown above (`test_access_key`, etc.) are arbitrary placeholder strings. The AWS SDK uses them to generate an authentication token, but the local test server doesn't validate the token. These tests verify that the IAM authentication flow works correctly (token generation, connection establishment, and token refresh), not that the credentials are valid.

### DNS Tests

To run [DNS tests](tests/test_dns.rs) locally:

1. Add the following entries to your hosts file:
   - Linux/macOS: `/etc/hosts`
   - Windows: `C:\Windows\System32\drivers\etc\hosts`

   ```text
   127.0.0.1 valkey.glide.test.tls.com
   127.0.0.1 valkey.glide.test.no_tls.com
   ::1 valkey.glide.test.tls.com
   ::1 valkey.glide.test.no_tls.com
   ```

2. Set the environment variable:

   ```bash
   export VALKEY_GLIDE_DNS_TESTS_ENABLED=1
   ```

If the environment variable is not set, DNS tests will be skipped.

## Timeout Watchdog Diagnostics

The timeout watchdog provides structured diagnostic information when command timeouts occur. It runs on a dedicated OS thread independent of the Tokio runtime, guaranteeing timeout delivery even under runtime starvation.

### Architecture

The watchdog is a **pure timer** — it signals "timeout fired" with a bare `()` via a oneshot channel. All diagnostic enrichment (cause classification, latency percentiles, inflight counts, RSS) happens on the consumer side (`send_command`) at fire time. This keeps the hot path minimal:

- **Register** (~100ns): `Instant::now()` + oneshot channel + mpsc send
- **Phase tracking** (zero-alloc): `AtomicU8` on `Cmd` set by routing layer after connection resolution
- **Fire path** (rare): builds `TimeoutEvent` with classified cause, p99 latency, inflight trend, and process RSS

No `Arc`, `Mutex`, or heap allocation on the per-command hot path beyond the oneshot channel.

### Enabling

Diagnostics are enabled automatically when a `request_timeout` is configured on the client. No additional configuration is needed. Timeout events are emitted via `log_warn` at the `"timeout_watchdog"` log target.

### Log Output

When a timeout fires, a structured log line is emitted:

```
Timeout: cmd=GET node=10.0.0.1:6379 cause=ServerUnresponsive phase=Sent
  elapsed=252ms configured=250ms pending=47
  inflight=850→920 BUILDING (backpressure increasing during timeout window)
  p99=15ms suggested_timeout=45ms rss=384MB
```

### Interpreting the Fields

| Field | Meaning |
|-------|---------|
| `cmd` | The Valkey command that timed out |
| `node` | The resolved target node address (or `"unknown"` if routing didn't complete) |
| `cause` | Classified root cause (see below) |
| `phase` | `Queued` = command never left the client; `Sent` = command was sent, awaiting response |
| `elapsed` | Actual wall-clock time since submission |
| `configured` | The timeout duration that was set |
| `pending` | Total commands registered with the watchdog at fire time |
| `inflight` | Format: `at_register→at_timeout`. Shows how backpressure changed during the timeout window |
| `p99` | Recent p99 latency for this client (from the last 4096 commands) |
| `suggested_timeout` | 3× observed p99, floored at the configured timeout |
| `rss` | Process resident set size in MB at fire time (Linux/macOS) |

### Timeout Causes

| Cause | Meaning | Typical Action |
|-------|---------|----------------|
| `ServerUnresponsive` | Command was sent but the server didn't respond | Check server health, network, slow queries |
| `ClientBackpressure` | Command never left the client (phase=Queued) | Tokio runtime is starved or connection pool is exhausted |
| `SystemOverload` | >100 commands pending across many nodes | Local resource exhaustion (CPU, memory, FDs) |

### Inflight Trend

The `inflight=X→Y` field shows the number of in-flight requests at command submission vs. at timeout fire:

- **BUILDING** (`Y > X + 10`): Backpressure is increasing — the system is falling behind. New commands are arriving faster than they complete.
- **DRAINING** (`X > Y + 10`): Backpressure is decreasing — the system is recovering. The timeout was likely caused by a transient spike.
- **STABLE** (within ±10): The system was already saturated when the command was submitted. The timeout reflects steady-state overload.

### OpenTelemetry

Timeout events increment the existing `glide.timeout_errors` OTel counter. The structured diagnostic fields are currently emitted via logging only. A future enhancement will attach timeout cause and node as OTel metric attributes for dashboard integration.

### Suggested Timeout

When `suggested_timeout` appears in the output, it indicates what the timeout *should* be based on recent latency observations (3× p99). If your configured timeout is significantly lower than the suggested value, you may be timing out commands that would have succeeded with a slightly longer deadline.

## Client-Wide Circuit Breaker

The circuit breaker detects sustained error rates across all connections and rejects requests at the FFI boundary before threads park. This prevents thread explosion under degraded conditions.

### Enabling

Disabled by default. Pass a `ClientCircuitBreakerConfiguration` to the client builder to enable. All fields have defaults and are optional.

### State Machine

```
Closed → Open → HalfOpen → Closed
                HalfOpen → Open (on probe failure)
```

- **Closed**: Normal operation. Errors tracked in a sliding window.
- **Open**: Requests rejected immediately. Transitions to HalfOpen after `open_timeout`.
- **HalfOpen**: All traffic allowed (optimistic). Closes after `consecutive_successes` successful commands. Reopens on any error.

### Configuration

| Parameter | Default | Description |
|-----------|---------|-------------|
| `window_size_ms` | 10000 | Sliding window duration for error rate calculation |
| `failure_rate_threshold` | 0.5 | Error rate (0.0-1.0) to trip the breaker |
| `min_errors` | 50 | Minimum errors before rate is evaluated |
| `open_timeout_ms` | 5000 | Time in Open state before allowing probes |
| `count_timeouts` | false | Whether timeouts count toward tripping |
| `consecutive_successes` | 3 | Successful probes needed before closing |

### Error Classification

Only transport-level errors count toward tripping: `IoError`, `FatalSendError`, `FatalReceiveError`, and connection drops. Server-side errors (WRONGTYPE, MOVED, etc.) do not count. Timeouts are opt-in via `count_timeouts`.

### Exception Type

When the breaker is open, requests are rejected with a dedicated exception type (`CircuitBreakerException` in Java, `CircuitBreakerError` in Python/Node/Go) so callers can distinguish CB rejections from server errors.

## Recommended VSCode extensions

[rust-analyzer](https://marketplace.visualstudio.com/items?itemName=rust-lang.rust-analyzer) - Rust language server.
[CodeLLDB](https://marketplace.visualstudio.com/items?itemName=vadimcn.vscode-lldb) - Debugger.
[Even Better TOML](https://marketplace.visualstudio.com/items?itemName=tamasfe.even-better-toml) - TOML language support.
