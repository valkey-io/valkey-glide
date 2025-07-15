# CommandType Enum Implementation Plan

## Overview

This document outlines the implementation plan for replacing protobuf `RequestType` enum with a custom `CommandType` enum in the Valkey GLIDE JNI implementation.

## Current Issue

Despite successfully converting all 233 client methods to use direct JNI typed methods, the codebase still depends on protobuf `RequestType` enum constants for command identification. This creates an unnecessary coupling between the new JNI implementation and the old protobuf code.

## Solution

Create a new `CommandType` enum that will replace `RequestType` across the codebase, resulting in a clean architecture that completely eliminates protobuf dependencies.

## Implementation Steps

### 1. Create CommandType Enum

Create a new enum in package `io.valkey.glide.jni.commands`:

```java
package io.valkey.glide.jni.commands;

/**
 * Enum representing all supported Valkey commands.
 * This replaces the protobuf RequestType enum with a cleaner, non-protobuf implementation.
 */
public enum CommandType {
    // String commands
    GET("GET"),
    SET("SET"),
    MGET("MGET"),
    MSET("MSET"),
    APPEND("APPEND"),
    GETRANGE("GETRANGE"),
    SETRANGE("SETRANGE"),
    STRLEN("STRLEN"),
    
    // Hash commands
    HGET("HGET"),
    HSET("HSET"),
    HDEL("HDEL"),
    HEXISTS("HEXISTS"),
    HGETALL("HGETALL"),
    HINCRBY("HINCRBY"),
    HINCRBYFLOAT("HINCRBYFLOAT"),
    HKEYS("HKEYS"),
    HLEN("HLEN"),
    HMGET("HMGET"),
    HRANDFIELD("HRANDFIELD"),
    HSCAN("HSCAN"),
    HSETNX("HSETNX"),
    HSTRLEN("HSTRLEN"),
    HVALS("HVALS"),
    
    // List commands
    BLMOVE("BLMOVE"),
    BLPOP("BLPOP"),
    BRPOP("BRPOP"),
    LINDEX("LINDEX"),
    LINSERT("LINSERT"),
    LLEN("LLEN"),
    LMOVE("LMOVE"),
    LPOP("LPOP"),
    LPOS("LPOS"),
    LPUSH("LPUSH"),
    LPUSHX("LPUSHX"),
    LRANGE("LRANGE"),
    LREM("LREM"),
    LSET("LSET"),
    LTRIM("LTRIM"),
    RPOP("RPOP"),
    RPUSH("RPUSH"),
    RPUSHX("RPUSHX"),
    
    // Set commands
    SADD("SADD"),
    SCARD("SCARD"),
    SDIFF("SDIFF"),
    SDIFFSTORE("SDIFFSTORE"),
    SINTER("SINTER"),
    SINTERSTORE("SINTERSTORE"),
    SISMEMBER("SISMEMBER"),
    SMEMBERS("SMEMBERS"),
    SMISMEMBER("SMISMEMBER"),
    SMOVE("SMOVE"),
    SPOP("SPOP"),
    SRANDMEMBER("SRANDMEMBER"),
    SREM("SREM"),
    SUNION("SUNION"),
    SUNIONSTORE("SUNIONSTORE"),
    
    // Sorted set commands
    ZADD("ZADD"),
    ZCARD("ZCARD"),
    ZCOUNT("ZCOUNT"),
    ZDIFF("ZDIFF"),
    ZDIFFSTORE("ZDIFFSTORE"),
    ZINCRBY("ZINCRBY"),
    ZINTER("ZINTER"),
    ZINTERSTORE("ZINTERSTORE"),
    ZLEXCOUNT("ZLEXCOUNT"),
    ZMSCORE("ZMSCORE"),
    ZPOPMAX("ZPOPMAX"),
    ZPOPMIN("ZPOPMIN"),
    ZRANDMEMBER("ZRANDMEMBER"),
    ZRANGE("ZRANGE"),
    ZRANGEBYLEX("ZRANGEBYLEX"),
    ZRANGEBYSCORE("ZRANGEBYSCORE"),
    ZRANK("ZRANK"),
    ZREM("ZREM"),
    ZREMRANGEBYLEX("ZREMRANGEBYLEX"),
    ZREMRANGEBYRANK("ZREMRANGEBYRANK"),
    ZREMRANGEBYSCORE("ZREMRANGEBYSCORE"),
    ZREVRANGE("ZREVRANGE"),
    ZREVRANGEBYLEX("ZREVRANGEBYLEX"),
    ZREVRANGEBYSCORE("ZREVRANGEBYSCORE"),
    ZREVRANK("ZREVRANK"),
    ZSCORE("ZSCORE"),
    ZUNION("ZUNION"),
    ZUNIONSTORE("ZUNIONSTORE"),
    
    // Connection commands
    PING("PING"),
    ECHO("ECHO"),
    SELECT("SELECT"),
    
    // Server commands
    INFO("INFO"),
    TIME("TIME"),
    DBSIZE("DBSIZE"),
    FLUSHDB("FLUSHDB"),
    FLUSHALL("FLUSHALL"),
    LASTSAVE("LASTSAVE"),
    CONFIG_GET("CONFIG GET"),
    CONFIG_SET("CONFIG SET"),
    CONFIG_RESETSTAT("CONFIG RESETSTAT"),
    CONFIG_REWRITE("CONFIG REWRITE"),
    CLIENT_ID("CLIENT ID"),
    CLIENT_GETNAME("CLIENT GETNAME"),
    
    // Function commands
    FUNCTION_LOAD("FUNCTION LOAD"),
    FUNCTION_DELETE("FUNCTION DELETE"),
    FUNCTION_FLUSH("FUNCTION FLUSH"),
    FUNCTION_LIST("FUNCTION LIST"),
    FUNCTION_DUMP("FUNCTION DUMP"),
    FUNCTION_RESTORE("FUNCTION RESTORE"),
    FUNCTION_KILL("FUNCTION KILL"),
    FUNCTION_STATS("FUNCTION STATS"),
    FCALL("FCALL"),
    FCALL_RO("FCALL_RO"),
    
    // Script commands
    SCRIPT_EXISTS("SCRIPT EXISTS"),
    SCRIPT_FLUSH("SCRIPT FLUSH"),
    SCRIPT_KILL("SCRIPT KILL"),
    
    // PubSub commands
    PUBLISH("PUBLISH"),
    SPUBLISH("SPUBLISH"),
    PUBSUB_CHANNELS("PUBSUB CHANNELS"),
    PUBSUB_NUMPAT("PUBSUB NUMPAT"),
    PUBSUB_NUMSUB("PUBSUB NUMSUB"),
    PUBSUB_SHARDCHANNELS("PUBSUB SHARDCHANNELS"),
    PUBSUB_SHARDNUMSUB("PUBSUB SHARDNUMSUB"),
    
    // Transaction commands
    WATCH("WATCH"),
    UNWATCH("UNWATCH"),
    
    // Key management commands
    DEL("DEL"),
    UNLINK("UNLINK"),
    EXISTS("EXISTS"),
    EXPIRE("EXPIRE"),
    EXPIREAT("EXPIREAT"),
    TTL("TTL"),
    PEXPIRE("PEXPIRE"),
    PEXPIREAT("PEXPIREAT"),
    PTTL("PTTL"),
    EXPIRETIME("EXPIRETIME"),
    PEXPIRETIME("PEXPIRETIME"),
    TYPE("TYPE"),
    RENAME("RENAME"),
    RENAMENX("RENAMENX"),
    TOUCH("TOUCH"),
    MOVE("MOVE"),
    RANDOMKEY("RANDOMKEY"),
    
    // Misc commands
    LOLWUT("LOLWUT"),
    
    // Generic custom command
    CUSTOM_COMMAND("CUSTOM");

    private final String commandName;

    CommandType(String commandName) {
        this.commandName = commandName;
    }

    /**
     * Get the command name as used in the Valkey protocol.
     * 
     * @return The command name string
     */
    public String getCommandName() {
        return commandName;
    }
    
    /**
     * Convert a string command name to a CommandType enum.
     * 
     * @param commandName The command name to convert
     * @return The CommandType enum value, or CUSTOM_COMMAND if not found
     */
    public static CommandType fromString(String commandName) {
        if (commandName == null || commandName.isEmpty()) {
            throw new IllegalArgumentException("Command name cannot be null or empty");
        }
        
        String upperCommand = commandName.toUpperCase();
        for (CommandType type : values()) {
            if (type.commandName.equals(upperCommand)) {
                return type;
            }
        }
        
        // No matching enum found, return custom command
        return CUSTOM_COMMAND;
    }
}
```

### 2. Update JniCommandManager

Update the `JniCommandManager` class to use `CommandType` instead of `RequestType`:

1. Add a mapping from `RequestType` to `CommandType` for backwards compatibility
2. Update method signatures to accept `CommandType` instead of `RequestType`
3. Update the command execution logic to use `commandType.getCommandName()`

### 3. Update CommandManager

Update the `CommandManager` class to use `CommandType`:

1. Replace `COMMAND_SPECS` map to use `CommandType` keys
2. Update execute methods to accept `CommandType` instead of `RequestType`
3. Add compatibility methods for both `RequestType` and `CommandType`

### 4. Update Client Classes

Update all client classes to use `CommandType`:

1. Replace import statements for `RequestType` with imports for `CommandType`
2. Replace all references to `RequestType.X` with `CommandType.X`
3. Update any switch statements or type-specific handling

### 5. Add Routing Support

Add routing support to the JNI client:

1. Add route-based execution methods to `GlideJniClient`
2. Implement native JNI methods for routing
3. Update `JniCommandManager` to support routing properly

### 6. Clean Up and Testing

1. Remove all protobuf imports
2. Remove response handler methods no longer needed
3. Update tests to use `CommandType`
4. Run full integration tests

## Backwards Compatibility

To maintain backwards compatibility during the transition:

1. Create a full mapping between `RequestType` and `CommandType`
2. Add compatibility methods in `CommandManager` and `JniCommandManager`
3. Document the transition path for external code

## Timeline

1. **Phase 1**: Create CommandType enum and update JniCommandManager (2 days)
2. **Phase 2**: Update CommandManager and add compatibility layer (2 days)
3. **Phase 3**: Update all client classes (3 days)
4. **Phase 4**: Add routing support (2 days)
5. **Phase 5**: Testing and fixing issues (2 days)

Total estimated time: ~11 days

## Conclusion

This implementation will provide a clean, type-safe replacement for the protobuf `RequestType` enum while maintaining performance and compatibility with existing code. The `CommandType` enum approach is more maintainable, has better performance, and eliminates the protobuf dependency completely.