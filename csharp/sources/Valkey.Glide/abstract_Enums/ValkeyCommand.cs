using System;

namespace Valkey.Glide;

internal enum ValkeyCommand
{
    NONE, // must be first for "zero reasons"

    APPEND,
    ASKING,
    AUTH,

    BGREWRITEAOF,
    BGSAVE,
    BITCOUNT,
    BITOP,
    BITPOS,
    BLPOP,
    BRPOP,
    BRPOPLPUSH,

    CLIENT,
    CLUSTER,
    CONFIG,
    COPY,
    COMMAND,

    DBSIZE,
    DEBUG,
    DECR,
    DECRBY,
    DEL,
    DISCARD,
    DUMP,

    ECHO,
    EVAL,
    EVALSHA,
    EVAL_RO,
    EVALSHA_RO,
    EXEC,
    EXISTS,
    EXPIRE,
    EXPIREAT,
    EXPIRETIME,

    FLUSHALL,
    FLUSHDB,

    GEOADD,
    GEODIST,
    GEOHASH,
    GEOPOS,
    GEORADIUS,
    GEORADIUSBYMEMBER,
    GEOSEARCH,
    GEOSEARCHSTORE,

    GET,
    GETBIT,
    GETDEL,
    GETEX,
    GETRANGE,
    GETSET,

    HDEL,
    HELLO,
    HEXISTS,
    HEXPIRE,
    HEXPIREAT,
    HEXPIRETIME,
    HGET,
    HGETALL,
    HINCRBY,
    HINCRBYFLOAT,
    HKEYS,
    HLEN,
    HMGET,
    HMSET,
    HPERSIST,
    HPEXPIRE,
    HPEXPIREAT,
    HPEXPIRETIME,
    HPTTL,
    HRANDFIELD,
    HSCAN,
    HSET,
    HSETNX,
    HSTRLEN,
    HVALS,

    INCR,
    INCRBY,
    INCRBYFLOAT,
    INFO,

    KEYS,

    LASTSAVE,
    LATENCY,
    LCS,
    LINDEX,
    LINSERT,
    LLEN,
    LMOVE,
    LMPOP,
    LPOP,
    LPOS,
    LPUSH,
    LPUSHX,
    LRANGE,
    LREM,
    LSET,
    LTRIM,

    MEMORY,
    MGET,
    MIGRATE,
    MONITOR,
    MOVE,
    MSET,
    MSETNX,
    MULTI,

    OBJECT,

    PERSIST,
    PEXPIRE,
    PEXPIREAT,
    PEXPIRETIME,
    PFADD,
    PFCOUNT,
    PFMERGE,
    PING,
    PSETEX,
    PSUBSCRIBE,
    PTTL,
    PUBLISH,
    PUBSUB,
    PUNSUBSCRIBE,

    QUIT,

    RANDOMKEY,
    READONLY,
    READWRITE,
    RENAME,
    RENAMENX,
    REPLICAOF,
    RESTORE,
    ROLE,
    RPOP,
    RPOPLPUSH,
    RPUSH,
    RPUSHX,

    SADD,
    SAVE,
    SCAN,
    SCARD,
    SCRIPT,
    SDIFF,
    SDIFFSTORE,
    SELECT,
    SENTINEL,
    SET,
    SETBIT,
    SETEX,
    SETNX,
    SETRANGE,
    SHUTDOWN,
    SINTER,
    SINTERCARD,
    SINTERSTORE,
    SISMEMBER,
    SLAVEOF,
    SLOWLOG,
    SMEMBERS,
    SMISMEMBER,
    SMOVE,
    SORT,
    SORT_RO,
    SPOP,
    SPUBLISH,
    SRANDMEMBER,
    SREM,
    STRLEN,
    SUBSCRIBE,
    SUNION,
    SUNIONSTORE,
    SSCAN,
    SSUBSCRIBE,
    SUNSUBSCRIBE,
    SWAPDB,
    SYNC,

    TIME,
    TOUCH,
    TTL,
    TYPE,

    UNLINK,
    UNSUBSCRIBE,
    UNWATCH,

    WATCH,

    XACK,
    XADD,
    XAUTOCLAIM,
    XCLAIM,
    XDEL,
    XGROUP,
    XINFO,
    XLEN,
    XPENDING,
    XRANGE,
    XREAD,
    XREADGROUP,
    XREVRANGE,
    XTRIM,

    ZADD,
    ZCARD,
    ZCOUNT,
    ZDIFF,
    ZDIFFSTORE,
    ZINCRBY,
    ZINTER,
    ZINTERCARD,
    ZINTERSTORE,
    ZLEXCOUNT,
    ZMPOP,
    ZMSCORE,
    ZPOPMAX,
    ZPOPMIN,
    ZRANDMEMBER,
    ZRANGE,
    ZRANGEBYLEX,
    ZRANGEBYSCORE,
    ZRANGESTORE,
    ZRANK,
    ZREM,
    ZREMRANGEBYLEX,
    ZREMRANGEBYRANK,
    ZREMRANGEBYSCORE,
    ZREVRANGE,
    ZREVRANGEBYLEX,
    ZREVRANGEBYSCORE,
    ZREVRANK,
    ZSCAN,
    ZSCORE,
    ZUNION,
    ZUNIONSTORE,

    UNKNOWN,
}

internal static class ValkeyCommandExtensions
{
    /// <summary>
    /// Gets whether a given command can be issued only to a primary, or if any server is eligible.
    /// </summary>
    /// <param name="command">The <see cref="ValkeyCommand"/> to check.</param>
    /// <returns><see langword="true"/> if the command is primary-only, <see langword="false"/> otherwise.</returns>
    [System.Diagnostics.CodeAnalysis.SuppressMessage("Style", "IDE0066:Convert switch statement to expression", Justification = "No, it'd be ridiculous.")]
    internal static bool IsPrimaryOnly(this ValkeyCommand command)
    {
        switch (command)
        {
            // Commands that can only be issued to a primary (writable) server
            // If a command *may* be writable (e.g. an EVAL script), it should *not* be primary-only
            //   because that'd block a legitimate use case of a read-only script on replica servers,
            //   for example spreading load via a .DemandReplica flag in the caller.
            // Basically: would it fail on a read-only replica in 100% of cases? Then it goes in the list.
            case ValkeyCommand.APPEND:
            case ValkeyCommand.BITOP:
            case ValkeyCommand.BLPOP:
            case ValkeyCommand.BRPOP:
            case ValkeyCommand.BRPOPLPUSH:
            case ValkeyCommand.DECR:
            case ValkeyCommand.DECRBY:
            case ValkeyCommand.DEL:
            case ValkeyCommand.EXPIRE:
            case ValkeyCommand.EXPIREAT:
            case ValkeyCommand.FLUSHALL:
            case ValkeyCommand.FLUSHDB:
            case ValkeyCommand.GEOSEARCHSTORE:
            case ValkeyCommand.GETDEL:
            case ValkeyCommand.GETEX:
            case ValkeyCommand.GETSET:
            case ValkeyCommand.HDEL:
            case ValkeyCommand.HEXPIRE:
            case ValkeyCommand.HEXPIREAT:
            case ValkeyCommand.HINCRBY:
            case ValkeyCommand.HINCRBYFLOAT:
            case ValkeyCommand.HMSET:
            case ValkeyCommand.HPERSIST:
            case ValkeyCommand.HPEXPIRE:
            case ValkeyCommand.HPEXPIREAT:
            case ValkeyCommand.HSET:
            case ValkeyCommand.HSETNX:
            case ValkeyCommand.INCR:
            case ValkeyCommand.INCRBY:
            case ValkeyCommand.INCRBYFLOAT:
            case ValkeyCommand.LINSERT:
            case ValkeyCommand.LMOVE:
            case ValkeyCommand.LMPOP:
            case ValkeyCommand.LPOP:
            case ValkeyCommand.LPUSH:
            case ValkeyCommand.LPUSHX:
            case ValkeyCommand.LREM:
            case ValkeyCommand.LSET:
            case ValkeyCommand.LTRIM:
            case ValkeyCommand.MIGRATE:
            case ValkeyCommand.MOVE:
            case ValkeyCommand.MSET:
            case ValkeyCommand.MSETNX:
            case ValkeyCommand.PERSIST:
            case ValkeyCommand.PEXPIRE:
            case ValkeyCommand.PEXPIREAT:
            case ValkeyCommand.PFADD:
            case ValkeyCommand.PFMERGE:
            case ValkeyCommand.PSETEX:
            case ValkeyCommand.RENAME:
            case ValkeyCommand.RENAMENX:
            case ValkeyCommand.RESTORE:
            case ValkeyCommand.RPOP:
            case ValkeyCommand.RPOPLPUSH:
            case ValkeyCommand.RPUSH:
            case ValkeyCommand.RPUSHX:
            case ValkeyCommand.SADD:
            case ValkeyCommand.SDIFFSTORE:
            case ValkeyCommand.SET:
            case ValkeyCommand.SETBIT:
            case ValkeyCommand.SETEX:
            case ValkeyCommand.SETNX:
            case ValkeyCommand.SETRANGE:
            case ValkeyCommand.SINTERSTORE:
            case ValkeyCommand.SMOVE:
            case ValkeyCommand.SPOP:
            case ValkeyCommand.SREM:
            case ValkeyCommand.SUNIONSTORE:
            case ValkeyCommand.SWAPDB:
            case ValkeyCommand.TOUCH:
            case ValkeyCommand.UNLINK:
            case ValkeyCommand.XAUTOCLAIM:
            case ValkeyCommand.ZADD:
            case ValkeyCommand.ZDIFFSTORE:
            case ValkeyCommand.ZINTERSTORE:
            case ValkeyCommand.ZINCRBY:
            case ValkeyCommand.ZMPOP:
            case ValkeyCommand.ZPOPMAX:
            case ValkeyCommand.ZPOPMIN:
            case ValkeyCommand.ZRANGESTORE:
            case ValkeyCommand.ZREM:
            case ValkeyCommand.ZREMRANGEBYLEX:
            case ValkeyCommand.ZREMRANGEBYRANK:
            case ValkeyCommand.ZREMRANGEBYSCORE:
            case ValkeyCommand.ZUNIONSTORE:
                return true;
            // Commands that can be issued anywhere
            case ValkeyCommand.NONE:
            case ValkeyCommand.ASKING:
            case ValkeyCommand.AUTH:
            case ValkeyCommand.BGREWRITEAOF:
            case ValkeyCommand.BGSAVE:
            case ValkeyCommand.BITCOUNT:
            case ValkeyCommand.BITPOS:
            case ValkeyCommand.CLIENT:
            case ValkeyCommand.CLUSTER:
            case ValkeyCommand.COMMAND:
            case ValkeyCommand.CONFIG:
            case ValkeyCommand.DBSIZE:
            case ValkeyCommand.DEBUG:
            case ValkeyCommand.DISCARD:
            case ValkeyCommand.DUMP:
            case ValkeyCommand.ECHO:
            case ValkeyCommand.EVAL:
            case ValkeyCommand.EVALSHA:
            case ValkeyCommand.EVAL_RO:
            case ValkeyCommand.EVALSHA_RO:
            case ValkeyCommand.EXEC:
            case ValkeyCommand.EXISTS:
            case ValkeyCommand.EXPIRETIME:
            case ValkeyCommand.GEODIST:
            case ValkeyCommand.GEOHASH:
            case ValkeyCommand.GEOPOS:
            case ValkeyCommand.GEORADIUS:
            case ValkeyCommand.GEORADIUSBYMEMBER:
            case ValkeyCommand.GEOSEARCH:
            case ValkeyCommand.GET:
            case ValkeyCommand.GETBIT:
            case ValkeyCommand.GETRANGE:
            case ValkeyCommand.HELLO:
            case ValkeyCommand.HEXISTS:
            case ValkeyCommand.HEXPIRETIME:
            case ValkeyCommand.HGET:
            case ValkeyCommand.HGETALL:
            case ValkeyCommand.HKEYS:
            case ValkeyCommand.HLEN:
            case ValkeyCommand.HMGET:
            case ValkeyCommand.HPEXPIRETIME:
            case ValkeyCommand.HPTTL:
            case ValkeyCommand.HRANDFIELD:
            case ValkeyCommand.HSCAN:
            case ValkeyCommand.HSTRLEN:
            case ValkeyCommand.HVALS:
            case ValkeyCommand.INFO:
            case ValkeyCommand.KEYS:
            case ValkeyCommand.LASTSAVE:
            case ValkeyCommand.LATENCY:
            case ValkeyCommand.LCS:
            case ValkeyCommand.LINDEX:
            case ValkeyCommand.LLEN:
            case ValkeyCommand.LPOS:
            case ValkeyCommand.LRANGE:
            case ValkeyCommand.MEMORY:
            case ValkeyCommand.MGET:
            case ValkeyCommand.MONITOR:
            case ValkeyCommand.MULTI:
            case ValkeyCommand.OBJECT:
            case ValkeyCommand.PEXPIRETIME:
            case ValkeyCommand.PFCOUNT:
            case ValkeyCommand.PING:
            case ValkeyCommand.PSUBSCRIBE:
            case ValkeyCommand.PTTL:
            case ValkeyCommand.PUBLISH:
            case ValkeyCommand.PUBSUB:
            case ValkeyCommand.PUNSUBSCRIBE:
            case ValkeyCommand.QUIT:
            case ValkeyCommand.RANDOMKEY:
            case ValkeyCommand.READONLY:
            case ValkeyCommand.READWRITE:
            case ValkeyCommand.REPLICAOF:
            case ValkeyCommand.ROLE:
            case ValkeyCommand.SAVE:
            case ValkeyCommand.SCAN:
            case ValkeyCommand.SCARD:
            case ValkeyCommand.SCRIPT:
            case ValkeyCommand.SDIFF:
            case ValkeyCommand.SELECT:
            case ValkeyCommand.SENTINEL:
            case ValkeyCommand.SHUTDOWN:
            case ValkeyCommand.SINTER:
            case ValkeyCommand.SINTERCARD:
            case ValkeyCommand.SISMEMBER:
            case ValkeyCommand.SLAVEOF:
            case ValkeyCommand.SLOWLOG:
            case ValkeyCommand.SMEMBERS:
            case ValkeyCommand.SMISMEMBER:
            case ValkeyCommand.SORT_RO:
            case ValkeyCommand.SPUBLISH:
            case ValkeyCommand.SRANDMEMBER:
            case ValkeyCommand.SSUBSCRIBE:
            case ValkeyCommand.STRLEN:
            case ValkeyCommand.SUBSCRIBE:
            case ValkeyCommand.SUNION:
            case ValkeyCommand.SUNSUBSCRIBE:
            case ValkeyCommand.SSCAN:
            case ValkeyCommand.SYNC:
            case ValkeyCommand.TIME:
            case ValkeyCommand.TTL:
            case ValkeyCommand.TYPE:
            case ValkeyCommand.UNSUBSCRIBE:
            case ValkeyCommand.UNWATCH:
            case ValkeyCommand.WATCH:
            case ValkeyCommand.XINFO:
            case ValkeyCommand.XLEN:
            case ValkeyCommand.XPENDING:
            case ValkeyCommand.XRANGE:
            case ValkeyCommand.XREAD:
            case ValkeyCommand.XREVRANGE:
            case ValkeyCommand.ZCARD:
            case ValkeyCommand.ZCOUNT:
            case ValkeyCommand.ZDIFF:
            case ValkeyCommand.ZINTER:
            case ValkeyCommand.ZINTERCARD:
            case ValkeyCommand.ZLEXCOUNT:
            case ValkeyCommand.ZMSCORE:
            case ValkeyCommand.ZRANDMEMBER:
            case ValkeyCommand.ZRANGE:
            case ValkeyCommand.ZRANGEBYLEX:
            case ValkeyCommand.ZRANGEBYSCORE:
            case ValkeyCommand.ZRANK:
            case ValkeyCommand.ZREVRANGE:
            case ValkeyCommand.ZREVRANGEBYLEX:
            case ValkeyCommand.ZREVRANGEBYSCORE:
            case ValkeyCommand.ZREVRANK:
            case ValkeyCommand.ZSCAN:
            case ValkeyCommand.ZSCORE:
            case ValkeyCommand.ZUNION:
            case ValkeyCommand.UNKNOWN:
            // Writable commands, but allowed for the writable-replicas scenario
            case ValkeyCommand.COPY:
            case ValkeyCommand.GEOADD:
            case ValkeyCommand.SORT:
            case ValkeyCommand.XACK:
            case ValkeyCommand.XADD:
            case ValkeyCommand.XCLAIM:
            case ValkeyCommand.XDEL:
            case ValkeyCommand.XGROUP:
            case ValkeyCommand.XREADGROUP:
            case ValkeyCommand.XTRIM:
                return false;
            default:
                throw new ArgumentOutOfRangeException(nameof(command), $"Every ValkeyCommand must be defined in Message.IsPrimaryOnly, unknown command '{command}' encountered.");
        }
    }
}
