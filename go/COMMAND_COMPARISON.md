# Command Comparison: Valkey/Redis Commands vs valkey-glide (Go Client)

This document provides a detailed comparison between all Valkey/Redis commands and what valkey-glide implements in its Go client.

---

## SUPPORTED COMMANDS

Commands that valkey-glide implements with their corresponding method names.

### String Commands (17/21 supported)

| Command | valkey-glide Support | Method Name |
|---------|---------------------|-------------|
| SET | ✅ | `Set`, `SetWithOptions` |
| GET | ✅ | `Get` |
| GETEX | ✅ | `GetEx`, `GetExWithOptions` |
| MSET | ✅ | `MSet` |
| MGET | ✅ | `MGet` |
| MSETNX | ✅ | `MSetNX` |
| INCR | ✅ | `Incr` |
| INCRBY | ✅ | `IncrBy` |
| INCRBYFLOAT | ✅ | `IncrByFloat` |
| DECR | ✅ | `Decr` |
| DECRBY | ✅ | `DecrBy` |
| STRLEN | ✅ | `Strlen` |
| SETRANGE | ✅ | `SetRange` |
| GETRANGE | ✅ | `GetRange` |
| APPEND | ✅ | `Append` |
| GETDEL | ✅ | `GetDel` |
| LCS | ✅ | `LCS`, `LCSLen`, `LCSWithOptions` |

#### String Commands Not Implemented

| Command | Status | Notes |
|---------|--------|-------|
| SETEX | ❌ | Use `SetWithOptions` with expiration |
| SETNX | ❌ | Use `SetWithOptions` with NX option |
| PSETEX | ❌ | Use `SetWithOptions` with PX option |
| GETSET | ❌ | Deprecated, use `SET ... GET` |
| SUBSTR | ❌ | Deprecated alias for GETRANGE |

---

### Hash Commands (22/16 in standard list + extras)

| Command | valkey-glide Support | Method Name |
|---------|---------------------|-------------|
| HGET | ✅ | `HGet` |
| HSET | ✅ | `HSet` |
| HMGET | ✅ | `HMGet` |
| HMSET | ✅ | `HSet` (same behavior) |
| HDEL | ✅ | `HDel` |
| HLEN | ✅ | `HLen` |
| HKEYS | ✅ | `HKeys` |
| HVALS | ✅ | `HVals` |
| HGETALL | ✅ | `HGetAll` |
| HEXISTS | ✅ | `HExists` |
| HINCRBY | ✅ | `HIncrBy` |
| HINCRBYFLOAT | ✅ | `HIncrByFloat` |
| HSETNX | ✅ | `HSetNX` |
| HSTRLEN | ✅ | `HStrLen` |
| HSCAN | ✅ | `HScan`, `HScanWithOptions` |
| HRANDFIELD | ✅ | `HRandField`, `HRandFieldWithCount`, `HRandFieldWithCountWithValues` |

#### Bonus Hash Commands (Valkey 8.0+)

| Command | valkey-glide Support | Method Name |
|---------|---------------------|-------------|
| HSETEX | ✅ | `HSetEx` |
| HGETEX | ✅ | `HGetEx` |
| HEXPIRE | ✅ | `HExpire` |
| HEXPIREAT | ✅ | `HExpireAt` |
| HPEXPIRE | ✅ | `HPExpire` |
| HPEXPIREAT | ✅ | `HPExpireAt` |
| HPERSIST | ✅ | `HPersist` |
| HTTL | ✅ | `HTtl` |
| HPTTL | ✅ | `HPTtl` |
| HEXPIRETIME | ✅ | `HExpireTime` |
| HPEXPIRETIME | ✅ | `HPExpireTime` |

---

### List Commands (20/22 supported)

| Command | valkey-glide Support | Method Name |
|---------|---------------------|-------------|
| LPUSH | ✅ | `LPush` |
| RPUSH | ✅ | `RPush` |
| LPOP | ✅ | `LPop`, `LPopCount` |
| RPOP | ✅ | `RPop`, `RPopCount` |
| LRANGE | ✅ | `LRange` |
| LINDEX | ✅ | `LIndex` |
| LSET | ✅ | `LSet` |
| LLEN | ✅ | `LLen` |
| LINSERT | ✅ | `LInsert` |
| LREM | ✅ | `LRem` |
| LTRIM | ✅ | `LTrim` |
| LPOS | ✅ | `LPos`, `LPosWithOptions`, `LPosCount` |
| LMOVE | ✅ | `LMove` |
| BLMOVE | ✅ | `BLMove` |
| LMPOP | ✅ | `LMPop`, `LMPopCount` |
| BLMPOP | ✅ | `BLMPop`, `BLMPopCount` |
| BLPOP | ✅ | `BLPop` |
| BRPOP | ✅ | `BRPop` |
| LPUSHX | ✅ | `LPushX` |
| RPUSHX | ✅ | `RPushX` |

#### List Commands Not Implemented

| Command | Status | Notes |
|---------|--------|-------|
| RPOPLPUSH | ❌ | Deprecated, use `LMove` |
| BRPOPLPUSH | ❌ | Deprecated, use `BLMove` |

---

### Set Commands (17/17 fully supported)

| Command | valkey-glide Support | Method Name |
|---------|---------------------|-------------|
| SADD | ✅ | `SAdd` |
| SREM | ✅ | `SRem` |
| SMEMBERS | ✅ | `SMembers` |
| SCARD | ✅ | `SCard` |
| SISMEMBER | ✅ | `SIsMember` |
| SMISMEMBER | ✅ | `SMIsMember` |
| SDIFF | ✅ | `SDiff` |
| SDIFFSTORE | ✅ | `SDiffStore` |
| SINTER | ✅ | `SInter` |
| SINTERSTORE | ✅ | `SInterStore` |
| SINTERCARD | ✅ | `SInterCard`, `SInterCardLimit` |
| SUNION | ✅ | `SUnion` |
| SUNIONSTORE | ✅ | `SUnionStore` |
| SRANDMEMBER | ✅ | `SRandMember`, `SRandMemberCount` |
| SPOP | ✅ | `SPop`, `SPopCount` |
| SMOVE | ✅ | `SMove` |
| SSCAN | ✅ | `SScan`, `SScanWithOptions` |

---

### Sorted Set Commands (33/35 supported)

| Command | valkey-glide Support | Method Name |
|---------|---------------------|-------------|
| ZADD | ✅ | `ZAdd`, `ZAddWithOptions`, `ZAddIncr` |
| ZREM | ✅ | `ZRem` |
| ZCARD | ✅ | `ZCard` |
| ZSCORE | ✅ | `ZScore` |
| ZRANK | ✅ | `ZRank`, `ZRankWithScore` |
| ZREVRANK | ✅ | `ZRevRank`, `ZRevRankWithScore` |
| ZRANGE | ✅ | `ZRange`, `ZRangeWithScores` |
| ZCOUNT | ✅ | `ZCount` |
| ZLEXCOUNT | ✅ | `ZLexCount` |
| ZINCRBY | ✅ | `ZIncrBy` |
| ZPOPMIN | ✅ | `ZPopMin`, `ZPopMinWithOptions` |
| ZPOPMAX | ✅ | `ZPopMax`, `ZPopMaxWithOptions` |
| BZPOPMIN | ✅ | `BZPopMin` |
| BZPOPMAX | ✅ | `BZPopMax` |
| ZMPOP | ✅ | `ZMPop`, `ZMPopWithOptions` |
| BZMPOP | ✅ | `BZMPop`, `BZMPopWithOptions` |
| ZRANGESTORE | ✅ | `ZRangeStore` |
| ZINTERSTORE | ✅ | `ZInterStore`, `ZInterStoreWithOptions` |
| ZUNIONSTORE | ✅ | `ZUnionStore`, `ZUnionStoreWithOptions` |
| ZDIFF | ✅ | `ZDiff`, `ZDiffWithScores` |
| ZDIFFSTORE | ✅ | `ZDiffStore` |
| ZINTER | ✅ | `ZInter`, `ZInterWithScores` |
| ZUNION | ✅ | `ZUnion`, `ZUnionWithScores` |
| ZINTERCARD | ✅ | `ZInterCard`, `ZInterCardWithOptions` |
| ZMSCORE | ✅ | `ZMScore` |
| ZRANDMEMBER | ✅ | `ZRandMember`, `ZRandMemberWithCount`, `ZRandMemberWithCountWithScores` |
| ZSCAN | ✅ | `ZScan`, `ZScanWithOptions` |
| ZREMRANGEBYRANK | ✅ | `ZRemRangeByRank` |
| ZREMRANGEBYSCORE | ✅ | `ZRemRangeByScore` |
| ZREMRANGEBYLEX | ✅ | `ZRemRangeByLex` |

#### Sorted Set Commands Not Implemented (Deprecated)

| Command | Status | Notes |
|---------|--------|-------|
| ZREVRANGE | ❌ | Use `ZRange` with REV option |
| ZRANGEBYSCORE | ❌ | Use `ZRange` with BYSCORE |
| ZREVRANGEBYSCORE | ❌ | Use `ZRange` with BYSCORE + REV |
| ZRANGEBYLEX | ❌ | Use `ZRange` with BYLEX |
| ZREVRANGEBYLEX | ❌ | Use `ZRange` with BYLEX + REV |

---

### Stream Commands (20/25 supported)

| Command | valkey-glide Support | Method Name |
|---------|---------------------|-------------|
| XADD | ✅ | `XAdd`, `XAddWithOptions` |
| XREAD | ✅ | `XRead`, `XReadWithOptions` |
| XREADGROUP | ✅ | `XReadGroup`, `XReadGroupWithOptions` |
| XLEN | ✅ | `XLen` |
| XRANGE | ✅ | `XRange`, `XRangeWithOptions` |
| XREVRANGE | ✅ | `XRevRange`, `XRevRangeWithOptions` |
| XACK | ✅ | `XAck` |
| XCLAIM | ✅ | `XClaim`, `XClaimWithOptions`, `XClaimJustId` |
| XAUTOCLAIM | ✅ | `XAutoClaim`, `XAutoClaimWithOptions`, `XAutoClaimJustId` |
| XINFO STREAM | ✅ | `XInfoStream`, `XInfoStreamFullWithOptions` |
| XINFO GROUPS | ✅ | `XInfoGroups` |
| XINFO CONSUMERS | ✅ | `XInfoConsumers` |
| XGROUP CREATE | ✅ | `XGroupCreate`, `XGroupCreateWithOptions` |
| XGROUP CREATECONSUMER | ✅ | `XGroupCreateConsumer` |
| XGROUP DESTROY | ✅ | `XGroupDestroy` |
| XGROUP SETID | ✅ | `XGroupSetId`, `XGroupSetIdWithOptions` |
| XGROUP DELCONSUMER | ✅ | `XGroupDelConsumer` |
| XDEL | ✅ | `XDel` |
| XTRIM | ✅ | `XTrim` |
| XPENDING | ✅ | `XPending`, `XPendingWithOptions` |

#### Stream Commands Not Implemented

| Command | Status | Notes |
|---------|--------|-------|
| XSETID | ❌ | Not implemented |
| XINFO HELP | ❌ | Internal/debug |
| XGROUP HELP | ❌ | Internal/debug |

---

### HyperLogLog Commands (3/5 supported)

| Command | valkey-glide Support | Method Name |
|---------|---------------------|-------------|
| PFADD | ✅ | `PfAdd` |
| PFCOUNT | ✅ | `PfCount` |
| PFMERGE | ✅ | `PfMerge` |

#### HyperLogLog Commands Not Implemented

| Command | Status | Notes |
|---------|--------|-------|
| PFDEBUG | ❌ | Internal/debug |
| PFSELFTEST | ❌ | Internal/debug |

---

### Geo Commands (8/10 supported)

| Command | valkey-glide Support | Method Name |
|---------|---------------------|-------------|
| GEOADD | ✅ | `GeoAdd`, `GeoAddWithOptions` |
| GEOPOS | ✅ | `GeoPos` |
| GEODIST | ✅ | `GeoDist`, `GeoDistWithUnit` |
| GEOHASH | ✅ | `GeoHash` |
| GEOSEARCH | ✅ | `GeoSearch`, `GeoSearchWithInfoOptions`, `GeoSearchWithResultOptions`, `GeoSearchWithFullOptions` |
| GEOSEARCHSTORE | ✅ | `GeoSearchStore`, `GeoSearchStoreWithInfoOptions`, `GeoSearchStoreWithResultOptions`, `GeoSearchStoreWithFullOptions` |

#### Geo Commands Not Implemented (Deprecated)

| Command | Status | Notes |
|---------|--------|-------|
| GEORADIUS | ❌ | Deprecated, use GEOSEARCH |
| GEORADIUSBYMEMBER | ❌ | Deprecated, use GEOSEARCH |
| GEORADIUS_RO | ❌ | Deprecated, use GEOSEARCH |
| GEORADIUSBYMEMBER_RO | ❌ | Deprecated, use GEOSEARCH |

---

### Bitmap Commands (7/7 fully supported)

| Command | valkey-glide Support | Method Name |
|---------|---------------------|-------------|
| SETBIT | ✅ | `SetBit` |
| GETBIT | ✅ | `GetBit` |
| BITCOUNT | ✅ | `BitCount`, `BitCountWithOptions` |
| BITOP | ✅ | `BitOp` |
| BITPOS | ✅ | `BitPos`, `BitPosWithOptions` |
| BITFIELD | ✅ | `BitField` |
| BITFIELD_RO | ✅ | `BitFieldRO` |

---

### Generic/Key Commands (30/38 supported)

| Command | valkey-glide Support | Method Name |
|---------|---------------------|-------------|
| DEL | ✅ | `Del` |
| EXISTS | ✅ | `Exists` |
| EXPIRE | ✅ | `Expire`, `ExpireWithOptions` |
| EXPIREAT | ✅ | `ExpireAt`, `ExpireAtWithOptions` |
| EXPIRETIME | ✅ | `ExpireTime` |
| PEXPIRE | ✅ | `PExpire`, `PExpireWithOptions` |
| PEXPIREAT | ✅ | `PExpireAt`, `PExpireAtWithOptions` |
| PEXPIRETIME | ✅ | `PExpireTime` |
| TTL | ✅ | `TTL` |
| PTTL | ✅ | `PTTL` |
| PERSIST | ✅ | `Persist` |
| TYPE | ✅ | `Type` |
| UNLINK | ✅ | `Unlink` |
| TOUCH | ✅ | `Touch` |
| RENAME | ✅ | `Rename` |
| RENAMENX | ✅ | `RenameNX` |
| COPY | ✅ | `Copy`, `CopyWithOptions` |
| DUMP | ✅ | `Dump` |
| RESTORE | ✅ | `Restore`, `RestoreWithOptions` |
| OBJECT ENCODING | ✅ | `ObjectEncoding` |
| OBJECT IDLETIME | ✅ | `ObjectIdleTime` |
| OBJECT FREQ | ✅ | `ObjectFreq` |
| OBJECT REFCOUNT | ✅ | `ObjectRefCount` |
| SORT | ✅ | `Sort`, `SortWithOptions` |
| SORT_RO | ✅ | `SortReadOnly`, `SortReadOnlyWithOptions` |
| SCAN | ✅ | `Scan`, `ScanWithOptions` |
| RANDOMKEY | ✅ | `RandomKey` |
| MOVE | ✅ | `Move` (standalone only) |
| WAIT | ✅ | `Wait` |

#### Generic/Key Commands Not Implemented

| Command | Status | Notes |
|---------|--------|-------|
| KEYS | ❌ | Not implemented (use SCAN instead for production) |
| MIGRATE | ❌ | Not implemented |
| RESTORE-ASKING | ❌ | Internal cluster command |
| OBJECT HELP | ❌ | Internal/debug |
| WAITAOF | ❌ | Not implemented |

---

### Connection Commands (6/28 supported)

| Command | valkey-glide Support | Method Name |
|---------|---------------------|-------------|
| PING | ✅ | `Ping`, `PingWithOptions` |
| ECHO | ✅ | `Echo` |
| CLIENT ID | ✅ | `ClientId` |
| CLIENT GETNAME | ✅ | `ClientGetName` |
| CLIENT SETNAME | ✅ | `ClientSetName` |
| SELECT | ✅ | `Select` (standalone only) |

#### Connection Commands Not Implemented

| Command | Status | Notes |
|---------|--------|-------|
| AUTH | ❌ | Handled by configuration |
| QUIT | ❌ | Use `Close()` |
| HELLO | ❌ | Handled internally |
| RESET | ❌ | Not implemented |
| CLIENT LIST | ❌ | Not implemented |
| CLIENT KILL | ❌ | Not implemented |
| CLIENT PAUSE | ❌ | Not implemented |
| CLIENT UNPAUSE | ❌ | Not implemented |
| CLIENT REPLY | ❌ | Not implemented |
| CLIENT CACHING | ❌ | Not implemented |
| CLIENT NO-EVICT | ❌ | Not implemented |
| CLIENT NO-TOUCH | ❌ | Not implemented |
| CLIENT TRACKINGINFO | ❌ | Not implemented |
| CLIENT GETREDIR | ❌ | Not implemented |
| CLIENT TRACKING | ❌ | Not implemented |
| CLIENT UNBLOCK | ❌ | Not implemented |
| CLIENT INFO | ❌ | Not implemented |
| CLIENT SETINFO | ❌ | Handled internally |

---

### Server Management Commands (17/45+ supported)

| Command | valkey-glide Support | Method Name |
|---------|---------------------|-------------|
| INFO | ✅ | `Info`, `InfoWithOptions` |
| DBSIZE | ✅ | `DBSize` |
| FLUSHDB | ✅ | `FlushDB`, `FlushDBWithOptions` |
| FLUSHALL | ✅ | `FlushAll`, `FlushAllWithOptions` |
| LASTSAVE | ✅ | `LastSave` |
| TIME | ✅ | `Time` |
| CONFIG GET | ✅ | `ConfigGet` |
| CONFIG SET | ✅ | `ConfigSet` |
| CONFIG RESETSTAT | ✅ | `ConfigResetStat` |
| CONFIG REWRITE | ✅ | `ConfigRewrite` |
| LOLWUT | ✅ | `Lolwut`, `LolwutWithOptions` |

#### Server Management Commands Not Implemented

| Command | Status | Notes |
|---------|--------|-------|
| SAVE | ❌ | Not implemented |
| BGSAVE | ❌ | Not implemented |
| BGREWRITEAOF | ❌ | Not implemented |
| SHUTDOWN | ❌ | Not implemented |
| DEBUG | ❌ | Internal/debug |
| SLAVEOF | ❌ | Use replication config |
| REPLICAOF | ❌ | Use replication config |
| SLOWLOG | ❌ | Not implemented |
| MEMORY | ❌ | Not implemented |
| MODULE | ❌ | Not implemented |
| ACL | ❌ | Not implemented |
| MONITOR | ❌ | Not implemented |
| SWAPDB | ❌ | Not implemented |
| FAILOVER | ❌ | Not implemented |
| LATENCY | ❌ | Not implemented |

---

### Scripting Commands (15/22 supported)

| Command | valkey-glide Support | Method Name |
|---------|---------------------|-------------|
| EVAL/EVALSHA | ✅ | `InvokeScript`, `InvokeScriptWithOptions` |
| EVAL_RO/EVALSHA_RO | ✅ | `InvokeScript` with read-only option |
| SCRIPT EXISTS | ✅ | `ScriptExists` |
| SCRIPT FLUSH | ✅ | `ScriptFlush`, `ScriptFlushWithMode` |
| SCRIPT KILL | ✅ | `ScriptKill` |
| SCRIPT LOAD | ✅ | Via `InvokeScript` |
| FCALL | ✅ | `FCall`, `FCallWithKeysAndArgs` |
| FCALL_RO | ✅ | `FCallReadOnly`, `FCallReadOnlyWithKeysAndArgs` |
| FUNCTION LOAD | ✅ | `FunctionLoad` |
| FUNCTION LIST | ✅ | `FunctionList` |
| FUNCTION DELETE | ✅ | `FunctionDelete` |
| FUNCTION FLUSH | ✅ | `FunctionFlush`, `FunctionFlushSync`, `FunctionFlushAsync` |
| FUNCTION KILL | ✅ | `FunctionKill` |
| FUNCTION DUMP | ✅ | `FunctionDump` |
| FUNCTION RESTORE | ✅ | `FunctionRestore`, `FunctionRestoreWithPolicy` |
| FUNCTION STATS | ✅ | `FunctionStats` |

#### Scripting Commands Not Implemented

| Command | Status | Notes |
|---------|--------|-------|
| SCRIPT DEBUG | ❌ | Internal/debug |
| SCRIPT HELP | ❌ | Internal/debug |
| FUNCTION HELP | ❌ | Internal/debug |

---

### Pub/Sub Commands (9/17 supported)

| Command | valkey-glide Support | Method Name |
|---------|---------------------|-------------|
| PUBLISH | ✅ | `Publish` |
| PUBSUB CHANNELS | ✅ | `PubSubChannels`, `PubSubChannelsWithPattern` |
| PUBSUB NUMSUB | ✅ | `PubSubNumSub` |
| PUBSUB NUMPAT | ✅ | `PubSubNumPat` |
| PUBSUB SHARDCHANNELS | ✅ | `PubSubShardChannels` (cluster) |
| PUBSUB SHARDNUMSUB | ✅ | `PubSubShardNumSub` (cluster) |
| SPUBLISH | ✅ | `Publish(..., sharded=true)` (cluster) |

#### Pub/Sub Commands Not Implemented

| Command | Status | Notes |
|---------|--------|-------|
| SUBSCRIBE | ❌ | Use subscription config at client creation |
| UNSUBSCRIBE | ❌ | Use subscription config |
| PSUBSCRIBE | ❌ | Use subscription config |
| PUNSUBSCRIBE | ❌ | Use subscription config |
| SSUBSCRIBE | ❌ | Use subscription config |
| SUNSUBSCRIBE | ❌ | Use subscription config |
| PUBSUB HELP | ❌ | Internal/debug |

---

### Transaction Commands (3/5 supported)

| Command | valkey-glide Support | Method Name |
|---------|---------------------|-------------|
| WATCH | ✅ | `Watch` |
| UNWATCH | ✅ | `Unwatch` |
| MULTI/EXEC | ✅ | `Exec`, `ExecWithOptions` (Batch API) |

#### Transaction Commands Not Implemented

| Command | Status | Notes |
|---------|--------|-------|
| MULTI | ❌ | Use Batch API instead |
| DISCARD | ❌ | Use Batch API instead |

---

### Cluster Commands (0/28 supported directly)

| Command | Status | Notes |
|---------|--------|-------|
| CLUSTER INFO | ❌ | Handled internally |
| CLUSTER NODES | ❌ | Handled internally |
| CLUSTER SLOTS | ❌ | Handled internally |
| CLUSTER SHARDS | ❌ | Handled internally |
| CLUSTER KEYSLOT | ❌ | Handled internally |
| CLUSTER GETKEYSINSLOT | ❌ | Handled internally |
| CLUSTER COUNTKEYSINSLOT | ❌ | Handled internally |
| CLUSTER ADDSLOTS | ❌ | Handled internally |
| CLUSTER ADDSLOTSRANGE | ❌ | Handled internally |
| CLUSTER DELSLOTS | ❌ | Handled internally |
| CLUSTER DELSLOTSRANGE | ❌ | Handled internally |
| CLUSTER FLUSHSLOTS | ❌ | Handled internally |
| CLUSTER FAILOVER | ❌ | Handled internally |
| CLUSTER FORGET | ❌ | Handled internally |
| CLUSTER MEET | ❌ | Handled internally |
| CLUSTER REPLICATE | ❌ | Handled internally |
| CLUSTER REPLICAS | ❌ | Handled internally |
| CLUSTER SLAVES | ❌ | Handled internally |
| CLUSTER RESET | ❌ | Handled internally |
| CLUSTER SAVECONFIG | ❌ | Handled internally |
| CLUSTER SETSLOT | ❌ | Handled internally |
| CLUSTER BUMPEPOCH | ❌ | Handled internally |
| CLUSTER SET-CONFIG-EPOCH | ❌ | Handled internally |
| CLUSTER COUNT-FAILURE-REPORTS | ❌ | Handled internally |
| CLUSTER MYID | ❌ | Handled internally |
| CLUSTER MYSHARDID | ❌ | Handled internally |
| CLUSTER LINKS | ❌ | Handled internally |
| CLUSTER HELP | ❌ | Internal/debug |

**Note:** Cluster topology is managed automatically by valkey-glide. For custom routing, use `CustomCommandWithRoute`.

---

## Internal/Admin Commands NOT Supported

These commands are either internal, deprecated, or handled automatically:

### Internal Commands

| Command | Notes |
|---------|-------|
| SYNC | Internal replication |
| PSYNC | Internal replication |
| REPLCONF | Internal replication |
| ASKING | Internal cluster |
| READONLY | Internal cluster |
| READWRITE | Internal cluster |
| RESTORE-ASKING | Internal cluster |

### Debug Commands

| Command | Notes |
|---------|-------|
| DEBUG | Debug only |
| PFDEBUG | Debug only |
| PFSELFTEST | Debug only |
| SCRIPT DEBUG | Debug only |
| All *|HELP subcommands | Help/documentation |

### Admin Commands

| Command Group | Notes |
|---------------|-------|
| ACL * | Access control - not implemented |
| MODULE * | Module management - not implemented |
| MEMORY * | Memory introspection - not implemented |
| SLOWLOG * | Slow log - not implemented |
| LATENCY * | Latency monitoring - not implemented |

### Deprecated Commands

| Command | Replacement |
|---------|-------------|
| RPOPLPUSH | Use `LMOVE` |
| BRPOPLPUSH | Use `BLMOVE` |
| GEORADIUS | Use `GEOSEARCH` |
| GEORADIUSBYMEMBER | Use `GEOSEARCH` |
| GEORADIUS_RO | Use `GEOSEARCH` |
| GEORADIUSBYMEMBER_RO | Use `GEOSEARCH` |
| ZREVRANGE | Use `ZRANGE` with REV |
| ZRANGEBYSCORE | Use `ZRANGE` with BYSCORE |
| ZREVRANGEBYSCORE | Use `ZRANGE` with BYSCORE + REV |
| ZRANGEBYLEX | Use `ZRANGE` with BYLEX |
| ZREVRANGEBYLEX | Use `ZRANGE` with BYLEX + REV |
| SETEX | Use `SET` with EX option |
| PSETEX | Use `SET` with PX option |
| SETNX | Use `SET` with NX option |
| GETSET | Use `SET` with GET option |
| HMSET | Use `HSET` |
| SUBSTR | Use `GETRANGE` |

---

## Summary

| Category | Total Commands | Supported | Coverage |
|----------|----------------|-----------|----------|
| **String** | 21 | 17 | ~81% |
| **Hash** | 16 | 22+ | 100%+ |
| **List** | 22 | 20 | ~91% |
| **Set** | 17 | 17 | 100% |
| **Sorted Set** | 35 | 33 | ~94% |
| **Stream** | 25 | 20 | ~80% |
| **HyperLogLog** | 5 | 3 | 60% |
| **Geo** | 10 | 8 | 80% |
| **Bitmap** | 7 | 7 | 100% |
| **Generic/Key** | 38 | 30 | ~79% |
| **Connection** | 28 | 6 | ~21% |
| **Server** | 45+ | 17 | ~38% |
| **Scripting** | 22 | 15 | ~68% |
| **Pub/Sub** | 17 | 9 | ~53% |
| **Transaction** | 5 | 3 | 60% |
| **Cluster** | 28 | 0 | 0% (internal) |

**Total Data Commands Coverage: ~85-90%** of commonly used commands are supported.

---

## Key Observations

1. **All primary data operations** (CRUD for all data types) are fully supported
2. **Deprecated commands** are intentionally not implemented - use modern equivalents
3. **Admin/monitoring commands** have limited support - focus is on client operations
4. **Cluster management** is fully automated - no manual cluster commands needed
5. **Subscription commands** (SUBSCRIBE, etc.) are handled via configuration, not method calls
6. **CustomCommand** is available for any unsupported command via `CustomCommand(ctx, []string{...})`

---

## Using Unsupported Commands

For any command not directly supported, you can use the `CustomCommand` method:

```go
// Standalone client
result, err := client.CustomCommand(ctx, []string{"COMMAND", "ARG1", "ARG2"})

// Cluster client
result, err := clusterClient.CustomCommand(ctx, []string{"COMMAND", "ARG1", "ARG2"})

// Cluster client with specific routing
result, err := clusterClient.CustomCommandWithRoute(ctx, []string{"COMMAND", "ARG1", "ARG2"}, route)
```

---

*Generated from valkey-glide Go client source code analysis*
