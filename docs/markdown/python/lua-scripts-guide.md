# Using Lua Scripts with Valkey GLIDE for Python

This guide covers how to use Lua scripts with Valkey GLIDE for Python, including the `Script` class, script execution, management, and best practices.

## Table of Contents

- [Prerequisites and Setup](#prerequisites-and-setup)
- [Overview](#overview)
- [Basic Script Usage](#basic-script-usage)
- [Scripts with Keys and Arguments](#scripts-with-keys-and-arguments)
- [Script Management](#script-management)
- [Cluster Mode Considerations](#cluster-mode-considerations)
- [Advanced Features](#advanced-features)
- [Best Practices](#best-practices)
- [Error Handling](#error-handling)
- [Migration from Direct EVAL](#migration-from-direct-eval)

## Prerequisites and Setup

### Required Imports

```python
import asyncio
from glide import (
    Script,
    GlideClient,
    GlideClientConfiguration,
    NodeAddress
)

# For script management examples
from glide import FlushMode

# For error handling examples
from glide import RequestError

# For cluster examples
from glide import GlideClusterClient, GlideClusterClientConfiguration, SlotKeyRoute, SlotType, AllPrimaries
```

### Basic Client Setup

```python
# Standalone client configuration
config = GlideClientConfiguration(addresses=[NodeAddress("localhost", 6379)])

# Create client
client = await GlideClient.create(config)

# Always close the client when done
await client.close()
```

### Cluster Client Setup (for cluster examples)

```python
# Cluster client configuration
config = GlideClusterClientConfiguration(addresses=[NodeAddress("localhost", 7000)])

# Create cluster client
cluster_client = await GlideClusterClient.create(config)

# Always close when done
await cluster_client.close()
```

### Requirements

- Running Valkey/Redis server on localhost:6379 (or cluster on ports 7000+)
- GLIDE Python client installed
- Python 3.8+ with asyncio support

### Running the Examples

All examples assume you have an async context. For standalone scripts, use:

```python
async def main():
    # Your example code here
    pass

if __name__ == "__main__":
    asyncio.run(main())
```

**Important Notes:**
- All script arguments must be strings, not integers
- Keys and arguments are automatically encoded by GLIDE
- Scripts are cached automatically using SHA1 hashes

## Overview

Valkey GLIDE provides a `Script` class that wraps Lua scripts and handles their execution efficiently. Scripts are automatically cached using SHA1 hashes and executed via `EVALSHA` for optimal performance.

### Key Benefits

- **Automatic Caching**: Scripts are cached using SHA1 hashes for efficient reuse
- **Cluster Support**: Scripts work seamlessly in both standalone and cluster modes
- **Performance**: Uses `EVALSHA` internally to avoid sending script code repeatedly
- **Management**: Built-in methods for script lifecycle management

## Basic Script Usage

### Creating and Executing Simple Scripts

```python
from glide import Script, GlideClient, GlideClientConfiguration, NodeAddress

# Create a client
config = GlideClientConfiguration(addresses=[NodeAddress("localhost", 6379)])
client = await GlideClient.create(config)

# Create a simple script
script = Script("return 'Hello, Valkey!'")

# Execute the script
result = await client.invoke_script(script)
print(result)  # b'Hello, Valkey!'
```

### Scripts with Return Values

```python
# Script that returns a number
script = Script("return 42")
result = await client.invoke_script(script)
print(result)  # 42

# Script that returns an array
script = Script("return {1, 2, 3, 'hello'}")
result = await client.invoke_script(script)
print(result)  # [1, 2, 3, b'hello']
```

## Scripts with Keys and Arguments

Scripts can access keys and arguments through the `KEYS` and `ARGV` arrays.

### Using KEYS Array

```python
# Script that operates on keys
script = Script("return redis.call('GET', KEYS[1])")

# Execute with keys
result = await client.invoke_script(script, keys=["mykey"])
```

### Using ARGV Array

```python
# Script that uses arguments
script = Script("return 'Hello, ' .. ARGV[1]")

# Execute with arguments
result = await client.invoke_script(script, args=["World"])
print(result)  # b'Hello, World'
```

### Combining Keys and Arguments

```python
# Script that sets a key-value pair
script = Script("return redis.call('SET', KEYS[1], ARGV[1])")

# Execute with both keys and arguments
result = await client.invoke_script(
    script,
    keys=["user:1000:name"],
    args=["John Doe"]
)
print(result)  # b'OK'

# Script that gets and modifies a value
script = Script("""
    local current = redis.call('GET', KEYS[1])
    if current then
        return redis.call('SET', KEYS[1], current .. ARGV[1])
    else
        return redis.call('SET', KEYS[1], ARGV[1])
    end
""")

result = await client.invoke_script(
    script,
    keys=["counter"],
    args=[":increment"]
)
```

### Working with Multiple Keys

```python
# Script that works with multiple keys
script = Script("""
    local key1_val = redis.call('GET', KEYS[1])
    local key2_val = redis.call('GET', KEYS[2])
    return {key1_val, key2_val}
""")

result = await client.invoke_script(
    script,
    keys=["key1", "key2"]
)
```

## Script Management

### Script Hashing and Caching

Each script is automatically assigned a SHA1 hash for efficient caching:

```python
script = Script("return 'Hello'")

# Get the script's SHA1 hash
hash_value = script.get_hash()
print(f"Script hash: {hash_value}")

# Scripts with the same code have the same hash
script2 = Script("return 'Hello'")
assert script.get_hash() == script2.get_hash()
```

### Viewing Script Source (Valkey 8.0+)

```python
# Load a script
script = Script("return 'Hello World'")
await client.invoke_script(script)

# Show the original source code
source = await client.script_show(script.get_hash())
print(source)  # b"return 'Hello World'"
```

### Checking Script Existence

```python
# Check if scripts exist in the server cache
script1 = Script("return 'Script 1'")
script2 = Script("return 'Script 2'")

# Load script1 by executing it
await client.invoke_script(script1)

# Check existence of both scripts
exists = await client.script_exists([
    script1.get_hash(),
    script2.get_hash()
])
print(exists)  # [True, False] - only script1 was loaded
```

### Flushing Script Cache

```python
# Load a script
script = Script("return 'Test'")
await client.invoke_script(script)

# Verify it exists
exists = await client.script_exists([script.get_hash()])
print(exists)  # [True]

# Flush the script cache on the server
await client.script_flush()

# Verify script is gone from server
exists = await client.script_exists([script.get_hash()])
print(exists)  # [False]

# Flush with ASYNC mode (non-blocking)
await client.script_flush(FlushMode.ASYNC)

# Verify script still exist from client
print(f"Can still access hash: {script.get_hash()}")

# Explicit cleanup of client-side Script object
del script
```

### Killing Running Scripts
A script can be safely killed if it has only performed read-only operations. However, once it executes any write operation, it becomes uninterruptible and must either run to completion or reach a timeout.

```python
# This long-running script CAN be killed (read-only)
killable_long_script = Script("""
    local start = redis.call('TIME')[1]
    while redis.call('TIME')[1] - start < 10 do
        redis.call('GET', 'some_key')  -- Read-only operations
    end
    return 'Done'
""")

# This long-running script CANNOT be killed (performs writes)
unkillable_script = Script("""
    redis.call('SET', 'temp', 'value')  -- Write operation
    local start = redis.call('TIME')[1]
    while redis.call('TIME')[1] - start < 10 do
        -- Long operation after write
    end
    return 'Done'
""")

# In one task, run the script
async def run_script():
    try:
        await client.invoke_script(killable_long_script)
    except RequestError as e:
        if "Script killed" in str(e):
            print("Script was killed")

# In another task, kill the script
async def kill_script():
    await asyncio.sleep(2)  # Wait a bit
    await client.script_kill()
    print("Script killed")

# Run both tasks concurrently
await asyncio.gather(run_script(), kill_script())
```

## Cluster Mode Considerations

### Default Behavior

In cluster mode, scripts are automatically routed to the appropriate nodes:

```python
from glide import GlideClusterClient

cluster_client = await GlideClusterClient.create(config)

# This works the same in cluster mode
script = Script("return redis.call('SET', KEYS[1], ARGV[1])")
result = await cluster_client.invoke_script(
    script,
    keys=["user:1000"],  # Routed based on key hash
    args=["John"]
)
```

### Explicit Routing

You can explicitly route scripts to specific nodes:

```python
from glide import SlotKeyRoute, SlotType, AllPrimaries

# Route to a specific slot
route = SlotKeyRoute(SlotType.PRIMARY, "user:1000")
result = await cluster_client.invoke_script_route(
    script,
    keys=["user:1000"],
    args=["John"],
    route=route
)

# Route to all primary nodes
route = AllPrimaries()
result = await cluster_client.invoke_script_route(
    script,
    route=route
)
```

### Multi-Slot Scripts

Scripts that access multiple keys **must** ensure all keys belong to the same slot in cluster mode:

```python
# This WILL ALWAYS FAIL in cluster mode if keys are in different slots
# The server rejects the request immediately with CROSSSLOT error
script = Script("""
    redis.call('SET', KEYS[1], ARGV[1])
    redis.call('SET', KEYS[2], ARGV[2])
    return 'OK'
""")

# This will be rejected before execution
try:
    result = await cluster_client.invoke_script(
        script,
        keys=["key1", "key2"],  # Different slots - ALWAYS fails
        args=["value1", "value2"]
    )
except RequestError as e:
    if "CrossSlot" in str(e):
        print("Keys are in different slots - script rejected")

# Keys must be in the same slot using hash tags
result = await cluster_client.invoke_script(
    script,
    keys=["user:{1000}:name", "user:{1000}:email"],  # Same slot due to {1000}
    args=["John", "john@example.com"]
)
```

## Advanced Features

### Binary Data Support

Scripts can work with binary data:

```python
# Script with binary input
script = Script(bytes("return ARGV[1]", "utf-8"))

# Execute with binary arguments
result = await client.invoke_script(
    script,
    args=[bytes("binary data", "utf-8")]
)
```

### Large Keys and Arguments

GLIDE handles large keys and arguments efficiently:

```python
# Large key (8KB)
large_key = "0" * (2**13)
script = Script("return KEYS[1]")
result = await client.invoke_script(script, keys=[large_key])

# Large arguments (4KB each)
large_arg1 = "0" * (2**12)
large_arg2 = "1" * (2**12)
script = Script("return ARGV[2]")
result = await client.invoke_script(script, args=[large_arg1, large_arg2])
```

### Script Reuse and Performance

Scripts are automatically cached and reused:

```python
# Create a reusable script
increment_script = Script("""
    local current = redis.call('GET', KEYS[1])
    if current then
        return redis.call('SET', KEYS[1], current + ARGV[1])
    else
        return redis.call('SET', KEYS[1], ARGV[1])
    end
""")

# Use the same script multiple times - efficient due to caching
for i in range(100):
    await client.invoke_script(
        increment_script,
        keys=[f"counter:{i}"],
        args=["1"]
    )
```

## Best Practices
### 1. Use Scripts for Atomic Non-primitive Operations

```python
# Good: Conditional update with multiple data structures
conditional_update = Script("""
    local current = redis.call('GET', KEYS[1])
    local threshold = tonumber(ARGV[2])

    if current and tonumber(current) >= threshold then
        redis.call('SET', KEYS[1], ARGV[1])
        redis.call('LPUSH', KEYS[2], ARGV[1])
        redis.call('EXPIRE', KEYS[2], ARGV[3])
        return 1
    else
        return 0
    end
""")

result = await client.invoke_script(
    conditional_update,
    keys=["user:score", "user:history"],
    args=["100", "50", "86400"]  # new score, threshold, expire in 1 day
)
```

### 2. Handle Nil Values Properly

```python
# Good: Proper nil handling
safe_script = Script("""
    local val = redis.call('GET', KEYS[1])
    if val then
        return val
    else
        return 'default_value'
    end
""")
```

### 3. Use Appropriate Data Types

```python
# Good: Return appropriate types
typed_script = Script("""
    local value = redis.call('GET', KEYS[1])
    return tonumber(value) or 0  -- Ensure numeric return, default to 0 if nil
""")
```

### 4. Consider Cluster Constraints

```python
# Good: Use hash tags for related keys
cluster_script = Script("""
    redis.call('SET', KEYS[1], ARGV[1])
    redis.call('SET', KEYS[2], ARGV[2])
    return 'OK'
""")

# Execute with hash tags
await cluster_client.invoke_script(
    cluster_script,
    keys=["user:{123}:name", "user:{123}:email"],
    args=["John", "john@example.com"]
)
```

## Error Handling

### Common Script Errors

```python
import asyncio
from glide import RequestError

# Handle script execution errors
script = Script("return redis.call('INCR', 'not_a_number')")

try:
    result = await client.invoke_script(script)
except RequestError as e:
    if "WRONGTYPE" in str(e) or "not an integer" in str(e):
        print("Type error in script")
    elif "syntax error" in str(e).lower():
        print("Lua syntax error in script")
    elif "unknown command" in str(e).lower():
        print("Invalid Redis command in script")
    else:
        print(f"Script error: {e}")
```

### Script Timeout Handling

```python
# Configure client timeout for long-running scripts
config = GlideClientConfiguration(
    addresses=[NodeAddress("localhost", 6379)],
    request_timeout=30000  # 30 seconds for long scripts (default is usually 5000ms)
)
client = await GlideClient.create(config)

# Handle long-running scripts
long_script = Script("""
    local start = redis.call('TIME')[1]
    while redis.call('TIME')[1] - start < 25 do
        redis.call('GET', 'dummy_key')  -- Read-only operation
    end
    return 'Done'
""")

try:
    result = await client.invoke_script(long_script)
    print(f"Script completed: {result.decode('utf-8')}")
except RequestError as e:
    if "timeout" in str(e).lower():
        print("Client timeout - script may still be running on server!")
        print("Consider increasing request_timeout in client configuration")
    elif "Script killed" in str(e):
        print("Script was killed by server (only possible for read-only scripts)")
    else:
        print(f"Script error: {e}")

# Important: Client timeout != Script termination
# - Client stops waiting for response
# - Script continues running on server
# - Use SCRIPT KILL to stop read-only scripts if needed
```

### Cluster-Specific Errors

```python
# Handle cluster routing errors
try:
    result = await cluster_client.invoke_script(
        script,
        keys=["key1", "key2"]  # Might be in different slots
    )
except RequestError as e:
    if "CROSSSLOT" in str(e):
        print("Keys are in different slots")
        # Use hash tags or route explicitly
```

## Batch Operations and Transactions

Currently, `invoke_script` is not supported in batch operations (pipelines/transactions). To use Lua scripts within an atomic batch (MULTI/EXEC transaction), you must use the `EVAL` command with `custom_command`.

**Note**: The `Transaction` class is deprecated. Use `Batch(is_atomic=True)` instead.

### Using EVAL in Atomic Batches

```python
from glide import Batch

# Create an atomic batch (transaction)
batch = Batch(is_atomic=True)
batch.set("batch-key", "batch-value")
batch.get("batch-key")
batch.custom_command(["EVAL", "return 'Hello from Lua!'", "0"])

# Execute the batch
results = await client.exec(batch=batch, raise_on_error=False)

print("Batch executed:")
print(f"SET result: {results[0]}")      # b'OK'
print(f"GET result: {results[1]}")      # b'batch-value'
print(f"EVAL result: {results[2]}")     # b'Hello from Lua!'
```

### EVAL with Keys and Arguments in Atomic Batches

```python
# Script with keys and arguments
batch = Batch(is_atomic=True)
batch.custom_command([
    "EVAL",
    "return redis.call('SET', KEYS[1], ARGV[1])",
    "1",           # Number of keys
    "script-key",  # Key
    "script-value" # Argument
])
batch.get("script-key")

results = await client.exec(batch, raise_on_error=False)
print(f"EVAL result: {results[0]}")  # b'OK'
print(f"GET result: {results[1]}")   # b'script-value'
```

## Migration from Direct EVAL

If you're migrating from direct `EVAL` commands, here's how to adapt:

### Before (Direct EVAL)
```python
# Old approach with custom commands (not recommended)
result = await client.custom_command([
    "EVAL",
    "return redis.call('SET', KEYS[1], ARGV[1])",
    "1",
    "mykey",
    "myvalue"
])
```

### After (Script Class)
```python
# New approach with Script class (recommended)
script = Script("return redis.call('SET', KEYS[1], ARGV[1])")
result = await client.invoke_script(
    script,
    keys=["mykey"],
    args=["myvalue"]
)
```

### Benefits of Migration

1. **Automatic Caching**: Scripts are cached automatically
2. **Better Error Handling**: More specific error types
3. **Cluster Support**: Automatic routing in cluster mode
4. **Type Safety**: Better integration with GLIDE's type system
5. **Performance**: Optimized execution path

## Examples Repository

Here are some common script patterns:

### Rate Limiting
```python
rate_limit_script = Script("""
    local key = KEYS[1]
    local limit = tonumber(ARGV[1])
    local window = tonumber(ARGV[2])

    local current = redis.call('GET', key)
    if current == false then
        redis.call('SET', key, 1)
        redis.call('EXPIRE', key, window)
        return {1, limit}
    end

    current = tonumber(current)
    if current < limit then
        local new_val = redis.call('INCR', key)
        local ttl = redis.call('TTL', key)
        return {new_val, limit}
    else
        local ttl = redis.call('TTL', key)
        return {current, limit, ttl}
    end
""")

# Usage
result = await client.invoke_script(
    rate_limit_script,
    keys=["rate_limit:user:123"],
    args=["10", "60"]  # 10 requests per 60 seconds
)
```

### Distributed Lock
```python
acquire_lock_script = Script("""
    if redis.call('SET', KEYS[1], ARGV[1], 'NX', 'EX', ARGV[2]) then
        return 1
    else
        return 0
    end
""")

release_lock_script = Script("""
    if redis.call('GET', KEYS[1]) == ARGV[1] then
        return redis.call('DEL', KEYS[1])
    else
        return 0
    end
""")

# Acquire lock
lock_acquired = await client.invoke_script(
    acquire_lock_script,
    keys=["lock:resource:123"],
    args=["unique_token", "30"]  # 30 second expiration
)

if lock_acquired:
    try:
        # Do work while holding lock
        pass
    finally:
        # Release lock
        await client.invoke_script(
            release_lock_script,
            keys=["lock:resource:123"],
            args=["unique_token"]
        )
```

### Conditional Update
```python
conditional_update_script = Script("""
    local current = redis.call('GET', KEYS[1])
    if current == ARGV[1] then
        redis.call('SET', KEYS[1], ARGV[2])
        return 1
    else
        return 0
    end
""")

# Update only if current value matches expected
updated = await client.invoke_script(
    conditional_update_script,
    keys=["user:123:status"],
    args=["pending", "active"]  # Change from "pending" to "active"
)
```

## Conclusion

Valkey GLIDE's `Script` class provides a powerful and efficient way to execute Lua scripts. By following the patterns and best practices outlined in this guide, you can:

- Write efficient, atomic operations
- Handle complex business logic server-side
- Ensure optimal performance through automatic caching
- Work seamlessly in both standalone and cluster environments

For more information, see the [Valkey Lua scripting documentation](https://valkey.io/commands/eval/) and the [GLIDE API documentation](https://valkey.io/valkey-glide/).
