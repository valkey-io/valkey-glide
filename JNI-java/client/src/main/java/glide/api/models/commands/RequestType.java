/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

/**
 * Constants for command types to maintain compatibility with UDS implementation tests.
 * This class provides simple string constants that match the protobuf RequestType used in UDS.
 * 
 * By using simple strings, we eliminate the need for an enum and provide direct compatibility
 * with the expected test patterns.
 */
public final class RequestType {
    
    // Connection management commands
    public static final String Ping = "PING";
    public static final String Echo = "ECHO";
    public static final String Select = "SELECT";
    
    // Key management commands
    public static final String Del = "DEL";
    public static final String Exists = "EXISTS";
    public static final String EXPIRE = "EXPIRE";
    public static final String EXPIREAT = "EXPIREAT";
    public static final String TTL = "TTL";
    public static final String PEXPIRE = "PEXPIRE";
    public static final String PEXPIREAT = "PEXPIREAT";
    public static final String PTTL = "PTTL";
    public static final String ExpireTime = "EXPIRETIME";
    public static final String PExpireTime = "PEXPIRETIME";
    public static final String Persist = "PERSIST";
    public static final String Type = "TYPE";
    public static final String Rename = "RENAME";
    public static final String RenameNX = "RENAMENX";
    public static final String Touch = "TOUCH";
    public static final String Move = "MOVE";
    public static final String RandomKey = "RANDOMKEY";
    public static final String Scan = "SCAN";
    public static final String UNLINK = "UNLINK";
    
    // String commands  
    public static final String GET = "GET";
    public static final String SET = "SET";
    public static final String MGet = "MGET";
    public static final String MSet = "MSET";
    public static final String MSetNX = "MSETNX";
    public static final String Append = "APPEND";
    public static final String GetRange = "GETRANGE";
    public static final String SetRange = "SETRANGE";
    public static final String Strlen = "STRLEN";
    public static final String GETDEL = "GETDEL";
    public static final String GETEX = "GETEX";
    public static final String Incr = "INCR";
    public static final String IncrBy = "INCRBY";
    public static final String IncrByFloat = "INCRBYFLOAT";
    public static final String Decr = "DECR";
    public static final String DecrBy = "DECRBY";
    public static final String LCS = "LCS";
    
    // Hash commands
    public static final String HGet = "HGET";
    public static final String HSet = "HSET";
    public static final String HDel = "HDEL";
    public static final String HExists = "HEXISTS";
    public static final String HGetAll = "HGETALL";
    public static final String HIncrBy = "HINCRBY";
    public static final String HIncrByFloat = "HINCRBYFLOAT";
    public static final String HKeys = "HKEYS";
    public static final String HLen = "HLEN";
    public static final String HMGet = "HMGET";
    public static final String HRandField = "HRANDFIELD";
    public static final String HScan = "HSCAN";
    public static final String HSetNX = "HSETNX";
    public static final String HStrlen = "HSTRLEN";
    public static final String HVals = "HVALS";
    
    // Hash field expiration commands (Valkey 9.0+)
    public static final String HSetEx = "HSETEX";
    public static final String HGetEx = "HGETEX";
    public static final String HExpire = "HEXPIRE";
    public static final String HPExpire = "HPEXPIRE";
    public static final String HPersist = "HPERSIST";
    public static final String HExpireAt = "HEXPIREAT";
    public static final String HPExpireAt = "HPEXPIREAT";
    public static final String HTtl = "HTTL";
    public static final String HPTtl = "HPTTL";
    public static final String HExpireTime = "HEXPIRETIME";
    public static final String HPExpireTime = "HPEXPIRETIME";
    
    // List commands
    public static final String BLMove = "BLMOVE";
    public static final String BLMPop = "BLMPOP";
    public static final String BLPop = "BLPOP";
    public static final String BRPop = "BRPOP";
    public static final String LIndex = "LINDEX";
    public static final String LInsert = "LINSERT";
    public static final String LLen = "LLEN";
    public static final String LMove = "LMOVE";
    public static final String LPop = "LPOP";
    public static final String LPos = "LPOS";
    public static final String LMPop = "LMPOP";
    public static final String LPush = "LPUSH";
    public static final String LPushX = "LPUSHX";
    public static final String LRange = "LRANGE";
    public static final String LRem = "LREM";
    public static final String LSet = "LSET";
    public static final String LTrim = "LTRIM";
    public static final String RPop = "RPOP";
    public static final String RPush = "RPUSH";
    public static final String RPushX = "RPUSHX";
    
    // Set commands
    public static final String SAdd = "SADD";
    public static final String SCard = "SCARD";
    public static final String SDiff = "SDIFF";
    public static final String SDiffStore = "SDIFFSTORE";
    public static final String SInter = "SINTER";
    public static final String SInterStore = "SINTERSTORE";
    public static final String SIsMember = "SISMEMBER";
    public static final String SMembers = "SMEMBERS";
    public static final String SMIsMember = "SMISMEMBER";
    public static final String SMove = "SMOVE";
    public static final String SPop = "SPOP";
    public static final String SRandMember = "SRANDMEMBER";
    public static final String SRem = "SREM";
    public static final String SScan = "SSCAN";
    public static final String SUnion = "SUNION";
    public static final String SUnionStore = "SUNIONSTORE";
    public static final String SInterCard = "SINTERCARD";
    
    // Sorted set commands
    public static final String ZAdd = "ZADD";
    public static final String ZCard = "ZCARD";
    public static final String ZCount = "ZCOUNT";
    public static final String ZDiff = "ZDIFF";
    public static final String ZDiffStore = "ZDIFFSTORE";
    public static final String ZIncrBy = "ZINCRBY";
    public static final String ZInter = "ZINTER";
    public static final String ZInterCard = "ZINTERCARD";
    public static final String ZInterStore = "ZINTERSTORE";
    public static final String ZLexCount = "ZLEXCOUNT";
    public static final String ZMScore = "ZMSCORE";
    public static final String ZPopMax = "ZPOPMAX";
    public static final String ZPopMin = "ZPOPMIN";
    public static final String ZRandMember = "ZRANDMEMBER";
    public static final String ZRange = "ZRANGE";
    public static final String ZRangeStore = "ZRANGESTORE";
    public static final String ZRangeByLex = "ZRANGEBYLEX";
    public static final String ZRangeByScore = "ZRANGEBYSCORE";
    public static final String ZRank = "ZRANK";
    public static final String ZRem = "ZREM";
    public static final String ZRemRangeByLex = "ZREMRANGEBYLEX";
    public static final String ZRemRangeByRank = "ZREMRANGEBYRANK";
    public static final String ZRemRangeByScore = "ZREMRANGEBYSCORE";
    public static final String ZRevRange = "ZREVRANGE";
    public static final String ZRevRangeByLex = "ZREVRANGEBYLEX";
    public static final String ZRevRangeByScore = "ZREVRANGEBYSCORE";
    public static final String ZRevRank = "ZREVRANK";
    public static final String ZScore = "ZSCORE";
    public static final String ZUnion = "ZUNION";
    public static final String ZUnionStore = "ZUNIONSTORE";
    public static final String BZMPop = "BZMPOP";
    public static final String BZPopMax = "BZPOPMAX";
    public static final String BZPopMin = "BZPOPMIN";
    public static final String ZMPop = "ZMPOP";
    public static final String ZScan = "ZSCAN";
    
    // HyperLogLog commands
    public static final String PfAdd = "PFADD";
    public static final String PfCount = "PFCOUNT";
    public static final String PfMerge = "PFMERGE";
    
    // Server commands
    public static final String Info = "INFO";
    public static final String Time = "TIME";
    public static final String DBSize = "DBSIZE";
    public static final String FlushDB = "FLUSHDB";
    public static final String FlushAll = "FLUSHALL";
    public static final String LastSave = "LASTSAVE";
    public static final String Sort = "SORT";
    public static final String SortReadOnly = "SORT_RO";
    public static final String Wait = "WAIT";
    public static final String ConfigGet = "CONFIG GET";
    public static final String ConfigSet = "CONFIG SET";
    public static final String ConfigResetStat = "CONFIG RESETSTAT";
    public static final String ConfigRewrite = "CONFIG REWRITE";
    public static final String ClientId = "CLIENT ID";
    public static final String ClientGetName = "CLIENT GETNAME";
    
    // Function commands
    public static final String FunctionLoad = "FUNCTION LOAD";
    public static final String FunctionDelete = "FUNCTION DELETE";
    public static final String FunctionFlush = "FUNCTION FLUSH";
    public static final String FunctionList = "FUNCTION LIST";
    public static final String FunctionDump = "FUNCTION DUMP";
    public static final String FunctionRestore = "FUNCTION RESTORE";
    public static final String FunctionKill = "FUNCTION KILL";
    public static final String FunctionStats = "FUNCTION STATS";
    public static final String FCall = "FCALL";
    public static final String FCallReadOnly = "FCALL_RO";
    
    // Script commands
    public static final String ScriptExists = "SCRIPT EXISTS";
    public static final String ScriptFlush = "SCRIPT FLUSH";
    public static final String ScriptKill = "SCRIPT KILL";
    public static final String ScriptLoad = "SCRIPT LOAD";
    public static final String ScriptShow = "SCRIPT SHOW";
    public static final String Eval = "EVAL";
    public static final String EvalSha = "EVALSHA";
    
    // PubSub commands
    public static final String PUBLISH = "PUBLISH";
    public static final String SPublish = "SPUBLISH";
    public static final String PubSubChannels = "PUBSUB CHANNELS";
    public static final String PubSubNumPat = "PUBSUB NUMPAT";
    public static final String PubSubNumSub = "PUBSUB NUMSUB";
    public static final String PubSubShardChannels = "PUBSUB SHARDCHANNELS";
    public static final String PubSubShardNumSub = "PUBSUB SHARDNUMSUB";
    
    // Transaction commands
    public static final String Multi = "MULTI";
    public static final String Exec = "EXEC";
    public static final String Discard = "DISCARD";
    public static final String Watch = "WATCH";
    public static final String UnWatch = "UNWATCH";
    
    // Bit commands
    public static final String BitCount = "BITCOUNT";
    public static final String BitField = "BITFIELD";
    public static final String BitFieldReadOnly = "BITFIELD_RO";
    public static final String BitOp = "BITOP";
    public static final String BitPos = "BITPOS";
    
    // Object commands
    public static final String ObjectEncoding = "OBJECT ENCODING";
    public static final String ObjectFreq = "OBJECT FREQ";
    public static final String ObjectIdleTime = "OBJECT IDLETIME";
    public static final String ObjectRefCount = "OBJECT REFCOUNT";
    
    // Copy and dump commands
    public static final String Copy = "COPY";
    public static final String Dump = "DUMP";
    public static final String Restore = "RESTORE";
    
    // Stream commands
    public static final String XAdd = "XADD";
    public static final String XAck = "XACK";
    public static final String XAutoClaim = "XAUTOCLAIM";
    public static final String XDel = "XDEL";
    public static final String XLen = "XLEN";
    public static final String XRange = "XRANGE";
    public static final String XRevRange = "XREVRANGE";
    public static final String XRead = "XREAD";
    public static final String XReadGroup = "XREADGROUP";
    public static final String XTrim = "XTRIM";
    public static final String XPending = "XPENDING";
    public static final String XGroupCreate = "XGROUP CREATE";
    public static final String XGroupDestroy = "XGROUP DESTROY";
    public static final String XGroupCreateConsumer = "XGROUP CREATECONSUMER";
    public static final String XGroupDelConsumer = "XGROUP DELCONSUMER";
    public static final String XGroupSetId = "XGROUP SETID";
    public static final String XInfoStream = "XINFO STREAM";
    public static final String XInfoConsumers = "XINFO CONSUMERS";
    public static final String XInfoGroups = "XINFO GROUPS";
    
    // Geospatial commands
    public static final String GeoAdd = "GEOADD";
    public static final String GeoSearch = "GEOSEARCH";
    public static final String GeoSearchStore = "GEOSEARCHSTORE";
    public static final String GeoPos = "GEOPOS";
    public static final String GeoDist = "GEODIST";
    public static final String GeoHash = "GEOHASH";
    
    // Misc commands
    public static final String Lolwut = "LOLWUT";
    
    // Custom command for arbitrary commands
    public static final String CustomCommand = "CUSTOM";
    
    
    // Private constructor to prevent instantiation
    private RequestType() {
        throw new UnsupportedOperationException("This class should not be instantiated");
    }
}