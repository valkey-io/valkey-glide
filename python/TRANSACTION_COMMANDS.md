# Redis Transaction Commands in Valkey GLIDE Python Client

This document describes the newly added support for individual Redis transaction commands (`MULTI`, `EXEC`, `DISCARD`) in the Valkey GLIDE Python client.

## Overview

The Valkey GLIDE Python client now supports both approaches for Redis transactions:

1. **Batch System** (existing): Use `Batch(is_atomic=True)` for high-level transaction management
2. **Individual Commands** (new): Use `MULTI`, `EXEC`, and `DISCARD` for fine-grained transaction control

## New Commands

### `multi()`

Marks the start of a transaction block. Subsequent commands will be queued for atomic execution using `EXEC`.

**Signature:**
```python
# Async
async def multi(self) -> TOK

# Sync  
def multi(self) -> TOK
```

**Returns:** `"OK"` on success

**Example:**
```python
await client.multi()  # Start transaction
# Commands are now queued...
```

### `exec()`

Executes all previously queued commands in a transaction and restores the connection state to normal. When using `WATCH`, `EXEC` will execute commands only if the watched keys were not modified, otherwise returns `None`.

**Signature:**
```python
# Async
async def exec(self) -> Optional[List[TResult]]

# Sync
def exec(self) -> Optional[List[TResult]]
```

**Returns:** 
- `List[TResult]`: Array of results from executed commands
- `None`: If transaction was aborted due to `WATCH` condition

**Example:**
```python
results = await client.exec()  # Execute queued commands
if results is None:
    print("Transaction aborted due to WATCH condition")
else:
    print(f"Transaction results: {results}")
```

### `discard()`

Flushes all previously queued commands in a transaction and restores the connection state to normal.

**Signature:**
```python
# Async
async def discard(self) -> TOK

# Sync
def discard(self) -> TOK
```

**Returns:** `"OK"` on success

**Example:**
```python
await client.discard()  # Cancel transaction
```

## Redis Server Response Patterns

The implementation follows the exact Redis server response patterns:

### Command Responses

| Command | Context | Response | Type |
|---------|---------|----------|------|
| `MULTI` | Always | `"OK"` | Simple string |
| `DISCARD` | Always | `"OK"` | Simple string |
| `EXEC` | Success | `[result1, result2, ...]` | Array of command results |
| `EXEC` | WATCH abort | `None` | Null reply |
| Any command | After MULTI | `"QUEUED"` | Simple string |

### Response Type Details

- **Simple string responses**: `"OK"`, `"QUEUED"` (not bytes)
- **Array responses**: List containing individual command results
- **Null responses**: `None` when transaction is aborted by WATCH
- **Command results**: Each command's result in its normal format (e.g., `b"value"` for GET, `"OK"` for SET)

### Error Conditions

| Scenario | Error Message |
|----------|---------------|
| Nested MULTI | `"MULTI calls can not be nested"` |
| EXEC without MULTI | `"EXEC without MULTI"` |
| DISCARD without MULTI | `"DISCARD without MULTI"` |

## Usage Patterns

### Basic Transaction

```python
# Start transaction
await client.multi()

# Queue commands (each returns "QUEUED")
await client.set("key1", "value1")
await client.set("key2", "value2") 
await client.get("key1")

# Execute all commands atomically
results = await client.exec()
# results = ['OK', 'OK', b'value1']
```

### Transaction with WATCH

```python
# Watch a key for changes
await client.watch(["important_key"])

# Start transaction
await client.multi()
await client.set("important_key", "new_value")

# Execute - returns None if important_key was modified externally
result = await client.exec()
if result is None:
    print("Transaction aborted - key was modified")
```

### Discarding a Transaction

```python
# Start transaction
await client.multi()
await client.set("key", "value")

# Change your mind - discard the transaction
await client.discard()

# Key was not modified
assert await client.get("key") is None
```

### Error Handling

```python
await client.set("string_key", "not_a_number")

await client.multi()
await client.set("string_key", "new_value")  # Will succeed
await client.incr("string_key")              # Will fail
await client.get("string_key")               # Will succeed

results = await client.exec()
# results = ['OK', RequestError(...), b'new_value']
```

## Comparison with Batch System

### Individual Commands Approach
```python
await client.multi()
await client.set("key", "value")
await client.get("key")
results = await client.exec()
```

### Batch System Approach
```python
batch = Batch(is_atomic=True)
batch.set("key", "value")
batch.get("key")
results = await client.exec(batch, raise_on_error=True)
```

Both approaches achieve the same result. Choose based on your needs:

- **Individual Commands**: Better for dynamic transactions, conditional logic, or when you need fine-grained control
- **Batch System**: Better for static transactions, bulk operations, or when you want higher-level abstractions

## Error Conditions

### MULTI Errors
- `RequestError`: "MULTI calls can not be nested" - Called `MULTI` inside an existing transaction

### EXEC Errors  
- `RequestError`: "EXEC without MULTI" - Called `EXEC` without starting a transaction

### DISCARD Errors
- `RequestError`: "DISCARD without MULTI" - Called `DISCARD` without starting a transaction

## Cluster Mode Considerations

In cluster mode, all keys in a transaction must map to the same hash slot. This applies to both individual commands and batch transactions.

```python
# ✅ Good - same slot
await client.multi()
await client.set("{user:123}:name", "John")
await client.set("{user:123}:email", "john@example.com")
await client.exec()

# ❌ Bad - different slots (will fail)
await client.multi()
await client.set("key1", "value1")  # Different slot
await client.set("key2", "value2")  # Different slot  
await client.exec()  # RequestError: CrossSlot
```

## Testing

Comprehensive tests have been added for all transaction commands:

- `test_multi_exec_discard`: Basic transaction functionality
- `test_discard_transaction`: Transaction cancellation
- `test_watch_multi_exec_success`: WATCH with successful transaction
- `test_watch_multi_exec_abort`: WATCH with aborted transaction
- `test_transaction_error_handling`: Mixed success/failure results
- `test_nested_multi_error`: Error handling for nested MULTI
- `test_exec_without_multi_error`: Error handling for EXEC without MULTI
- `test_discard_without_multi_error`: Error handling for DISCARD without MULTI

Run tests with:
```bash
make python-test
# or
cd python && python3 dev.py test --args -k "test_multi_exec_discard"
```

## Implementation Details

The transaction commands are implemented in:

- **Async Client**: `python/glide-async/python/glide/async_commands/core.py`
- **Sync Client**: `python/glide-sync/glide_sync/sync_commands/core.py`
- **Protocol**: Uses existing `RequestType.Multi`, `RequestType.Exec`, `RequestType.Discard` from protobuf
- **Tests**: `python/tests/async_tests/test_async_client.py` and `python/tests/sync_tests/test_sync_client.py`

## Migration Guide

If you're currently using the batch system and want to migrate to individual commands:

### Before (Batch System)
```python
batch = Batch(is_atomic=True)
batch.set("key1", "value1")
batch.set("key2", "value2")
batch.get("key1")
results = await client.exec(batch, raise_on_error=True)
```

### After (Individual Commands)
```python
await client.multi()
await client.set("key1", "value1")
await client.set("key2", "value2") 
await client.get("key1")
results = await client.exec()
```

The batch system remains fully supported and is not deprecated. Choose the approach that best fits your use case.