# ACL Permissions Requirements for Valkey GLIDE

## Overview

Valkey GLIDE automatically executes several commands during connection establishment. When using Access Control Lists (ACLs), users must have the appropriate permissions for these commands, or connections will fail. **Missing ACL permissions can cause infinite retry loops and production outages.**

This document provides comprehensive guidance on the minimum ACL permissions required for Valkey GLIDE to function properly.

## Critical Warning

**🚨 CONNECTION FAILURES:** If ACL users lack required permissions, GLIDE will enter infinite retry loops, causing:
- Massive log flooding
- Resource exhaustion  
- Complete connection failures
- Production outages

## Commands Executed During Connection Setup

GLIDE automatically executes the following commands during every connection establishment:

### 1. HELLO Command (RESP3 Protocol Negotiation)
- **When executed**: Always for RESP3 connections
- **Required permission**: Usually works with basic authentication
- **Failure impact**: Connection fails immediately with clear error

### 2. AUTH Command (Authentication)
- **When executed**: When credentials are provided
- **Required permission**: Always allowed (part of authentication)
- **Failure impact**: Connection fails immediately with authentication error

### 3. SELECT Command (Database Selection)
- **When executed**: When database number != 0
- **Required permission**: `+select` or `+@keyspace`
- **Failure impact**: Connection fails with database switch error
- **Example**: `ACL SETUSER myuser +select`

### 4. CLIENT SETNAME Command (Connection Identification)
- **When executed**: When client name is configured
- **Required permission**: `+client|setname`
- **Failure impact**: Connection fails with client name error
- **Example**: `ACL SETUSER myuser +client|setname`

### 5. INFO Command (Availability Zone Discovery)
- **When executed**: When AZ affinity is enabled
- **Required permission**: `+info`
- **Failure impact**: Connection fails with info permission error
- **Example**: `ACL SETUSER myuser +info`

### 6. CLIENT SETINFO Commands (Library Metadata) ⚠️ CRITICAL
- **Commands executed**: 
  - `CLIENT SETINFO LIB-NAME glide-java` (or glide-python, glide-node, etc.)
  - `CLIENT SETINFO LIB-VER x.x.x`
- **When executed**: **ALWAYS** - on every connection
- **Required permission**: `+client|setinfo`
- **Failure impact**: **Silent infinite retry loops** - This is the most common cause of production issues
- **Example**: `ACL SETUSER myuser +client|setinfo`

!!! danger "Most Common Issue"
    The `CLIENT SETINFO` commands are executed on every connection attempt. Missing `+client|setinfo` permission causes infinite retry loops that can bring down applications.

### 7. PubSub Resubscription Commands (RESP3 + Existing Subscriptions)
- **Commands executed**: `SUBSCRIBE`, `PSUBSCRIBE`, `SSUBSCRIBE`
- **When executed**: RESP3 connections with existing subscriptions
- **Required permission**: `+@pubsub` or specific commands like `+subscribe +psubscribe +ssubscribe`
- **Failure impact**: Connection fails with subscription errors
- **Example**: `ACL SETUSER myuser +@pubsub`

## Additional Commands for Cluster Mode

When using GLIDE in cluster mode, additional commands are executed:

### 8. PING Command (Connection Health Check)
- **When executed**: During cluster connection establishment
- **Required permission**: `+ping`
- **Failure impact**: Connection fails during cluster setup
- **Example**: `ACL SETUSER myuser +ping`

### 9. READONLY Command (Read-from-Replica Configuration)
- **When executed**: When read-from-replica strategy is enabled
- **Required permission**: `+readonly`
- **Failure impact**: Connection fails during user connection setup
- **Example**: `ACL SETUSER myuser +readonly`

### 10. CLIENT SETNAME Command (Management Connection) ⚠️ CLUSTER CRITICAL
- **Command executed**: `CLIENT SETNAME glide_management_connection`
- **When executed**: For every cluster management connection
- **Required permission**: `+client|setname`
- **Failure impact**: **Infinite retry loops in cluster mode** - "Failed to create management connection" errors
- **Example**: `ACL SETUSER myuser +client|setname`

!!! warning "Cluster Mode Critical"
    In cluster mode, missing `+client|setname` permission causes "Failed to create management connection" errors and infinite retry loops.

## Minimum Required ACL Configurations

### Basic Read-Only User (Minimum)
```bash
# Absolute minimum for basic read-only operations
ACL SETUSER readonly-user on >password ~* -@all +@read +ping +@connection +info
```

### Recommended Read-Only User
```bash
# Comprehensive read-only user with all required connection permissions
ACL SETUSER readonly-user on >password ~* -@all +@read +ping +cluster +readonly +info +client|setname +client|setinfo
```

### Cluster Read-Only User
```bash
# Read-only user for cluster deployments (includes cluster-specific commands)
ACL SETUSER cluster-readonly-user on >password ~* -@all +@read +ping +cluster +readonly +info +client|setname +client|setinfo
```

### Read-Write User (No Admin)
```bash
# Read-write user without administrative commands
ACL SETUSER readwrite-user on >password ~* -@all +@read +@write +ping +cluster +readonly +info +client|setname +client|setinfo
```

### Application User (Comprehensive)
```bash
# Full-featured application user
ACL SETUSER app-user on >password ~app:* -@all +@read +@write +@list +@hash +@set +@sortedset +@stream +ping +info +@connection +@pubsub
```

### Legacy Redis Compatibility
```bash
# For migrating from Redis installations
ACL SETUSER legacy-user on >password ~* -@all +@read +@write +@keyspace +@connection +info +ping +@pubsub
```

## ACL Permission Categories Reference

### Essential Connection Permissions
- `+@connection` - Includes client commands, ping, select, etc.
- `+client|setname` - Required for connection identification
- `+client|setinfo` - **CRITICAL** - Required to prevent infinite retry loops
- `+info` - Required for availability zone discovery
- `+ping` - Required for connection health checks

### Database Operations
- `+@read` - All read operations (GET, HGET, etc.)
- `+@write` - All write operations (SET, HSET, etc.)
- `+@keyspace` - Key management (DEL, EXISTS, EXPIRE, etc.)
- `+select` - Database selection

### Data Structure Specific
- `+@string` - String operations
- `+@list` - List operations  
- `+@hash` - Hash operations
- `+@set` - Set operations
- `+@sortedset` - Sorted set operations
- `+@stream` - Stream operations

### PubSub and Clustering
- `+@pubsub` - All publish/subscribe operations
- `+cluster` - Cluster topology commands
- `+readonly` - Read-only cluster operations

## Testing Your ACL Configuration

### 1. Validate Permissions
```bash
# Test with your ACL user
ACL WHOAMI
ACL LIST

# Test required commands
PING
INFO
CLIENT SETINFO LIB-NAME test
CLIENT SETNAME test-connection
```

### 2. Connection Test Scripts

#### Python Example
```python
# Python example
from glide import GlideClient, GlideClientConfiguration, NodeAddress, ServerCredentials

config = GlideClientConfiguration(
    addresses=[NodeAddress("localhost", 6379)],
    credentials=ServerCredentials("your-acl-user", "your-password")
)

try:
    client = GlideClient.create_client(config).get()
    print("✅ Connection successful!")
    client.close()
except Exception as e:
    print(f"❌ Connection failed: {e}")
```

#### Java Example
```java
// Java example
import glide.api.GlideClient;
import glide.api.models.configuration.GlideClientConfiguration;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.ServerCredentials;

GlideClientConfiguration config = GlideClientConfiguration.builder()
    .addresses(List.of(NodeAddress.builder().host("localhost").port(6379).build()))
    .credentials(ServerCredentials.builder()
        .username("your-acl-user")
        .password("your-password")
        .build())
    .build();

try {
    GlideClient client = GlideClient.createClient(config).get();
    System.out.println("✅ Connection successful!");
    client.close();
} catch (Exception e) {
    System.out.println("❌ Connection failed: " + e.getMessage());
}
```

#### Node.js Example
```javascript
// Node.js example
import { GlideClient, GlideClientConfiguration } from '@valkey/valkey-glide';

const config: GlideClientConfiguration = {
    addresses: [{ host: 'localhost', port: 6379 }],
    credentials: {
        username: 'your-acl-user',
        password: 'your-password'
    }
};

try {
    const client = await GlideClient.createClient(config);
    console.log('✅ Connection successful!');
    client.close();
} catch (error) {
    console.log('❌ Connection failed:', error.message);
}
```

### 3. ACL Testing Commands

#### Test Individual Permissions
```bash
# Basic connection commands
redis-cli -u redis://your-acl-user:your-password@localhost:6379 PING
redis-cli -u redis://your-acl-user:your-password@localhost:6379 INFO server

# Client commands
redis-cli -u redis://your-acl-user:your-password@localhost:6379 CLIENT SETNAME test
redis-cli -u redis://your-acl-user:your-password@localhost:6379 CLIENT SETINFO LIB-NAME test

# Data commands
redis-cli -u redis://your-acl-user:your-password@localhost:6379 SET test:key value
redis-cli -u redis://your-acl-user:your-password@localhost:6379 GET test:key
```

#### Test Cluster Commands (if using cluster mode)
```bash
# Cluster-specific commands
redis-cli -u redis://your-acl-user:your-password@localhost:6379 READONLY
redis-cli -u redis://your-acl-user:your-password@localhost:6379 CLUSTER NODES
```

### 4. Automated ACL Validation Script

#### Bash Script
```bash
#!/bin/bash
# acl-test.sh - Test ACL permissions for Valkey GLIDE

USER="your-acl-user"
PASSWORD="your-password"
HOST="localhost"
PORT="6379"

REDIS_CLI="redis-cli -u redis://$USER:$PASSWORD@$HOST:$PORT"

echo "🔍 Testing ACL permissions for user: $USER"

# Test essential commands
commands=(
    "PING"
    "INFO server"
    "CLIENT SETNAME glide-test"
    "CLIENT SETINFO LIB-NAME glide-test"
    "CLIENT SETINFO LIB-VER 1.0.0"
)

for cmd in "${commands[@]}"; do
    echo -n "Testing '$cmd'... "
    if $REDIS_CLI $cmd >/dev/null 2>&1; then
        echo "✅"
    else
        echo "❌"
        echo "⚠️  Missing permission for: $cmd"
    fi
done

echo "📋 Testing completed. Fix any ❌ failures before using GLIDE."
```

## Troubleshooting Connection Issues

### Common Error Patterns

#### 1. CLIENT SETINFO Permission Denied
```
WARN: Failed to create management connection for node "hostname:6379". 
Error: NOPERM: this user has no permissions to run the 'client|setinfo' command
```
**Solution**: Add `+client|setinfo` to your ACL user

#### 2. CLIENT SETNAME Permission Denied (Cluster Mode)
```
WARN: Failed to create management connection for node "hostname:6379". 
Error: NOPERM: this user has no permissions to run the 'client|setname' command
```
**Solution**: Add `+client|setname` to your ACL user

#### 3. Infinite Retry Loops
**Symptoms**: 
- Thousands of identical permission errors in logs
- No successful connections
- High CPU usage from retry attempts

**Solution**: Ensure ALL required connection permissions are granted, especially `+client|setinfo` and `+client|setname`

#### 4. Database Selection Failures
```
Error: Redis server refused to switch database
```
**Solution**: Add `+select` or `+@keyspace` to your ACL user

#### 5. Cluster Connection Failures
```
Error: Failed to create initial connections
```
**Solution**: Ensure cluster-specific permissions (`+ping`, `+readonly`, `+cluster`) are granted

### Debug Steps

1. **Check server logs** for NOPERM errors
2. **Test individual commands** with your ACL user
3. **Use minimal configuration** first, then add features
4. **Verify permissions** with `ACL WHOAMI` and `ACL LIST`
5. **Test connection** with simple clients before using GLIDE

## Migration from Redis/ElastiCache

When migrating from Redis or AWS ElastiCache for Redis to Valkey with GLIDE:

1. **Review existing ACL configurations**
2. **Add missing GLIDE-specific permissions** (`+client|setinfo`, `+client|setname`)
3. **Test thoroughly** in non-production environments
4. **Monitor logs** for permission errors during migration

## Verifying the Fix

As of this update, Valkey GLIDE has been patched to properly handle ACL permission errors for `CLIENT SETINFO` commands. The pipeline implementation now correctly ignores server errors for commands marked as ignored, preventing infinite retry loops.

### How to Verify the Fix Works

1. **Create a restricted ACL user** missing `+client|setinfo`:
   ```bash
   ACL SETUSER test-user on >password ~* -@all +@read +ping +info +client|setname
   ```

2. **Test connection with GLIDE** - it should now succeed without infinite retries
3. **Check logs** - you may see permission warnings, but connections should establish successfully

### Before vs After the Fix

**Before (Problematic Behavior):**
```
WARN: Failed to create management connection for node "hostname:6379". 
Error: NOPERM: this user has no permissions to run the 'client|setinfo' command
[Infinite retry loop continues...]
```

**After (Fixed Behavior):**
```
WARN: CLIENT SETINFO command failed (ignored): NOPERM: this user has no permissions to run the 'client|setinfo' command
INFO: Connection established successfully
```

## Best Practices

1. **Always include connection permissions** in ACL configurations
2. **Test ACL changes** in development before production
3. **Monitor application logs** for NOPERM errors
4. **Use principle of least privilege** while ensuring functionality
5. **Document your ACL configurations** for your team
6. **Have rollback plans** when modifying ACL configurations

## Security Considerations

- Grant minimum required permissions for your use case
- Use key patterns (`~pattern*`) to restrict data access
- Regularly audit ACL configurations
- Consider separate users for different application components
- Monitor for unauthorized command usage

## Additional Resources

- [Valkey ACL Documentation](https://valkey.io/docs/topics/acl/)
- [Redis ACL Tutorial](https://redis.io/docs/interact/programmability/acl/)
- [Valkey GLIDE General Concepts](https://github.com/valkey-io/valkey-glide/wiki/General-Concepts)