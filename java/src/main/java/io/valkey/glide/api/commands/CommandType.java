/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package io.valkey.glide.api.commands;

/**
 * Enumeration of supported Valkey/Redis commands.
 * This enum defines all the command types that can be executed through the client.
 */
public enum CommandType {
    // String commands
    GET,
    SET,
    MGET,
    MSET,
    DEL,
    EXISTS,
    INCR,
    DECR,
    INCRBY,
    DECRBY,
    INCRBYFLOAT,
    STRLEN,
    APPEND,
    GETRANGE,
    SETRANGE,
    
    // Hash commands
    HGET,
    HSET,
    HMGET,
    HMSET,
    HGETALL,
    HDEL,
    HEXISTS,
    HLEN,
    HKEYS,
    HVALS,
    HINCRBY,
    HINCRBYFLOAT,
    
    // List commands
    LPUSH,
    RPUSH,
    LPOP,
    RPOP,
    LLEN,
    LINDEX,
    LSET,
    LRANGE,
    LTRIM,
    LREM,
    LINSERT,
    BLPOP,
    BRPOP,
    BRPOPLPUSH,
    
    // Set commands
    SADD,
    SREM,
    SMEMBERS,
    SCARD,
    SISMEMBER,
    SINTER,
    SUNION,
    SDIFF,
    SINTERSTORE,
    SUNIONSTORE,
    SDIFFSTORE,
    SMOVE,
    SPOP,
    SRANDMEMBER,
    
    // Sorted Set commands
    ZADD,
    ZREM,
    ZRANGE,
    ZRANGEBYSCORE,
    ZREVRANGE,
    ZREVRANGEBYSCORE,
    ZCARD,
    ZSCORE,
    ZCOUNT,
    ZINCRBY,
    ZRANK,
    ZREVRANK,
    ZREMRANGEBYRANK,
    ZREMRANGEBYSCORE,
    ZINTERSTORE,
    ZUNIONSTORE,
    
    // Connection commands
    PING,
    ECHO,
    AUTH,
    SELECT,
    QUIT,
    
    // Server commands
    INFO,
    DBSIZE,
    FLUSHDB,
    FLUSHALL,
    SAVE,
    BGSAVE,
    LASTSAVE,
    SHUTDOWN,
    DEBUG,
    MONITOR,
    
    // Key commands
    EXPIRE,
    EXPIREAT,
    TTL,
    PTTL,
    PERSIST,
    KEYS,
    RANDOMKEY,
    RENAME,
    RENAMENX,
    TYPE,
    DUMP,
    RESTORE,
    MIGRATE,
    MOVE,
    OBJECT,
    
    // Transaction commands
    MULTI,
    EXEC,
    DISCARD,
    WATCH,
    UNWATCH,
    
    // Scripting commands
    EVAL,
    EVALSHA,
    SCRIPT,
    
    // Pub/Sub commands
    PUBLISH,
    SUBSCRIBE,
    UNSUBSCRIBE,
    PSUBSCRIBE,
    PUNSUBSCRIBE,
    PUBSUB,
    
    // Cluster commands
    CLUSTER,
    READONLY,
    READWRITE,
    
    // Stream commands
    XADD,
    XREAD,
    XRANGE,
    XREVRANGE,
    XLEN,
    XDEL,
    XTRIM,
    XGROUP,
    XREADGROUP,
    XACK,
    XCLAIM,
    XPENDING,
    XINFO,
    
    // Geo commands
    GEOADD,
    GEODIST,
    GEOHASH,
    GEOPOS,
    GEORADIUS,
    GEORADIUSBYMEMBER,
    
    // HyperLogLog commands
    PFADD,
    PFCOUNT,
    PFMERGE,
    
    // Bitmap commands
    SETBIT,
    GETBIT,
    BITCOUNT,
    BITOP,
    BITPOS,
    BITFIELD,
    
    // Generic commands
    SCAN,
    HSCAN,
    SSCAN,
    ZSCAN,
    SORT,
    
    // Module commands
    MODULE,
    
    // Memory commands
    MEMORY,
    
    // Config commands
    CONFIG,
    
    // Client commands
    CLIENT,
    
    // Slowlog commands
    SLOWLOG,
    
    // Latency commands
    LATENCY,
    
    // Custom command (for arbitrary commands)
    CUSTOM
}
