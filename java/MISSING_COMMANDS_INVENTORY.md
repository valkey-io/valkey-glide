# Missing Commands Inventory for JNI Implementation

Based on integration test compilation failures, these command families need implementation:

## 1. String Commands (HIGH PRIORITY)
- `incr(String key)` - Increment integer value
- `incr(GlideString key)` - Binary-safe increment
- `incrBy(String key, long amount)` - Increment by amount
- `incrBy(GlideString key, long amount)` - Binary-safe increment by amount
- `incrByFloat(String key, double amount)` - Increment by float amount
- `incrByFloat(GlideString key, double amount)` - Binary-safe increment by float
- `decr(String key)` - Decrement integer value
- `decr(GlideString key)` - Binary-safe decrement
- `decrBy(String key, long amount)` - Decrement by amount
- `decrBy(GlideString key, long amount)` - Binary-safe decrement by amount
- `strlen(String key)` - Get string length
- `strlen(GlideString key)` - Binary-safe string length
- `get(String key)` - Get string value (may need GlideString variant)
- `mget(String[] keys)` - Multi-get strings
- `mget(GlideString[] keys)` - Binary-safe multi-get
- `mset(Map<String, String> keyValueMap)` - Multi-set strings
- `mset(Map<GlideString, GlideString> keyValueMap)` - Binary-safe multi-set
- `msetnx(Map<String, String> keyValueMap)` - Multi-set if not exists
- `msetnx(Map<GlideString, GlideString> keyValueMap)` - Binary-safe msetnx
- `getDel(String key)` - Get and delete
- `getDel(GlideString key)` - Binary-safe get and delete
- `getEx(String key, GetExOptions options)` - Get with expiration options
- `getEx(GlideString key, GetExOptions options)` - Binary-safe getex

## 2. Key Management Commands (HIGH PRIORITY)
- `exists(String[] keys)` - Check if keys exist
- `exists(GlideString[] keys)` - Binary-safe exists
- `expire(String key, long seconds)` - Set expiration
- `expire(GlideString key, long seconds)` - Binary-safe expire
- `expireAt(String key, long timestamp)` - Set expiration at timestamp
- `expireAt(GlideString key, long timestamp)` - Binary-safe expireAt
- `pexpire(String key, long milliseconds)` - Set expiration in milliseconds
- `pexpire(GlideString key, long milliseconds)` - Binary-safe pexpire
- `pexpire(String key, long milliseconds, ExpireOptions options)` - With options
- `pexpire(GlideString key, long milliseconds, ExpireOptions options)` - Binary-safe with options
- `pexpireAt(String key, long timestamp)` - Set expiration at timestamp in ms
- `pexpireAt(GlideString key, long timestamp)` - Binary-safe pexpireAt
- `ttl(String key)` - Get time to live
- `ttl(GlideString key)` - Binary-safe ttl
- `pttl(String key)` - Get time to live in milliseconds
- `pttl(GlideString key)` - Binary-safe pttl
- `expireTime(String key)` - Get expiration time
- `expireTime(GlideString key)` - Binary-safe expireTime
- `pexpireTime(String key)` - Get expiration time in milliseconds
- `pexpireTime(GlideString key)` - Binary-safe pexpireTime
- `persist(String key)` - Remove expiration
- `persist(GlideString key)` - Binary-safe persist
- `unlink(String[] keys)` - Non-blocking delete
- `unlink(GlideString[] keys)` - Binary-safe unlink

## 3. Scripting and Functions (MEDIUM PRIORITY)
- `fcall(String function, String[] keys, String[] args)` - Call function
- `fcall(GlideString function, GlideString[] keys, GlideString[] args)` - Binary-safe fcall
- `fcallReadOnly(String function, String[] keys, String[] args)` - Call read-only function
- `fcallReadOnly(GlideString function, GlideString[] keys, GlideString[] args)` - Binary-safe fcallReadOnly
- `functionStats()` - Get function statistics
- `submitScript(String script)` - Submit Lua script

## 4. List Commands (MEDIUM PRIORITY)
- `lmpop(String[] keys, ListDirection direction, long count)` - Pop from multiple lists
- `lmpop(GlideString[] keys, ListDirection direction, long count)` - Binary-safe lmpop

## 5. Set Commands (MEDIUM PRIORITY)
- `sintercard(String[] keys, long limit)` - Get intersection cardinality with limit
- `sintercard(GlideString[] keys, long limit)` - Binary-safe sintercard
- `sscan(String key, String cursor, ScanOptions options)` - Scan set members
- `sscan(GlideString key, GlideString cursor, ScanOptions options)` - Binary-safe sscan

## 6. String/LCS Commands (LOW PRIORITY)
- `lcsIdx(String key1, String key2, long minMatchLen)` - LCS with indices
- `lcsIdx(GlideString key1, GlideString key2, long minMatchLen)` - Binary-safe lcsIdx

## 7. Bitwise Commands (LOW PRIORITY)
- `bitcount(String key)` - Count set bits
- `bitcount(GlideString key)` - Binary-safe bitcount
- `bitcount(String key, long start, long end)` - Count set bits in range
- `bitcount(GlideString key, long start, long end)` - Binary-safe bitcount with range

## 8. Generic Commands (LOW PRIORITY)
- `sort(String key)` - Sort key elements
- `sort(GlideString key)` - Binary-safe sort
- `sort(String key, SortOptions options)` - Sort with options
- `sort(GlideString key, SortOptions options)` - Binary-safe sort with options

## 9. Server Info Commands (LOW PRIORITY)
- `info()` - Get server information
- `info(InfoOptions options)` - Get server info with options

## Missing Support Classes/Enums
- `ExpireOptions` class
- `GetExOptions` class
- `ListDirection` enum
- `ScanOptions` class
- `SortOptions` class
- `InfoOptions` class

## Route Variants for Cluster
Each method above needs a corresponding Route variant for cluster support:
- `methodName(..., Route route)` returning `CompletableFuture<ClusterValue<T>>`

## ✅ COMPLETED IMPLEMENTATIONS

### 1. String Commands (COMPLETED ✅)
- ✅ `incr/incrBy/incrByFloat/decr/decrBy` - All increment/decrement commands (18 methods)
- ✅ `get/set/mget/mset/msetnx/getDel/strlen` - Core string operations (32 methods)

### 2. Key Management Commands (COMPLETED ✅)
- ✅ `expire/expireAt/pexpire/pexpireAt/ttl/pttl/expireTime/pexpireTime/persist` - Expiration commands (48 methods)
- ✅ `exists/unlink` - Key existence commands (8 methods)

### 3. Scripting and Functions (COMPLETED ✅)
- ✅ `fcall/fcallReadOnly/functionStats` - Function commands (10 methods)

### 4. List Commands (COMPLETED ✅)
- ✅ `lmpop` - List pop commands (8 methods)
- ✅ `ListDirection` enum created

### 5. Set Commands (COMPLETED ✅)
- ✅ `sadd/srem/smembers/scard/sismember/smismember/smove` - Basic set operations (28 methods merged)
- ✅ `sintercard/sscan` - Advanced set operations (already implemented)

### 6. Bitwise Commands (COMPLETED ✅)
- ✅ `bitcount(String key)` - Count set bits (16 methods implemented separately)
- ✅ `bitcount(GlideString key)` - Binary-safe bitcount
- ✅ `bitcount(String key, long start, long end)` - Count set bits in range
- ✅ `bitcount(GlideString key, long start, long end)` - Binary-safe bitcount with range

### 7. String/LCS Commands (COMPLETED ✅)
- ✅ `lcsIdx(String key1, String key2, long minMatchLen)` - LCS with indices (12 methods implemented)
- ✅ `lcsIdx(GlideString key1, GlideString key2, long minMatchLen)` - Binary-safe lcsIdx
- ✅ `lcsIdxWithMatchLen` family - LCS with match length tracking

## ❌ REMAINING IMPLEMENTATIONS - CATEGORIZED FOR COLLABORATION

### ERROR CATEGORY A: Parameter Type Mismatches (Agent 2 Priority)
- ❌ `lcsIdx(String,String,int)` - Test expects `int` but we have `long` parameter
- ❌ `sintercard(String[],int)` - Test expects `int` but we have `long` parameter  
- ❌ `pexpire(String,int,ExpireOptions)` - Test expects `int` but we have `long` parameter

### ERROR CATEGORY B: Missing Support Classes (Agent 2 Priority)
- ❌ `ExpireOptions` class - Required for pexpire methods
- ❌ `GetExOptions` class - Required for getEx methods (if needed)
- ❌ `ScanOptions` class variations - Required for scan methods
- ❌ `SortOptions` class - Required for sort methods  
- ❌ `InfoOptions` class - Required for info methods

### ERROR CATEGORY C: String Commands Implementation (Agent 1 Current)
- ❌ `getEx(String key, GetExOptions options)` - Get with expiration options
- ❌ `getEx(GlideString key, GetExOptions options)` - Binary-safe getex

### ERROR CATEGORY D: Generic Commands Implementation (Agent 1 Next)  
- ❌ `sort(String key)` - Sort key elements
- ❌ `sort(GlideString key)` - Binary-safe sort
- ❌ `sort(String key, SortOptions options)` - Sort with options
- ❌ `sort(GlideString key, SortOptions options)` - Binary-safe sort with options

### ERROR CATEGORY E: Server Info Commands Implementation (Agent 1 Future)
- ❌ `info()` - Get server information
- ❌ `info(InfoOptions options)` - Get server info with options

### ERROR CATEGORY F: Symbol Resolution Issues (Investigate Both)
- ❌ Various "cannot find symbol" errors in test files
- ❌ Missing import statements or class references

### 9. Generic Commands
- ❌ `sort(String key)` - Sort key elements
- ❌ `sort(GlideString key)` - Binary-safe sort
- ❌ `sort(String key, SortOptions options)` - Sort with options
- ❌ `sort(GlideString key, SortOptions options)` - Binary-safe sort with options

### 10. Server Info Commands
- ❌ `info()` - Get server information
- ❌ `info(InfoOptions options)` - Get server info with options

## Missing Support Classes/Enums (TO CREATE)
- ❌ `GetExOptions` class
- ❌ `ScanOptions` class
- ❌ `SortOptions` class
- ❌ `InfoOptions` class

## Implementation Strategy - UPDATED
1. **Phase 1**: ✅ String commands (COMPLETED)
2. **Phase 2**: ✅ Key management commands (COMPLETED)  
3. **Phase 3**: ✅ Scripting/functions (COMPLETED)
4. **Phase 4**: ✅ List commands (COMPLETED)
5. **Phase 5**: ✅ Set commands (COMPLETED)
6. **Phase 6**: ✅ Bitwise commands (COMPLETED - 16 methods)
7. **Phase 7**: ✅ LCS commands (COMPLETED - 12 methods)
8. **Phase 8**: ❌ Remaining commands (getEx, sort, info) + Support classes

## Current Agent Coordination Plan - COLLABORATIVE APPROACH

### Agent #1 (Claude code) - Implementation Focus
- **Current**: GetEx commands (Category C)
- **Next**: Sort commands (Category D)  
- **Future**: Info commands (Category E)
- **Status**: Continue implementing missing command methods

### Agent #2 (Partner agent) - Type/Class Focus  
- **Priority 1**: Support class creation (Category B)
  - ExpireOptions, GetExOptions, ScanOptions, SortOptions, InfoOptions
- **Priority 2**: Parameter type fixes (Category A)  
  - int vs long parameter mismatches
- **Priority 3**: Symbol resolution (Category F)
  - Missing imports, class references

### Collaboration Strategy
- Each agent updates this file after completing tasks
- Agent #1 implements methods, Agent #2 handles type compatibility  
- Both agents can investigate Category F issues when encountered
- Target: 0 integration test compilation errors
