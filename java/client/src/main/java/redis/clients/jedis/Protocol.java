/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import java.nio.charset.StandardCharsets;
import redis.clients.jedis.commands.ProtocolCommand;

/**
 * Redis protocol utilities and command definitions.
 *
 * <p>This class provides compatibility with original Jedis Protocol class, containing essential
 * Redis commands and protocol utilities.
 *
 * <p><b>Compatibility Note:</b> This is a subset of the original Jedis Protocol class, containing
 * only the most commonly used commands that are supported by the GLIDE compatibility layer.
 */
public final class Protocol {

    /**
     * Redis protocol commands enum.
     *
     * <p>This enum contains Redis commands that can be used with the sendCommand method. All commands
     * are supported through customCommand fallback for maximum compatibility.
     */
    public static enum Command implements ProtocolCommand {
        // Basic commands
        PING,
        SET,
        GET,
        EXISTS,
        DEL,
        TYPE,
        ECHO,

        // String commands
        MGET,
        MSET,
        SETEX,
        PSETEX,
        SETNX,
        GETSET,
        GETDEL,
        APPEND,
        STRLEN,
        INCR,
        INCRBY,
        INCRBYFLOAT,
        DECR,
        DECRBY,

        // Key expiration and management
        EXPIRE,
        EXPIREAT,
        PEXPIRE,
        PEXPIREAT,
        TTL,
        PTTL,
        PERSIST,
        RENAME,
        RENAMENX,
        MOVE,
        RANDOMKEY,

        // Key operations
        RESTORE,
        DUMP,
        MIGRATE,
        COPY,
        TOUCH,
        UNLINK,

        // Hash commands
        HGET,
        HSET,
        HMGET,
        HMSET,
        HGETALL,
        HKEYS,
        HVALS,
        HLEN,
        HEXISTS,
        HDEL,
        HINCRBY,
        HINCRBYFLOAT,
        HSETNX,
        HSTRLEN,

        // List commands
        LPUSH,
        RPUSH,
        LPOP,
        RPOP,
        LLEN,
        LRANGE,
        LINDEX,
        LSET,
        LREM,
        LTRIM,
        LINSERT,
        LPUSHX,
        RPUSHX,

        // Set commands
        SADD,
        SREM,
        SMEMBERS,
        SCARD,
        SISMEMBER,
        SMISMEMBER,
        SINTER,
        SUNION,
        SDIFF,
        SPOP,
        SRANDMEMBER,
        SMOVE,

        // Sorted set commands
        ZADD,
        ZREM,
        ZCARD,
        ZCOUNT,
        ZRANGE,
        ZRANGEBYSCORE,
        ZRANK,
        ZREVRANK,
        ZSCORE,
        ZINCRBY,

        // Bit operations
        SETBIT,
        GETBIT,
        BITCOUNT,
        BITPOS,
        BITOP,
        BITFIELD,

        // HyperLogLog
        PFADD,
        PFCOUNT,
        PFMERGE,

        // Pub/Sub
        PUBLISH,
        SUBSCRIBE,
        UNSUBSCRIBE,
        PSUBSCRIBE,
        PUNSUBSCRIBE,

        // Transactions
        MULTI,
        EXEC,
        DISCARD,
        WATCH,
        UNWATCH,

        // Connection
        AUTH,
        SELECT,
        QUIT,
        PING_CONNECTION,

        // Server
        INFO,
        CONFIG,
        FLUSHDB,
        FLUSHALL,
        DBSIZE,
        TIME,
        LASTSAVE,

        // Scripting
        EVAL,
        EVALSHA,
        SCRIPT,

        // Streams (basic support)
        XADD,
        XREAD,
        XLEN,
        XDEL;

        private final byte[] raw;

        private Command() {
            raw = name().getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public byte[] getRaw() {
            return raw;
        }
    }
}
