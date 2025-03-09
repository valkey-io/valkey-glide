<!-- TOC -->
* [End-User info](#end-user-info)
  * [Roadmap](#roadmap)
  * [Concrete Net9 Client Command Support](#concrete-net9-client-command-support)
* [Developer Info](#developer-info)
  * [Dev-Machine Setup](#dev-machine-setup)
<!-- TOC -->

# End-User info

## Roadmap

1. Hit 1.0 Stable
    - Have every operator implemented
    - Stable and fully covered implementation
    - Have Hosting and DI capabilities ready
    - Have telemetry data ready
    - Support the latest protocol version
2. Hit 2.0 Stable
    - Deep native FFI integration with rust (will reduce memory allocations drastically)
    - Every command should be a callback in rust, letting rust do the string fiddling
    - Drop netstandard support
    - Focus on Performance
    - Stronger focus on .net 9+ features (specifically: Span and co)

## Concrete Net9 Client Command Support

The following is a list of commands, implemented by the glide api itself.
All commands are always available via the normal command api.
The following, hence, is just the list of commands which have immediate, as in:
no additional parsing needed, support.

| Command                                                                                    | Status | Test Coverage |
|--------------------------------------------------------------------------------------------|--------|---------------|
| [ACL](https://valkey.io/commands/acl/)                                                     | ❌      | ❌             |
| [ACL CAT](https://valkey.io/commands/acl-cat/)                                             | ❌      | ❌             |
| [ACL DELUSER](https://valkey.io/commands/acl-deluser/)                                     | ❌      | ❌             |
| [ACL DRYRUN](https://valkey.io/commands/acl-dryrun/)                                       | ❌      | ❌             |
| [ACL GENPASS](https://valkey.io/commands/acl-genpass/)                                     | ❌      | ❌             |
| [ACL GETUSER](https://valkey.io/commands/acl-getuser/)                                     | ❌      | ❌             |
| [ACL HELP](https://valkey.io/commands/acl-help/)                                           | ❌      | ❌             |
| [ACL LIST](https://valkey.io/commands/acl-list/)                                           | ❌      | ❌             |
| [ACL LOAD](https://valkey.io/commands/acl-load/)                                           | ❌      | ❌             |
| [ACL LOG](https://valkey.io/commands/acl-log/)                                             | ❌      | ❌             |
| [ACL SAVE](https://valkey.io/commands/acl-save/)                                           | ❌      | ❌             |
| [ACL SETUSER](https://valkey.io/commands/acl-setuser/)                                     | ❌      | ❌             |
| [ACL USERS](https://valkey.io/commands/acl-users/)                                         | ❌      | ❌             |
| [ACL WHOAMI](https://valkey.io/commands/acl-whoami/)                                       | ❌      | ❌             |
| [APPEND](https://valkey.io/commands/append/)                                               | ❌      | ❌             |
| [ASKING](https://valkey.io/commands/asking/)                                               | ❌      | ❌             |
| [AUTH](https://valkey.io/commands/auth/)                                                   | ❌      | ❌             |
| [BGREWRITEAOF](https://valkey.io/commands/bgrewriteaof/)                                   | ❌      | ❌             |
| [BGSAVE](https://valkey.io/commands/bgsave/)                                               | ❌      | ❌             |
| [BITCOUNT](https://valkey.io/commands/bitcount/)                                           | ❌      | ❌             |
| [BITFIELD](https://valkey.io/commands/bitfield/)                                           | ❌      | ❌             |
| [BITFIELD_RO](https://valkey.io/commands/bitfield_ro/)                                     | ❌      | ❌             |
| [BITOP](https://valkey.io/commands/bitop/)                                                 | ❌      | ❌             |
| [BITPOS](https://valkey.io/commands/bitpos/)                                               | ❌      | ❌             |
| [BLMOVE](https://valkey.io/commands/blmove/)                                               | ❌      | ❌             |
| [BLMPOP](https://valkey.io/commands/blmpop/)                                               | ❌      | ❌             |
| [BLPOP](https://valkey.io/commands/blpop/)                                                 | ❌      | ❌             |
| [BRPOP](https://valkey.io/commands/brpop/)                                                 | ❌      | ❌             |
| [BRPOPLPUSH](https://valkey.io/commands/brpoplpush/)                                       | ❌      | ❌             |
| [BZMPOP](https://valkey.io/commands/bzmpop/)                                               | ❌      | ❌             |
| [BZPOPMAX](https://valkey.io/commands/bzpopmax/)                                           | ❌      | ❌             |
| [BZPOPMIN](https://valkey.io/commands/bzpopmin/)                                           | ❌      | ❌             |
| [CLIENT](https://valkey.io/commands/client/)                                               | ❌      | ❌             |
| [CLIENT CACHING](https://valkey.io/commands/client-caching/)                               | ❌      | ❌             |
| [CLIENT CAPA](https://valkey.io/commands/client-capa/)                                     | ❌      | ❌             |
| [CLIENT GETNAME](https://valkey.io/commands/client-getname/)                               | ❌      | ❌             |
| [CLIENT GETREDIR](https://valkey.io/commands/client-getredir/)                             | ❌      | ❌             |
| [CLIENT HELP](https://valkey.io/commands/client-help/)                                     | ❌      | ❌             |
| [CLIENT ID](https://valkey.io/commands/client-id/)                                         | ❌      | ❌             |
| [CLIENT IMPORT-SOURCE](https://valkey.io/commands/client-import-source/)                   | ❌      | ❌             |
| [CLIENT INFO](https://valkey.io/commands/client-info/)                                     | ❌      | ❌             |
| [CLIENT KILL](https://valkey.io/commands/client-kill/)                                     | ❌      | ❌             |
| [CLIENT LIST](https://valkey.io/commands/client-list/)                                     | ❌      | ❌             |
| [CLIENT NO-EVICT](https://valkey.io/commands/client-no-evict/)                             | ❌      | ❌             |
| [CLIENT NO-TOUCH](https://valkey.io/commands/client-no-touch/)                             | ❌      | ❌             |
| [CLIENT PAUSE](https://valkey.io/commands/client-pause/)                                   | ❌      | ❌             |
| [CLIENT REPLY](https://valkey.io/commands/client-reply/)                                   | ❌      | ❌             |
| [CLIENT SETINFO](https://valkey.io/commands/client-setinfo/)                               | ❌      | ❌             |
| [CLIENT SETNAME](https://valkey.io/commands/client-setname/)                               | ❌      | ❌             |
| [CLIENT TRACKING](https://valkey.io/commands/client-tracking/)                             | ❌      | ❌             |
| [CLIENT TRACKINGINFO](https://valkey.io/commands/client-trackinginfo/)                     | ❌      | ❌             |
| [CLIENT UNBLOCK](https://valkey.io/commands/client-unblock/)                               | ❌      | ❌             |
| [CLIENT UNPAUSE](https://valkey.io/commands/client-unpause/)                               | ❌      | ❌             |
| [CLUSTER](https://valkey.io/commands/cluster/)                                             | ❌      | ❌             |
| [CLUSTER ADDSLOTS](https://valkey.io/commands/cluster-addslots/)                           | ❌      | ❌             |
| [CLUSTER ADDSLOTSRANGE](https://valkey.io/commands/cluster-addslotsrange/)                 | ❌      | ❌             |
| [CLUSTER BUMPEPOCH](https://valkey.io/commands/cluster-bumpepoch/)                         | ❌      | ❌             |
| [CLUSTER COUNT-FAILURE-REPORTS](https://valkey.io/commands/cluster-count-failure-reports/) | ❌      | ❌             |
| [CLUSTER COUNTKEYSINSLOT](https://valkey.io/commands/cluster-countkeysinslot/)             | ❌      | ❌             |
| [CLUSTER DELSLOTS](https://valkey.io/commands/cluster-delslots/)                           | ❌      | ❌             |
| [CLUSTER DELSLOTSRANGE](https://valkey.io/commands/cluster-delslotsrange/)                 | ❌      | ❌             |
| [CLUSTER FAILOVER](https://valkey.io/commands/cluster-failover/)                           | ❌      | ❌             |
| [CLUSTER FLUSHSLOTS](https://valkey.io/commands/cluster-flushslots/)                       | ❌      | ❌             |
| [CLUSTER FORGET](https://valkey.io/commands/cluster-forget/)                               | ❌      | ❌             |
| [CLUSTER GETKEYSINSLOT](https://valkey.io/commands/cluster-getkeysinslot/)                 | ❌      | ❌             |
| [CLUSTER HELP](https://valkey.io/commands/cluster-help/)                                   | ❌      | ❌             |
| [CLUSTER INFO](https://valkey.io/commands/cluster-info/)                                   | ❌      | ❌             |
| [CLUSTER KEYSLOT](https://valkey.io/commands/cluster-keyslot/)                             | ❌      | ❌             |
| [CLUSTER LINKS](https://valkey.io/commands/cluster-links/)                                 | ❌      | ❌             |
| [CLUSTER MEET](https://valkey.io/commands/cluster-meet/)                                   | ❌      | ❌             |
| [CLUSTER MYID](https://valkey.io/commands/cluster-myid/)                                   | ❌      | ❌             |
| [CLUSTER MYSHARDID](https://valkey.io/commands/cluster-myshardid/)                         | ❌      | ❌             |
| [CLUSTER NODES](https://valkey.io/commands/cluster-nodes/)                                 | ❌      | ❌             |
| [CLUSTER REPLICAS](https://valkey.io/commands/cluster-replicas/)                           | ❌      | ❌             |
| [CLUSTER REPLICATE](https://valkey.io/commands/cluster-replicate/)                         | ❌      | ❌             |
| [CLUSTER RESET](https://valkey.io/commands/cluster-reset/)                                 | ❌      | ❌             |
| [CLUSTER SAVECONFIG](https://valkey.io/commands/cluster-saveconfig/)                       | ❌      | ❌             |
| [CLUSTER SET-CONFIG-EPOCH](https://valkey.io/commands/cluster-set-config-epoch/)           | ❌      | ❌             |
| [CLUSTER SETSLOT](https://valkey.io/commands/cluster-setslot/)                             | ❌      | ❌             |
| [CLUSTER SHARDS](https://valkey.io/commands/cluster-shards/)                               | ❌      | ❌             |
| [CLUSTER SLAVES](https://valkey.io/commands/cluster-slaves/)                               | ❌      | ❌             |
| [CLUSTER SLOT-STATS](https://valkey.io/commands/cluster-slot-stats/)                       | ❌      | ❌             |
| [CLUSTER SLOTS](https://valkey.io/commands/cluster-slots/)                                 | ❌      | ❌             |
| [COMMAND](https://valkey.io/commands/command/)                                             | ❌      | ❌             |
| [COMMAND COUNT](https://valkey.io/commands/command-count/)                                 | ❌      | ❌             |
| [COMMAND DOCS](https://valkey.io/commands/command-docs/)                                   | ❌      | ❌             |
| [COMMAND GETKEYS](https://valkey.io/commands/command-getkeys/)                             | ❌      | ❌             |
| [COMMAND GETKEYSANDFLAGS](https://valkey.io/commands/command-getkeysandflags/)             | ❌      | ❌             |
| [COMMAND HELP](https://valkey.io/commands/command-help/)                                   | ❌      | ❌             |
| [COMMAND INFO](https://valkey.io/commands/command-info/)                                   | ❌      | ❌             |
| [COMMAND LIST](https://valkey.io/commands/command-list/)                                   | ❌      | ❌             |
| [CONFIG](https://valkey.io/commands/config/)                                               | ❌      | ❌             |
| [CONFIG GET](https://valkey.io/commands/config-get/)                                       | ❌      | ❌             |
| [CONFIG HELP](https://valkey.io/commands/config-help/)                                     | ❌      | ❌             |
| [CONFIG RESETSTAT](https://valkey.io/commands/config-resetstat/)                           | ❌      | ❌             |
| [CONFIG REWRITE](https://valkey.io/commands/config-rewrite/)                               | ❌      | ❌             |
| [CONFIG SET](https://valkey.io/commands/config-set/)                                       | ❌      | ❌             |
| [COPY](https://valkey.io/commands/copy/)                                                   | ❌      | ❌             |
| [DBSIZE](https://valkey.io/commands/dbsize/)                                               | ❌      | ❌             |
| [DEBUG](https://valkey.io/commands/debug/)                                                 | ❌      | ❌             |
| [DECR](https://valkey.io/commands/decr/)                                                   | ❌      | ❌             |
| [DECRBY](https://valkey.io/commands/decrby/)                                               | ❌      | ❌             |
| [DEL](https://valkey.io/commands/del/)                                                     | ❌      | ❌             |
| [DISCARD](https://valkey.io/commands/discard/)                                             | ❌      | ❌             |
| [DUMP](https://valkey.io/commands/dump/)                                                   | ❌      | ❌             |
| [ECHO](https://valkey.io/commands/echo/)                                                   | ❌      | ❌             |
| [EVAL](https://valkey.io/commands/eval/)                                                   | ❌      | ❌             |
| [EVALSHA](https://valkey.io/commands/evalsha/)                                             | ❌      | ❌             |
| [EVALSHA_RO](https://valkey.io/commands/evalsha_ro/)                                       | ❌      | ❌             |
| [EVAL_RO](https://valkey.io/commands/eval_ro/)                                             | ❌      | ❌             |
| [EXEC](https://valkey.io/commands/exec/)                                                   | ❌      | ❌             |
| [EXISTS](https://valkey.io/commands/exists/)                                               | ❌      | ❌             |
| [EXPIRE](https://valkey.io/commands/expire/)                                               | ❌      | ❌             |
| [EXPIREAT](https://valkey.io/commands/expireat/)                                           | ❌      | ❌             |
| [EXPIRETIME](https://valkey.io/commands/expiretime/)                                       | ❌      | ❌             |
| [FAILOVER](https://valkey.io/commands/failover/)                                           | ❌      | ❌             |
| [FCALL](https://valkey.io/commands/fcall/)                                                 | ❌      | ❌             |
| [FCALL_RO](https://valkey.io/commands/fcall_ro/)                                           | ❌      | ❌             |
| [FLUSHALL](https://valkey.io/commands/flushall/)                                           | ❌      | ❌             |
| [FLUSHDB](https://valkey.io/commands/flushdb/)                                             | ❌      | ❌             |
| [FUNCTION](https://valkey.io/commands/function/)                                           | ❌      | ❌             |
| [FUNCTION DELETE](https://valkey.io/commands/function-delete/)                             | ❌      | ❌             |
| [FUNCTION DUMP](https://valkey.io/commands/function-dump/)                                 | ❌      | ❌             |
| [FUNCTION FLUSH](https://valkey.io/commands/function-flush/)                               | ❌      | ❌             |
| [FUNCTION HELP](https://valkey.io/commands/function-help/)                                 | ❌      | ❌             |
| [FUNCTION KILL](https://valkey.io/commands/function-kill/)                                 | ❌      | ❌             |
| [FUNCTION LIST](https://valkey.io/commands/function-list/)                                 | ❌      | ❌             |
| [FUNCTION LOAD](https://valkey.io/commands/function-load/)                                 | ❌      | ❌             |
| [FUNCTION RESTORE](https://valkey.io/commands/function-restore/)                           | ❌      | ❌             |
| [FUNCTION STATS](https://valkey.io/commands/function-stats/)                               | ❌      | ❌             |
| [GEOADD](https://valkey.io/commands/geoadd/)                                               | ❌      | ❌             |
| [GEODIST](https://valkey.io/commands/geodist/)                                             | ❌      | ❌             |
| [GEOHASH](https://valkey.io/commands/geohash/)                                             | ❌      | ❌             |
| [GEOPOS](https://valkey.io/commands/geopos/)                                               | ❌      | ❌             |
| [GEORADIUS](https://valkey.io/commands/georadius/)                                         | ❌      | ❌             |
| [GEORADIUSBYMEMBER](https://valkey.io/commands/georadiusbymember/)                         | ❌      | ❌             |
| [GEORADIUSBYMEMBER_RO](https://valkey.io/commands/georadiusbymember_ro/)                   | ❌      | ❌             |
| [GEORADIUS_RO](https://valkey.io/commands/georadius_ro/)                                   | ❌      | ❌             |
| [GEOSEARCH](https://valkey.io/commands/geosearch/)                                         | ❌      | ❌             |
| [GEOSEARCHSTORE](https://valkey.io/commands/geosearchstore/)                               | ❌      | ❌             |
| [GET](https://valkey.io/commands/get/)                                                     | ❌      | ❌             |
| [GETBIT](https://valkey.io/commands/getbit/)                                               | ❌      | ❌             |
| [GETDEL](https://valkey.io/commands/getdel/)                                               | ❌      | ❌             |
| [GETEX](https://valkey.io/commands/getex/)                                                 | ❌      | ❌             |
| [GETRANGE](https://valkey.io/commands/getrange/)                                           | ❌      | ❌             |
| [GETSET](https://valkey.io/commands/getset/)                                               | ❌      | ❌             |
| [HDEL](https://valkey.io/commands/hdel/)                                                   | ❌      | ❌             |
| [HELLO](https://valkey.io/commands/hello/)                                                 | ❌      | ❌             |
| [HEXISTS](https://valkey.io/commands/hexists/)                                             | ❌      | ❌             |
| [HGET](https://valkey.io/commands/hget/)                                                   | ❌      | ❌             |
| [HGETALL](https://valkey.io/commands/hgetall/)                                             | ❌      | ❌             |
| [HINCRBY](https://valkey.io/commands/hincrby/)                                             | ❌      | ❌             |
| [HINCRBYFLOAT](https://valkey.io/commands/hincrbyfloat/)                                   | ❌      | ❌             |
| [HKEYS](https://valkey.io/commands/hkeys/)                                                 | ❌      | ❌             |
| [HLEN](https://valkey.io/commands/hlen/)                                                   | ❌      | ❌             |
| [HMGET](https://valkey.io/commands/hmget/)                                                 | ❌      | ❌             |
| [HMSET](https://valkey.io/commands/hmset/)                                                 | ❌      | ❌             |
| [HRANDFIELD](https://valkey.io/commands/hrandfield/)                                       | ❌      | ❌             |
| [HSCAN](https://valkey.io/commands/hscan/)                                                 | ❌      | ❌             |
| [HSET](https://valkey.io/commands/hset/)                                                   | ❌      | ❌             |
| [HSETNX](https://valkey.io/commands/hsetnx/)                                               | ❌      | ❌             |
| [HSTRLEN](https://valkey.io/commands/hstrlen/)                                             | ❌      | ❌             |
| [HVALS](https://valkey.io/commands/hvals/)                                                 | ❌      | ❌             |
| [INCR](https://valkey.io/commands/incr/)                                                   | ❌      | ❌             |
| [INCRBY](https://valkey.io/commands/incrby/)                                               | ❌      | ❌             |
| [INCRBYFLOAT](https://valkey.io/commands/incrbyfloat/)                                     | ❌      | ❌             |
| [INFO](https://valkey.io/commands/info/)                                                   | ❌      | ❌             |
| [KEYS](https://valkey.io/commands/keys/)                                                   | ❌      | ❌             |
| [LASTSAVE](https://valkey.io/commands/lastsave/)                                           | ❌      | ❌             |
| [LATENCY](https://valkey.io/commands/latency/)                                             | ❌      | ❌             |
| [LATENCY DOCTOR](https://valkey.io/commands/latency-doctor/)                               | ❌      | ❌             |
| [LATENCY GRAPH](https://valkey.io/commands/latency-graph/)                                 | ❌      | ❌             |
| [LATENCY HELP](https://valkey.io/commands/latency-help/)                                   | ❌      | ❌             |
| [LATENCY HISTOGRAM](https://valkey.io/commands/latency-histogram/)                         | ❌      | ❌             |
| [LATENCY HISTORY](https://valkey.io/commands/latency-history/)                             | ❌      | ❌             |
| [LATENCY LATEST](https://valkey.io/commands/latency-latest/)                               | ❌      | ❌             |
| [LATENCY RESET](https://valkey.io/commands/latency-reset/)                                 | ❌      | ❌             |
| [LCS](https://valkey.io/commands/lcs/)                                                     | ❌      | ❌             |
| [LINDEX](https://valkey.io/commands/lindex/)                                               | ❌      | ❌             |
| [LINSERT](https://valkey.io/commands/linsert/)                                             | ❌      | ❌             |
| [LLEN](https://valkey.io/commands/llen/)                                                   | ❌      | ❌             |
| [LMOVE](https://valkey.io/commands/lmove/)                                                 | ❌      | ❌             |
| [LMPOP](https://valkey.io/commands/lmpop/)                                                 | ❌      | ❌             |
| [LOLWUT](https://valkey.io/commands/lolwut/)                                               | ❌      | ❌             |
| [LPOP](https://valkey.io/commands/lpop/)                                                   | ❌      | ❌             |
| [LPOS](https://valkey.io/commands/lpos/)                                                   | ❌      | ❌             |
| [LPUSH](https://valkey.io/commands/lpush/)                                                 | ❌      | ❌             |
| [LPUSHX](https://valkey.io/commands/lpushx/)                                               | ❌      | ❌             |
| [LRANGE](https://valkey.io/commands/lrange/)                                               | ❌      | ❌             |
| [LREM](https://valkey.io/commands/lrem/)                                                   | ❌      | ❌             |
| [LSET](https://valkey.io/commands/lset/)                                                   | ❌      | ❌             |
| [LTRIM](https://valkey.io/commands/ltrim/)                                                 | ❌      | ❌             |
| [MEMORY](https://valkey.io/commands/memory/)                                               | ❌      | ❌             |
| [MEMORY DOCTOR](https://valkey.io/commands/memory-doctor/)                                 | ❌      | ❌             |
| [MEMORY HELP](https://valkey.io/commands/memory-help/)                                     | ❌      | ❌             |
| [MEMORY MALLOC-STATS](https://valkey.io/commands/memory-malloc-stats/)                     | ❌      | ❌             |
| [MEMORY PURGE](https://valkey.io/commands/memory-purge/)                                   | ❌      | ❌             |
| [MEMORY STATS](https://valkey.io/commands/memory-stats/)                                   | ❌      | ❌             |
| [MEMORY USAGE](https://valkey.io/commands/memory-usage/)                                   | ❌      | ❌             |
| [MGET](https://valkey.io/commands/mget/)                                                   | ❌      | ❌             |
| [MIGRATE](https://valkey.io/commands/migrate/)                                             | ❌      | ❌             |
| [MODULE](https://valkey.io/commands/module/)                                               | ❌      | ❌             |
| [MODULE HELP](https://valkey.io/commands/module-help/)                                     | ❌      | ❌             |
| [MODULE LIST](https://valkey.io/commands/module-list/)                                     | ❌      | ❌             |
| [MODULE LOAD](https://valkey.io/commands/module-load/)                                     | ❌      | ❌             |
| [MODULE LOADEX](https://valkey.io/commands/module-loadex/)                                 | ❌      | ❌             |
| [MODULE UNLOAD](https://valkey.io/commands/module-unload/)                                 | ❌      | ❌             |
| [MONITOR](https://valkey.io/commands/monitor/)                                             | ❌      | ❌             |
| [MOVE](https://valkey.io/commands/move/)                                                   | ❌      | ❌             |
| [MSET](https://valkey.io/commands/mset/)                                                   | ❌      | ❌             |
| [MSETNX](https://valkey.io/commands/msetnx/)                                               | ❌      | ❌             |
| [MULTI](https://valkey.io/commands/multi/)                                                 | ❌      | ❌             |
| [OBJECT](https://valkey.io/commands/object/)                                               | ❌      | ❌             |
| [OBJECT ENCODING](https://valkey.io/commands/object-encoding/)                             | ❌      | ❌             |
| [OBJECT FREQ](https://valkey.io/commands/object-freq/)                                     | ❌      | ❌             |
| [OBJECT HELP](https://valkey.io/commands/object-help/)                                     | ❌      | ❌             |
| [OBJECT IDLETIME](https://valkey.io/commands/object-idletime/)                             | ❌      | ❌             |
| [OBJECT REFCOUNT](https://valkey.io/commands/object-refcount/)                             | ❌      | ❌             |
| [PERSIST](https://valkey.io/commands/persist/)                                             | ❌      | ❌             |
| [PEXPIRE](https://valkey.io/commands/pexpire/)                                             | ❌      | ❌             |
| [PEXPIREAT](https://valkey.io/commands/pexpireat/)                                         | ❌      | ❌             |
| [PEXPIRETIME](https://valkey.io/commands/pexpiretime/)                                     | ❌      | ❌             |
| [PFADD](https://valkey.io/commands/pfadd/)                                                 | ❌      | ❌             |
| [PFCOUNT](https://valkey.io/commands/pfcount/)                                             | ❌      | ❌             |
| [PFDEBUG](https://valkey.io/commands/pfdebug/)                                             | ❌      | ❌             |
| [PFMERGE](https://valkey.io/commands/pfmerge/)                                             | ❌      | ❌             |
| [PFSELFTEST](https://valkey.io/commands/pfselftest/)                                       | ❌      | ❌             |
| [PING](https://valkey.io/commands/ping/)                                                   | ❌      | ❌             |
| [PSETEX](https://valkey.io/commands/psetex/)                                               | ❌      | ❌             |
| [PSUBSCRIBE](https://valkey.io/commands/psubscribe/)                                       | ❌      | ❌             |
| [PSYNC](https://valkey.io/commands/psync/)                                                 | ❌      | ❌             |
| [PTTL](https://valkey.io/commands/pttl/)                                                   | ❌      | ❌             |
| [PUBLISH](https://valkey.io/commands/publish/)                                             | ❌      | ❌             |
| [PUBSUB](https://valkey.io/commands/pubsub/)                                               | ❌      | ❌             |
| [PUBSUB CHANNELS](https://valkey.io/commands/pubsub-channels/)                             | ❌      | ❌             |
| [PUBSUB HELP](https://valkey.io/commands/pubsub-help/)                                     | ❌      | ❌             |
| [PUBSUB NUMPAT](https://valkey.io/commands/pubsub-numpat/)                                 | ❌      | ❌             |
| [PUBSUB NUMSUB](https://valkey.io/commands/pubsub-numsub/)                                 | ❌      | ❌             |
| [PUBSUB SHARDCHANNELS](https://valkey.io/commands/pubsub-shardchannels/)                   | ❌      | ❌             |
| [PUBSUB SHARDNUMSUB](https://valkey.io/commands/pubsub-shardnumsub/)                       | ❌      | ❌             |
| [PUNSUBSCRIBE](https://valkey.io/commands/punsubscribe/)                                   | ❌      | ❌             |
| [QUIT](https://valkey.io/commands/quit/)                                                   | ❌      | ❌             |
| [RANDOMKEY](https://valkey.io/commands/randomkey/)                                         | ❌      | ❌             |
| [READONLY](https://valkey.io/commands/readonly/)                                           | ❌      | ❌             |
| [READWRITE](https://valkey.io/commands/readwrite/)                                         | ❌      | ❌             |
| [RENAME](https://valkey.io/commands/rename/)                                               | ❌      | ❌             |
| [RENAMENX](https://valkey.io/commands/renamenx/)                                           | ❌      | ❌             |
| [REPLCONF](https://valkey.io/commands/replconf/)                                           | ❌      | ❌             |
| [REPLICAOF](https://valkey.io/commands/replicaof/)                                         | ❌      | ❌             |
| [RESET](https://valkey.io/commands/reset/)                                                 | ❌      | ❌             |
| [RESTORE](https://valkey.io/commands/restore/)                                             | ❌      | ❌             |
| [RESTORE-ASKING](https://valkey.io/commands/restore-asking/)                               | ❌      | ❌             |
| [ROLE](https://valkey.io/commands/role/)                                                   | ❌      | ❌             |
| [RPOP](https://valkey.io/commands/rpop/)                                                   | ❌      | ❌             |
| [RPOPLPUSH](https://valkey.io/commands/rpoplpush/)                                         | ❌      | ❌             |
| [RPUSH](https://valkey.io/commands/rpush/)                                                 | ❌      | ❌             |
| [RPUSHX](https://valkey.io/commands/rpushx/)                                               | ❌      | ❌             |
| [SADD](https://valkey.io/commands/sadd/)                                                   | ❌      | ❌             |
| [SAVE](https://valkey.io/commands/save/)                                                   | ❌      | ❌             |
| [SCAN](https://valkey.io/commands/scan/)                                                   | ❌      | ❌             |
| [SCARD](https://valkey.io/commands/scard/)                                                 | ❌      | ❌             |
| [SCRIPT](https://valkey.io/commands/script/)                                               | ❌      | ❌             |
| [SCRIPT DEBUG](https://valkey.io/commands/script-debug/)                                   | ❌      | ❌             |
| [SCRIPT EXISTS](https://valkey.io/commands/script-exists/)                                 | ❌      | ❌             |
| [SCRIPT FLUSH](https://valkey.io/commands/script-flush/)                                   | ❌      | ❌             |
| [SCRIPT HELP](https://valkey.io/commands/script-help/)                                     | ❌      | ❌             |
| [SCRIPT KILL](https://valkey.io/commands/script-kill/)                                     | ❌      | ❌             |
| [SCRIPT LOAD](https://valkey.io/commands/script-load/)                                     | ❌      | ❌             |
| [SCRIPT SHOW](https://valkey.io/commands/script-show/)                                     | ❌      | ❌             |
| [SDIFF](https://valkey.io/commands/sdiff/)                                                 | ❌      | ❌             |
| [SDIFFSTORE](https://valkey.io/commands/sdiffstore/)                                       | ❌      | ❌             |
| [SELECT](https://valkey.io/commands/select/)                                               | ❌      | ❌             |
| [SET](https://valkey.io/commands/set/)                                                     | ❌      | ❌             |
| [SETBIT](https://valkey.io/commands/setbit/)                                               | ❌      | ❌             |
| [SETEX](https://valkey.io/commands/setex/)                                                 | ❌      | ❌             |
| [SETNX](https://valkey.io/commands/setnx/)                                                 | ❌      | ❌             |
| [SETRANGE](https://valkey.io/commands/setrange/)                                           | ❌      | ❌             |
| [SHUTDOWN](https://valkey.io/commands/shutdown/)                                           | ❌      | ❌             |
| [SINTER](https://valkey.io/commands/sinter/)                                               | ❌      | ❌             |
| [SINTERCARD](https://valkey.io/commands/sintercard/)                                       | ❌      | ❌             |
| [SINTERSTORE](https://valkey.io/commands/sinterstore/)                                     | ❌      | ❌             |
| [SISMEMBER](https://valkey.io/commands/sismember/)                                         | ❌      | ❌             |
| [SLAVEOF](https://valkey.io/commands/slaveof/)                                             | ❌      | ❌             |
| [SLOWLOG](https://valkey.io/commands/slowlog/)                                             | ❌      | ❌             |
| [SLOWLOG GET](https://valkey.io/commands/slowlog-get/)                                     | ❌      | ❌             |
| [SLOWLOG HELP](https://valkey.io/commands/slowlog-help/)                                   | ❌      | ❌             |
| [SLOWLOG LEN](https://valkey.io/commands/slowlog-len/)                                     | ❌      | ❌             |
| [SLOWLOG RESET](https://valkey.io/commands/slowlog-reset/)                                 | ❌      | ❌             |
| [SMEMBERS](https://valkey.io/commands/smembers/)                                           | ❌      | ❌             |
| [SMISMEMBER](https://valkey.io/commands/smismember/)                                       | ❌      | ❌             |
| [SMOVE](https://valkey.io/commands/smove/)                                                 | ❌      | ❌             |
| [SORT](https://valkey.io/commands/sort/)                                                   | ❌      | ❌             |
| [SORT_RO](https://valkey.io/commands/sort_ro/)                                             | ❌      | ❌             |
| [SPOP](https://valkey.io/commands/spop/)                                                   | ❌      | ❌             |
| [SPUBLISH](https://valkey.io/commands/spublish/)                                           | ❌      | ❌             |
| [SRANDMEMBER](https://valkey.io/commands/srandmember/)                                     | ❌      | ❌             |
| [SREM](https://valkey.io/commands/srem/)                                                   | ❌      | ❌             |
| [SSCAN](https://valkey.io/commands/sscan/)                                                 | ❌      | ❌             |
| [SSUBSCRIBE](https://valkey.io/commands/ssubscribe/)                                       | ❌      | ❌             |
| [STRLEN](https://valkey.io/commands/strlen/)                                               | ❌      | ❌             |
| [SUBSCRIBE](https://valkey.io/commands/subscribe/)                                         | ❌      | ❌             |
| [SUBSTR](https://valkey.io/commands/substr/)                                               | ❌      | ❌             |
| [SUNION](https://valkey.io/commands/sunion/)                                               | ❌      | ❌             |
| [SUNIONSTORE](https://valkey.io/commands/sunionstore/)                                     | ❌      | ❌             |
| [SUNSUBSCRIBE](https://valkey.io/commands/sunsubscribe/)                                   | ❌      | ❌             |
| [SWAPDB](https://valkey.io/commands/swapdb/)                                               | ❌      | ❌             |
| [SYNC](https://valkey.io/commands/sync/)                                                   | ❌      | ❌             |
| [TIME](https://valkey.io/commands/time/)                                                   | ❌      | ❌             |
| [TOUCH](https://valkey.io/commands/touch/)                                                 | ❌      | ❌             |
| [TTL](https://valkey.io/commands/ttl/)                                                     | ❌      | ❌             |
| [TYPE](https://valkey.io/commands/type/)                                                   | ❌      | ❌             |
| [UNLINK](https://valkey.io/commands/unlink/)                                               | ❌      | ❌             |
| [UNSUBSCRIBE](https://valkey.io/commands/unsubscribe/)                                     | ❌      | ❌             |
| [UNWATCH](https://valkey.io/commands/unwatch/)                                             | ❌      | ❌             |
| [WAIT](https://valkey.io/commands/wait/)                                                   | ❌      | ❌             |
| [WAITAOF](https://valkey.io/commands/waitaof/)                                             | ❌      | ❌             |
| [WATCH](https://valkey.io/commands/watch/)                                                 | ❌      | ❌             |
| [XACK](https://valkey.io/commands/xack/)                                                   | ❌      | ❌             |
| [XADD](https://valkey.io/commands/xadd/)                                                   | ❌      | ❌             |
| [XAUTOCLAIM](https://valkey.io/commands/xautoclaim/)                                       | ❌      | ❌             |
| [XCLAIM](https://valkey.io/commands/xclaim/)                                               | ❌      | ❌             |
| [XDEL](https://valkey.io/commands/xdel/)                                                   | ❌      | ❌             |
| [XGROUP](https://valkey.io/commands/xgroup/)                                               | ❌      | ❌             |
| [XGROUP CREATE](https://valkey.io/commands/xgroup-create/)                                 | ❌      | ❌             |
| [XGROUP CREATECONSUMER](https://valkey.io/commands/xgroup-createconsumer/)                 | ❌      | ❌             |
| [XGROUP DELCONSUMER](https://valkey.io/commands/xgroup-delconsumer/)                       | ❌      | ❌             |
| [XGROUP DESTROY](https://valkey.io/commands/xgroup-destroy/)                               | ❌      | ❌             |
| [XGROUP HELP](https://valkey.io/commands/xgroup-help/)                                     | ❌      | ❌             |
| [XGROUP SETID](https://valkey.io/commands/xgroup-setid/)                                   | ❌      | ❌             |
| [XINFO](https://valkey.io/commands/xinfo/)                                                 | ❌      | ❌             |
| [XINFO CONSUMERS](https://valkey.io/commands/xinfo-consumers/)                             | ❌      | ❌             |
| [XINFO GROUPS](https://valkey.io/commands/xinfo-groups/)                                   | ❌      | ❌             |
| [XINFO HELP](https://valkey.io/commands/xinfo-help/)                                       | ❌      | ❌             |
| [XINFO STREAM](https://valkey.io/commands/xinfo-stream/)                                   | ❌      | ❌             |
| [XLEN](https://valkey.io/commands/xlen/)                                                   | ❌      | ❌             |
| [XPENDING](https://valkey.io/commands/xpending/)                                           | ❌      | ❌             |
| [XRANGE](https://valkey.io/commands/xrange/)                                               | ❌      | ❌             |
| [XREAD](https://valkey.io/commands/xread/)                                                 | ❌      | ❌             |
| [XREADGROUP](https://valkey.io/commands/xreadgroup/)                                       | ❌      | ❌             |
| [XREVRANGE](https://valkey.io/commands/xrevrange/)                                         | ❌      | ❌             |
| [XSETID](https://valkey.io/commands/xsetid/)                                               | ❌      | ❌             |
| [XTRIM](https://valkey.io/commands/xtrim/)                                                 | ❌      | ❌             |
| [ZADD](https://valkey.io/commands/zadd/)                                                   | ❌      | ❌             |
| [ZCARD](https://valkey.io/commands/zcard/)                                                 | ❌      | ❌             |
| [ZCOUNT](https://valkey.io/commands/zcount/)                                               | ❌      | ❌             |
| [ZDIFF](https://valkey.io/commands/zdiff/)                                                 | ❌      | ❌             |
| [ZDIFFSTORE](https://valkey.io/commands/zdiffstore/)                                       | ❌      | ❌             |
| [ZINCRBY](https://valkey.io/commands/zincrby/)                                             | ❌      | ❌             |
| [ZINTER](https://valkey.io/commands/zinter/)                                               | ❌      | ❌             |
| [ZINTERCARD](https://valkey.io/commands/zintercard/)                                       | ❌      | ❌             |
| [ZINTERSTORE](https://valkey.io/commands/zinterstore/)                                     | ❌      | ❌             |
| [ZLEXCOUNT](https://valkey.io/commands/zlexcount/)                                         | ❌      | ❌             |
| [ZMPOP](https://valkey.io/commands/zmpop/)                                                 | ❌      | ❌             |
| [ZMSCORE](https://valkey.io/commands/zmscore/)                                             | ❌      | ❌             |
| [ZPOPMAX](https://valkey.io/commands/zpopmax/)                                             | ❌      | ❌             |
| [ZPOPMIN](https://valkey.io/commands/zpopmin/)                                             | ❌      | ❌             |
| [ZRANDMEMBER](https://valkey.io/commands/zrandmember/)                                     | ❌      | ❌             |
| [ZRANGE](https://valkey.io/commands/zrange/)                                               | ❌      | ❌             |
| [ZRANGEBYLEX](https://valkey.io/commands/zrangebylex/)                                     | ❌      | ❌             |
| [ZRANGEBYSCORE](https://valkey.io/commands/zrangebyscore/)                                 | ❌      | ❌             |
| [ZRANGESTORE](https://valkey.io/commands/zrangestore/)                                     | ❌      | ❌             |
| [ZRANK](https://valkey.io/commands/zrank/)                                                 | ❌      | ❌             |
| [ZREM](https://valkey.io/commands/zrem/)                                                   | ❌      | ❌             |
| [ZREMRANGEBYLEX](https://valkey.io/commands/zremrangebylex/)                               | ❌      | ❌             |
| [ZREMRANGEBYRANK](https://valkey.io/commands/zremrangebyrank/)                             | ❌      | ❌             |
| [ZREMRANGEBYSCORE](https://valkey.io/commands/zremrangebyscore/)                           | ❌      | ❌             |
| [ZREVRANGE](https://valkey.io/commands/zrevrange/)                                         | ❌      | ❌             |
| [ZREVRANGEBYLEX](https://valkey.io/commands/zrevrangebylex/)                               | ❌      | ❌             |
| [ZREVRANGEBYSCORE](https://valkey.io/commands/zrevrangebyscore/)                           | ❌      | ❌             |
| [ZREVRANK](https://valkey.io/commands/zrevrank/)                                           | ❌      | ❌             |
| [ZSCAN](https://valkey.io/commands/zscan/)                                                 | ❌      | ❌             |
| [ZSCORE](https://valkey.io/commands/zscore/)                                               | ❌      | ❌             |
| [ZUNION](https://valkey.io/commands/zunion/)                                               | ❌      | ❌             |
| [ZUNIONSTORE](https://valkey.io/commands/zunionstore/)                                     | ❌      | ❌             |

# Developer Info

## Dev-Machine Setup

1. Install the latest dotnet version onto your system
    - Make sure Aspire support is available
2. Install either docker or podman
3. Install rust
    - cargo must be available
4. Check everything is working by executing `dotnet test` or running the tests in your IDE
