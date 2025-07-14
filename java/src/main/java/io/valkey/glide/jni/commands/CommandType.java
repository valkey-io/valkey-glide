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
    MSETNX("MSETNX"),
    APPEND("APPEND"),
    GETRANGE("GETRANGE"),
    SETRANGE("SETRANGE"),
    STRLEN("STRLEN"),
    GETDEL("GETDEL"),
    GETEX("GETEX"),
    INCR("INCR"),
    INCRBY("INCRBY"),
    INCRBYFLOAT("INCRBYFLOAT"),
    DECR("DECR"),
    DECRBY("DECRBY"),
    LCS("LCS"),
    
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
    BLMPOP("BLMPOP"),
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
    BZMPOP("BZMPOP"),
    BZPOPMAX("BZPOPMAX"),
    BZPOPMIN("BZPOPMIN"),
    
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
    
    // Bit commands
    BITCOUNT("BITCOUNT"),
    BITFIELD("BITFIELD"),
    BITFIELD_RO("BITFIELD_RO"),
    BITOP("BITOP"),
    BITPOS("BITPOS"),
    
    // Object commands
    OBJECT_ENCODING("OBJECT ENCODING"),
    OBJECT_FREQ("OBJECT FREQ"),
    OBJECT_IDLETIME("OBJECT IDLETIME"),
    OBJECT_REFCOUNT("OBJECT REFCOUNT"),
    
    // Copy and dump commands
    COPY("COPY"),
    DUMP("DUMP"),
    RESTORE("RESTORE"),
    
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
