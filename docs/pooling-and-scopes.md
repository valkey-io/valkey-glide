# Connection Pooling and Isolated Scopes

This document describes the client-instance pooling and isolated execution scope features available in Valkey GLIDE.

## Client-Instance Pool (`ClientPool`)

A pool of independent `GlideClient` instances that eliminates multiplexer contention under high concurrency. Each borrowed client has its own dedicated connection, so commands from different threads never share a pipeline.

### Key Properties

- **LIFO reuse**: Most recently returned client is borrowed next (warm caches, fewer stale connections)
- **Auto-reconnect**: Each pooled client is a full `GlideClient` with built-in reconnection. If a connection drops, the client transparently reconnects in the background with exponential backoff, restoring all state (AUTH, SELECT, CLIENT NAME, protocol, pub/sub subscriptions)
- **State reset on release**: When a client is returned to the pool, a batched DISCARD + SELECT pipeline resets connection state to the configured baseline before the next borrower receives it
- **Background creation**: When the pool is exhausted but below `max_size`, new clients are created in the background
- **Blocking acquire**: Configurable timeout for waiting on an available client
- **Leak detection**: Warning logged if pool is destroyed while clients are still borrowed

### State Reset on Release

When a borrowed client is returned to the pool:

1. **DISCARD** is sent (cancels any active MULTI/WATCH — safe even without MULTI)
2. **SELECT `<configured_db>`** is sent (resets to the pool's configured database)

This guarantees the next borrower gets a clean connection. If the reset fails (timeout or network error), the connection is discarded and a new one will be created on demand.

### Pool Limitations

- **Pub/Sub subscriptions**: Do not call `SUBSCRIBE`/`PSUBSCRIBE` on a pooled client. The pool's state reset does not send UNSUBSCRIBE, so the next borrower would receive a connection stuck in subscription mode. Use the main client's pubsub API instead, which manages dedicated subscription connections internally.
- **Configure-time pubsub**: Pool configs should not include pubsub subscriptions. Pooled clients are for stateless command execution (GET/SET/etc.), not long-lived subscription connections.

### Auto-Reconnection

Pooled clients inherit the full `GlideClient` reconnection stack:
- Disconnect detection via `DisconnectNotifier`
- Background reconnection with exponential backoff (infinite retries)
- State restoration on reconnect: AUTH, SELECT, CLIENT SETNAME, protocol version, pub/sub subscriptions
- IAM token refresh on reconnect (if IAM auth configured)

No user action is required — reconnection is transparent.

---

## Isolated Execution Scope (`IsolatedScope` / `scoped_connection()`)

A dedicated, non-multiplexed connection borrowed from a per-client scope pool. Provides exclusive connection access for operations requiring per-connection server state.

### Use Cases

- **WATCH/MULTI/EXEC** (optimistic concurrency control)
- **CLIENT TRACKING** (server-side key invalidation)
- **Blocking commands** (BLPOP, XREAD BLOCK)

### Current Limitations

- **Pub/Sub subscriptions** are not supported on scoped connections. SUBSCRIBE puts the connection into a push-message mode that requires a dedicated message handler — scoped connections don't wire one up. Use the main client's pubsub API instead (which manages its own dedicated subscription connections internally). This is consistent with how other Redis/Valkey clients handle pubsub (Lettuce, redis-py, node-redis all use separate connection types for subscriptions).

### State Inheritance

Scoped connections inherit the parent client's state at creation time:

| State | Inherited From |
|-------|---------------|
| Database (SELECT) | Parent's **current runtime** database (not just config) |
| Credentials (AUTH) | Connection request config |
| Client name | Connection request config |
| Compression | Parent's compression manager (applied at FFI layer) |
| Request timeout | Parent's configured timeout |
| Inflight limits | Counted against parent's inflight budget |

If the parent client calls `SELECT 5` at runtime, subsequently acquired scopes will be on database 5.

### State Cleanup on Release

When a scope is released back to the pool:

**Zero-cost path** (no state mutations detected):
- Connection returned immediately to idle — no round-trip

**Dirty state path** (WATCH, MULTI, SELECT, subscriptions, etc.):
- A single pipeline (one round-trip) cleans up all state:
  - `DISCARD` or `UNWATCH` (cancel transactions)
  - `UNSUBSCRIBE` / `PUNSUBSCRIBE` / `SUNSUBSCRIBE` (clear subscriptions)
  - `CLIENT TRACKING OFF` (disable tracking)
  - `SELECT <parent_db>` (reset database)
- On cleanup failure, the connection is discarded

### No Auto-Reconnection (By Design)

Scoped connections **do not** auto-reconnect. If the connection drops mid-scope, the command fails with an error and the scope becomes unusable.

**Why this is correct:**

A scope represents per-connection server state. If the connection drops:
- Active WATCH keys are invalidated
- Queued MULTI commands are lost
- CLIENT TRACKING subscriptions disappear
- Pinned cluster slot affinity is broken

Transparently reconnecting would create a fresh connection with none of this state, leading to silent correctness bugs (e.g., EXEC succeeding when it shouldn't, tracked invalidations lost).

**What to do:**

```python
# OCC retry loop — handles scope disconnection naturally
while not committed:
    with client.scoped_connection() as scope:
        try:
            scope.watch(key)
            val = scope.get(key)
            scope.multi()
            scope.set(key, str(int(val) + 1))
            result = scope.exec()
            if result is not None:
                committed = True
        except ConnectionError:
            # Scope is dead — loop will acquire a fresh one
            pass
```

When a scope fails, the broken connection is automatically discarded from the pool. The next `scoped_connection()` call returns a fresh, healthy connection.

### Functional Parity with Regular Commands

Scoped commands go through the same pipeline as regular GlideClient commands:

- **Compression**: Applied on write, decompressed on read
- **OpenTelemetry**: Span creation/completion per command
- **Inflight tracking**: Each scope command reserves an inflight slot on the parent
- **Timeout watchdog**: Registered for diagnostic logging on timeout
- **IAM token refresh**: Via parent client's token manager
- **Request timeout enforcement**: Via `send_command_on_connection`

---

## Configuration

### Pool Config

| Parameter | Description | Default |
|-----------|-------------|---------|
| `max_size` | Maximum clients in the pool | Required |
| `min_idle` | Pre-warmed idle clients at startup | 0 |
| `idle_timeout` | Evict idle clients after this duration | Required |
| `request_timeout` | Per-command timeout (also used for cleanup: 2×) | Required |
| `configured_database_id` | Database for SELECT reset on release | Parsed from connection request |

### Scope Pool Config

| Parameter | Description | Default |
|-----------|-------------|---------|
| `max_total` | Maximum concurrent scoped connections per client | 64 |
| `min_idle` | Pre-warmed idle connections (via `glide_scope_prewarm`) | 0 |
| `idle_timeout` | Evict stale idle connections | 5 minutes |
| `test_on_borrow` | PING before returning connection | false |
