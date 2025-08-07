/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import java.nio.charset.StandardCharsets;
import redis.clients.jedis.args.Rawable;
import redis.clients.jedis.commands.ProtocolCommand;

/**
 * Redis protocol utilities and command definitions.
 *
 * <p>This class provides compatibility with original Jedis Protocol class, containing essential
 * constants and command definitions for Redis protocol operations.
 */
public class Protocol {

    /** Default Redis host */
    public static final String DEFAULT_HOST = "localhost";

    /** Default Redis port */
    public static final int DEFAULT_PORT = 6379;

    /** Default database index */
    public static final int DEFAULT_DATABASE = 0;

    /** Default timeout in milliseconds */
    public static final int DEFAULT_TIMEOUT = 2000;

    /**
     * Redis protocol commands enum.
     *
     * <p><b>Compatibility Note:</b> This is a subset of the original Jedis Protocol class, containing
     * only the most commonly used commands that are supported by the GLIDE compatibility layer.
     */
    public enum Command implements ProtocolCommand {
        // String commands
        PING,
        SET,
        GET,
        GETSET,
        SETNX,
        SETEX,
        PSETEX,
        MGET,
        MSET,
        MSETNX,
        INCR,
        INCRBY,
        INCRBYFLOAT,
        DECR,
        DECRBY,
        APPEND,
        SUBSTR,
        STRLEN,

        // Key commands
        DEL,
        UNLINK,
        EXISTS,
        TYPE,
        KEYS,
        RANDOMKEY,
        RENAME,
        RENAMENX,
        EXPIRE,
        EXPIREAT,
        PEXPIRE,
        PEXPIREAT,
        TTL,
        PTTL,
        PERSIST,
        SORT,

        // Hash commands
        HSET,
        HGET,
        HSETNX,
        HMSET,
        HMGET,
        HINCRBY,
        HINCRBYFLOAT,
        HEXISTS,
        HDEL,
        HLEN,
        HKEYS,
        HVALS,
        HGETALL,
        HSTRLEN,

        // List commands
        RPUSH,
        LPUSH,
        LLEN,
        LRANGE,
        LTRIM,
        LINDEX,
        LSET,
        LREM,
        LPOP,
        RPOP,
        BLPOP,
        BRPOP,
        RPOPLPUSH,
        BRPOPLPUSH,
        LINSERT,
        LPUSHX,
        RPUSHX,

        // Set commands
        SADD,
        SMEMBERS,
        SREM,
        SPOP,
        SCARD,
        SISMEMBER,
        SRANDMEMBER,
        SINTER,
        SINTERSTORE,
        SUNION,
        SUNIONSTORE,
        SDIFF,
        SDIFFSTORE,
        SMOVE,
        SSCAN,

        // Sorted Set commands
        ZADD,
        ZRANGE,
        ZREM,
        ZINCRBY,
        ZRANK,
        ZREVRANK,
        ZREVRANGE,
        ZCARD,
        ZSCORE,
        ZMULTI,
        ZCOUNT,
        ZRANGEBYSCORE,
        ZREVRANGEBYSCORE,
        ZREMRANGEBYRANK,
        ZREMRANGEBYSCORE,
        ZUNIONSTORE,
        ZINTERSTORE,
        ZSCAN,

        // Connection commands
        QUIT,
        AUTH,
        SELECT,
        ECHO,

        // Server commands
        FLUSHDB,
        FLUSHALL,
        SAVE,
        BGSAVE,
        BGREWRITEAOF,
        LASTSAVE,
        SHUTDOWN,
        INFO,
        MONITOR,
        SLAVEOF,
        CONFIG,
        DBSIZE,
        TIME,
        ROLE,

        // Transaction commands
        MULTI,
        EXEC,
        DISCARD,
        WATCH,
        UNWATCH,

        // Pub/Sub commands
        SUBSCRIBE,
        UNSUBSCRIBE,
        PSUBSCRIBE,
        PUNSUBSCRIBE,
        PUBLISH,
        PUBSUB,

        // Script commands
        EVAL,
        EVALSHA,
        SCRIPT,

        // Cluster commands
        CLUSTER;

        private final byte[] raw;

        Command() {
            raw = name().getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public byte[] getRaw() {
            return raw;
        }
    }

    /** Redis protocol keywords enum. */
    public enum Keyword implements Rawable {
        AGGREGATE,
        ALPHA,
        ASC,
        BY,
        DESC,
        GET,
        LIMIT,
        MESSAGE,
        NO,
        NOSORT,
        PMESSAGE,
        PSUBSCRIBE,
        PUNSUBSCRIBE,
        OK,
        ONE,
        QUEUED,
        SET,
        STORE,
        SUBSCRIBE,
        UNSUBSCRIBE,
        WEIGHTS,
        WITHSCORES,
        RESETSTAT,
        RESET,
        FLUSH,
        EXISTS,
        LOAD,
        KILL,
        LEN,
        REFCOUNT,
        ENCODING,
        IDLETIME,
        GETNAME,
        SETNAME,
        LIST,
        MATCH,
        COUNT,
        PING,
        PONG,
        UNLOAD,
        REPLACE,
        KEYS,
        PAUSE,
        DOCTOR,
        BLOCK,
        NOACK,
        STREAMS,
        KEY,
        CREATE,
        CONSUMERGROUP,
        DELCONSUMER,
        DESTROY,
        SETID,
        IDLE,
        TIME,
        RETRYCOUNT,
        FORCE,
        JUSTID,
        MAXLEN,
        APPROXIMATELY,
        MINID,
        LIMIT_LOWER,
        LIMIT_UPPER,
        WITHDIST,
        WITHCOORD,
        WITHHASH,
        STOREDIST,
        STORE_LOWER,
        STORE_UPPER,
        ANY,
        FROMMEMBER,
        FROMLONLAT,
        BYRADIUS,
        BYBOX,
        ASC_LOWER,
        DESC_LOWER,
        COUNT_LOWER,
        WITHCOUNT,
        WITHHASH_LOWER,
        WITHCOORD_LOWER,
        WITHDIST_LOWER,
        WITHCODE,
        WITHMATCHLEN,
        IDX;

        private final byte[] raw;

        Keyword() {
            raw = name().getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public byte[] getRaw() {
            return raw;
        }
    }

    /** Redis protocol cluster keywords enum. */
    public enum ClusterKeyword implements Rawable {
        ADDSLOTS,
        DELSLOTS,
        FLUSHSLOTS,
        FORGET,
        MEET,
        NODES,
        REPLICATE,
        RESET,
        SAVECONFIG,
        SETSLOT,
        SLAVES,
        REPLICAS,
        SLOTS,
        IMPORTING,
        MIGRATING,
        STABLE,
        NODE,
        CALL,
        COUNTKEYSINSLOT,
        GETKEYSINSLOT,
        INFO,
        KEYSLOT,
        MYID;

        private final byte[] raw;

        ClusterKeyword() {
            raw = name().getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public byte[] getRaw() {
            return raw;
        }
    }
}
