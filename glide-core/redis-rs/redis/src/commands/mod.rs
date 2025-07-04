use crate::cmd::{cmd, Cmd, Iter};
use crate::connection::{Connection, ConnectionLike, Msg};
use crate::pipeline::Pipeline;
use crate::types::{
    ExistenceCheck, Expiry, FromRedisValue, NumericBehavior, RedisResult, RedisWrite, SetExpiry,
    ToRedisArgs,
};

#[macro_use]
mod macros;

#[cfg(feature = "cluster-async")]
pub use cluster_scan::ScanStateRC;

#[cfg(feature = "cluster-async")]
pub use cluster_scan::ClusterScanArgs;

#[cfg(feature = "cluster-async")]
pub(crate) mod cluster_scan;

#[cfg(feature = "cluster-async")]
pub use cluster_scan::ObjectType;

#[cfg(feature = "cluster")]
use crate::cluster_pipeline::ClusterPipeline;

use crate::RedisConnectionInfo;

implement_commands! {
    'a
    // most common operations

    /// Get the value of a key.  If key is a vec this becomes an `MGET`.
    fn get<K: ToRedisArgs>(key: K) {
        cmd(if key.is_single_arg() { "GET" } else { "MGET" }).arg(key)
    }

    /// Get values of keys
    fn mget<K: ToRedisArgs>(key: K){
        cmd("MGET").arg(key)
    }

    /// Gets all keys matching pattern
    fn keys<K: ToRedisArgs>(key: K) {
        cmd("KEYS").arg(key)
    }

    /// Set the string value of a key.
    fn set<K: ToRedisArgs, V: ToRedisArgs>(key: K, value: V) {
        cmd("SET").arg(key).arg(value)
    }

    /// Set the string value of a key with options.
    fn set_options<K: ToRedisArgs, V: ToRedisArgs>(key: K, value: V, options: SetOptions) {
        cmd("SET").arg(key).arg(value).arg(options)
    }

    /// Sets multiple keys to their values.
    #[allow(deprecated)]
    #[deprecated(since = "0.22.4", note = "Renamed to mset() to reflect Redis name")]
    fn set_multiple<K: ToRedisArgs, V: ToRedisArgs>(items: &'a [(K, V)]) {
        cmd("MSET").arg(items)
    }

    /// Sets multiple keys to their values.
    fn mset<K: ToRedisArgs, V: ToRedisArgs>(items: &'a [(K, V)]) {
        cmd("MSET").arg(items)
    }

    /// Set the value and expiration of a key.
    fn set_ex<K: ToRedisArgs, V: ToRedisArgs>(key: K, value: V, seconds: u64) {
        cmd("SETEX").arg(key).arg(seconds).arg(value)
    }

    /// Set the value and expiration in milliseconds of a key.
    fn pset_ex<K: ToRedisArgs, V: ToRedisArgs>(key: K, value: V, milliseconds: u64) {
        cmd("PSETEX").arg(key).arg(milliseconds).arg(value)
    }

    /// Set the value of a key, only if the key does not exist
    fn set_nx<K: ToRedisArgs, V: ToRedisArgs>(key: K, value: V) {
        cmd("SETNX").arg(key).arg(value)
    }

    /// Sets multiple keys to their values failing if at least one already exists.
    fn mset_nx<K: ToRedisArgs, V: ToRedisArgs>(items: &'a [(K, V)]) {
        cmd("MSETNX").arg(items)
    }

    /// Set the string value of a key and return its old value.
    fn getset<K: ToRedisArgs, V: ToRedisArgs>(key: K, value: V) {
        cmd("GETSET").arg(key).arg(value)
    }

    /// Get a range of bytes/substring from the value of a key. Negative values provide an offset from the end of the value.
    fn getrange<K: ToRedisArgs>(key: K, from: isize, to: isize) {
        cmd("GETRANGE").arg(key).arg(from).arg(to)
    }

    /// Overwrite the part of the value stored in key at the specified offset.
    fn setrange<K: ToRedisArgs, V: ToRedisArgs>(key: K, offset: isize, value: V) {
        cmd("SETRANGE").arg(key).arg(offset).arg(value)
    }

    /// Delete one or more keys.
    fn del<K: ToRedisArgs>(key: K) {
        cmd("DEL").arg(key)
    }

    /// Determine if a key exists.
    fn exists<K: ToRedisArgs>(key: K) {
        cmd("EXISTS").arg(key)
    }

    /// Determine the type of a key.
    fn key_type<K: ToRedisArgs>(key: K) {
        cmd("TYPE").arg(key)
    }

    /// Set a key's time to live in seconds.
    fn expire<K: ToRedisArgs>(key: K, seconds: i64) {
        cmd("EXPIRE").arg(key).arg(seconds)
    }

    /// Set the expiration for a key as a UNIX timestamp.
    fn expire_at<K: ToRedisArgs>(key: K, ts: i64) {
        cmd("EXPIREAT").arg(key).arg(ts)
    }

    /// Set a key's time to live in milliseconds.
    fn pexpire<K: ToRedisArgs>(key: K, ms: i64) {
        cmd("PEXPIRE").arg(key).arg(ms)
    }

    /// Set the expiration for a key as a UNIX timestamp in milliseconds.
    fn pexpire_at<K: ToRedisArgs>(key: K, ts: i64) {
        cmd("PEXPIREAT").arg(key).arg(ts)
    }

    /// Remove the expiration from a key.
    fn persist<K: ToRedisArgs>(key: K) {
        cmd("PERSIST").arg(key)
    }

    /// Get the expiration time of a key.
    fn ttl<K: ToRedisArgs>(key: K) {
        cmd("TTL").arg(key)
    }

    /// Get the expiration time of a key in milliseconds.
    fn pttl<K: ToRedisArgs>(key: K) {
        cmd("PTTL").arg(key)
    }

    /// Get the value of a key and set expiration
    fn get_ex<K: ToRedisArgs>(key: K, expire_at: Expiry) {
        let (option, time_arg) = match expire_at {
            Expiry::EX(sec) => ("EX", Some(sec)),
            Expiry::PX(ms) => ("PX", Some(ms)),
            Expiry::EXAT(timestamp_sec) => ("EXAT", Some(timestamp_sec)),
            Expiry::PXAT(timestamp_ms) => ("PXAT", Some(timestamp_ms)),
            Expiry::PERSIST => ("PERSIST", None),
        };

        cmd("GETEX").arg(key).arg(option).arg(time_arg)
    }

    /// Get the value of a key and delete it
    fn get_del<K: ToRedisArgs>(key: K) {
        cmd("GETDEL").arg(key)
    }

    /// Rename a key.
    fn rename<K: ToRedisArgs, N: ToRedisArgs>(key: K, new_key: N) {
        cmd("RENAME").arg(key).arg(new_key)
    }

    /// Rename a key, only if the new key does not exist.
    fn rename_nx<K: ToRedisArgs, N: ToRedisArgs>(key: K, new_key: N) {
        cmd("RENAMENX").arg(key).arg(new_key)
    }

    /// Unlink one or more keys.
    fn unlink<K: ToRedisArgs>(key: K) {
        cmd("UNLINK").arg(key)
    }

    // common string operations

    /// Append a value to a key.
    fn append<K: ToRedisArgs, V: ToRedisArgs>(key: K, value: V) {
        cmd("APPEND").arg(key).arg(value)
    }

    /// Increment the numeric value of a key by the given amount.  This
    /// issues a `INCRBY` or `INCRBYFLOAT` depending on the type.
    fn incr<K: ToRedisArgs, V: ToRedisArgs>(key: K, delta: V) {
        cmd(if delta.describe_numeric_behavior() == NumericBehavior::NumberIsFloat {
            "INCRBYFLOAT"
        } else {
            "INCRBY"
        }).arg(key).arg(delta)
    }

    /// Decrement the numeric value of a key by the given amount.
    fn decr<K: ToRedisArgs, V: ToRedisArgs>(key: K, delta: V) {
        cmd("DECRBY").arg(key).arg(delta)
    }

    /// Sets or clears the bit at offset in the string value stored at key.
    fn setbit<K: ToRedisArgs>(key: K, offset: usize, value: bool) {
        cmd("SETBIT").arg(key).arg(offset).arg(i32::from(value))
    }

    /// Returns the bit value at offset in the string value stored at key.
    fn getbit<K: ToRedisArgs>(key: K, offset: usize) {
        cmd("GETBIT").arg(key).arg(offset)
    }

    /// Count set bits in a string.
    fn bitcount<K: ToRedisArgs>(key: K) {
        cmd("BITCOUNT").arg(key)
    }

    /// Count set bits in a string in a range.
    fn bitcount_range<K: ToRedisArgs>(key: K, start: usize, end: usize) {
        cmd("BITCOUNT").arg(key).arg(start).arg(end)
    }

    /// Perform a bitwise AND between multiple keys (containing string values)
    /// and store the result in the destination key.
    fn bit_and<D: ToRedisArgs, S: ToRedisArgs>(dstkey: D, srckeys: S) {
        cmd("BITOP").arg("AND").arg(dstkey).arg(srckeys)
    }

    /// Perform a bitwise OR between multiple keys (containing string values)
    /// and store the result in the destination key.
    fn bit_or<D: ToRedisArgs, S: ToRedisArgs>(dstkey: D, srckeys: S) {
        cmd("BITOP").arg("OR").arg(dstkey).arg(srckeys)
    }

    /// Perform a bitwise XOR between multiple keys (containing string values)
    /// and store the result in the destination key.
    fn bit_xor<D: ToRedisArgs, S: ToRedisArgs>(dstkey: D, srckeys: S) {
        cmd("BITOP").arg("XOR").arg(dstkey).arg(srckeys)
    }

    /// Perform a bitwise NOT of the key (containing string values)
    /// and store the result in the destination key.
    fn bit_not<D: ToRedisArgs, S: ToRedisArgs>(dstkey: D, srckey: S) {
        cmd("BITOP").arg("NOT").arg(dstkey).arg(srckey)
    }

    /// Get the length of the value stored in a key.
    fn strlen<K: ToRedisArgs>(key: K) {
        cmd("STRLEN").arg(key)
    }

    // hash operations

    /// Gets a single (or multiple) fields from a hash.
    fn hget<K: ToRedisArgs, F: ToRedisArgs>(key: K, field: F) {
        cmd(if field.is_single_arg() { "HGET" } else { "HMGET" }).arg(key).arg(field)
    }

    /// Deletes a single (or multiple) fields from a hash.
    fn hdel<K: ToRedisArgs, F: ToRedisArgs>(key: K, field: F) {
        cmd("HDEL").arg(key).arg(field)
    }

    /// Sets a single field in a hash.
    fn hset<K: ToRedisArgs, F: ToRedisArgs, V: ToRedisArgs>(key: K, field: F, value: V) {
        cmd("HSET").arg(key).arg(field).arg(value)
    }

    /// Sets a single field in a hash if it does not exist.
    fn hset_nx<K: ToRedisArgs, F: ToRedisArgs, V: ToRedisArgs>(key: K, field: F, value: V) {
        cmd("HSETNX").arg(key).arg(field).arg(value)
    }

    /// Sets a multiple fields in a hash.
    fn hset_multiple<K: ToRedisArgs, F: ToRedisArgs, V: ToRedisArgs>(key: K, items: &'a [(F, V)]) {
        cmd("HMSET").arg(key).arg(items)
    }

    /// Increments a value.
    fn hincr<K: ToRedisArgs, F: ToRedisArgs, D: ToRedisArgs>(key: K, field: F, delta: D) {
        cmd(if delta.describe_numeric_behavior() == NumericBehavior::NumberIsFloat {
            "HINCRBYFLOAT"
        } else {
            "HINCRBY"
        }).arg(key).arg(field).arg(delta)
    }

    /// Checks if a field in a hash exists.
    fn hexists<K: ToRedisArgs, F: ToRedisArgs>(key: K, field: F) {
        cmd("HEXISTS").arg(key).arg(field)
    }

    /// Gets all the keys in a hash.
    fn hkeys<K: ToRedisArgs>(key: K) {
        cmd("HKEYS").arg(key)
    }

    /// Gets all the values in a hash.
    fn hvals<K: ToRedisArgs>(key: K) {
        cmd("HVALS").arg(key)
    }

    /// Gets all the fields and values in a hash.
    fn hgetall<K: ToRedisArgs>(key: K) {
        cmd("HGETALL").arg(key)
    }

    /// Gets the length of a hash.
    fn hlen<K: ToRedisArgs>(key: K) {
        cmd("HLEN").arg(key)
    }

    // list operations

    /// Pop an element from a list, push it to another list
    /// and return it; or block until one is available
    fn blmove<S: ToRedisArgs, D: ToRedisArgs>(srckey: S, dstkey: D, src_dir: Direction, dst_dir: Direction, timeout: f64) {
        cmd("BLMOVE").arg(srckey).arg(dstkey).arg(src_dir).arg(dst_dir).arg(timeout)
    }

    /// Pops `count` elements from the first non-empty list key from the list of
    /// provided key names; or blocks until one is available.
    fn blmpop<K: ToRedisArgs>(timeout: f64, numkeys: usize, key: K, dir: Direction, count: usize){
        cmd("BLMPOP").arg(timeout).arg(numkeys).arg(key).arg(dir).arg("COUNT").arg(count)
    }

    /// Remove and get the first element in a list, or block until one is available.
    fn blpop<K: ToRedisArgs>(key: K, timeout: f64) {
        cmd("BLPOP").arg(key).arg(timeout)
    }

    /// Remove and get the last element in a list, or block until one is available.
    fn brpop<K: ToRedisArgs>(key: K, timeout: f64) {
        cmd("BRPOP").arg(key).arg(timeout)
    }

    /// Pop a value from a list, push it to another list and return it;
    /// or block until one is available.
    fn brpoplpush<S: ToRedisArgs, D: ToRedisArgs>(srckey: S, dstkey: D, timeout: f64) {
        cmd("BRPOPLPUSH").arg(srckey).arg(dstkey).arg(timeout)
    }

    /// Get an element from a list by its index.
    fn lindex<K: ToRedisArgs>(key: K, index: isize) {
        cmd("LINDEX").arg(key).arg(index)
    }

    /// Insert an element before another element in a list.
    fn linsert_before<K: ToRedisArgs, P: ToRedisArgs, V: ToRedisArgs>(
            key: K, pivot: P, value: V) {
        cmd("LINSERT").arg(key).arg("BEFORE").arg(pivot).arg(value)
    }

    /// Insert an element after another element in a list.
    fn linsert_after<K: ToRedisArgs, P: ToRedisArgs, V: ToRedisArgs>(
            key: K, pivot: P, value: V) {
        cmd("LINSERT").arg(key).arg("AFTER").arg(pivot).arg(value)
    }

    /// Returns the length of the list stored at key.
    fn llen<K: ToRedisArgs>(key: K) {
        cmd("LLEN").arg(key)
    }

    /// Pop an element a list, push it to another list and return it
    fn lmove<S: ToRedisArgs, D: ToRedisArgs>(srckey: S, dstkey: D, src_dir: Direction, dst_dir: Direction) {
        cmd("LMOVE").arg(srckey).arg(dstkey).arg(src_dir).arg(dst_dir)
    }

    /// Pops `count` elements from the first non-empty list key from the list of
    /// provided key names.
    fn lmpop<K: ToRedisArgs>( numkeys: usize, key: K, dir: Direction, count: usize) {
        cmd("LMPOP").arg(numkeys).arg(key).arg(dir).arg("COUNT").arg(count)
    }

    /// Removes and returns the up to `count` first elements of the list stored at key.
    ///
    /// If `count` is not specified, then defaults to first element.
    fn lpop<K: ToRedisArgs>(key: K, count: Option<core::num::NonZeroUsize>) {
        cmd("LPOP").arg(key).arg(count)
    }

    /// Returns the index of the first matching value of the list stored at key.
    fn lpos<K: ToRedisArgs, V: ToRedisArgs>(key: K, value: V, options: LposOptions) {
        cmd("LPOS").arg(key).arg(value).arg(options)
    }

    /// Insert all the specified values at the head of the list stored at key.
    fn lpush<K: ToRedisArgs, V: ToRedisArgs>(key: K, value: V) {
        cmd("LPUSH").arg(key).arg(value)
    }

    /// Inserts a value at the head of the list stored at key, only if key
    /// already exists and holds a list.
    fn lpush_exists<K: ToRedisArgs, V: ToRedisArgs>(key: K, value: V) {
        cmd("LPUSHX").arg(key).arg(value)
    }

    /// Returns the specified elements of the list stored at key.
    fn lrange<K: ToRedisArgs>(key: K, start: isize, stop: isize) {
        cmd("LRANGE").arg(key).arg(start).arg(stop)
    }

    /// Removes the first count occurrences of elements equal to value
    /// from the list stored at key.
    fn lrem<K: ToRedisArgs, V: ToRedisArgs>(key: K, count: isize, value: V) {
        cmd("LREM").arg(key).arg(count).arg(value)
    }

    /// Trim an existing list so that it will contain only the specified
    /// range of elements specified.
    fn ltrim<K: ToRedisArgs>(key: K, start: isize, stop: isize) {
        cmd("LTRIM").arg(key).arg(start).arg(stop)
    }

    /// Sets the list element at index to value
    fn lset<K: ToRedisArgs, V: ToRedisArgs>(key: K, index: isize, value: V) {
        cmd("LSET").arg(key).arg(index).arg(value)
    }

    /// Removes and returns the up to `count` last elements of the list stored at key
    ///
    /// If `count` is not specified, then defaults to last element.
    fn rpop<K: ToRedisArgs>(key: K, count: Option<core::num::NonZeroUsize>) {
        cmd("RPOP").arg(key).arg(count)
    }

    /// Pop a value from a list, push it to another list and return it.
    fn rpoplpush<K: ToRedisArgs, D: ToRedisArgs>(key: K, dstkey: D) {
        cmd("RPOPLPUSH").arg(key).arg(dstkey)
    }

    /// Insert all the specified values at the tail of the list stored at key.
    fn rpush<K: ToRedisArgs, V: ToRedisArgs>(key: K, value: V) {
        cmd("RPUSH").arg(key).arg(value)
    }

    /// Inserts value at the tail of the list stored at key, only if key
    /// already exists and holds a list.
    fn rpush_exists<K: ToRedisArgs, V: ToRedisArgs>(key: K, value: V) {
        cmd("RPUSHX").arg(key).arg(value)
    }

    // set commands

    /// Add one or more members to a set.
    fn sadd<K: ToRedisArgs, M: ToRedisArgs>(key: K, member: M) {
        cmd("SADD").arg(key).arg(member)
    }

    /// Get the number of members in a set.
    fn scard<K: ToRedisArgs>(key: K) {
        cmd("SCARD").arg(key)
    }

    /// Subtract multiple sets.
    fn sdiff<K: ToRedisArgs>(keys: K) {
        cmd("SDIFF").arg(keys)
    }

    /// Subtract multiple sets and store the resulting set in a key.
    fn sdiffstore<D: ToRedisArgs, K: ToRedisArgs>(dstkey: D, keys: K) {
        cmd("SDIFFSTORE").arg(dstkey).arg(keys)
    }

    /// Intersect multiple sets.
    fn sinter<K: ToRedisArgs>(keys: K) {
        cmd("SINTER").arg(keys)
    }

    /// Intersect multiple sets and store the resulting set in a key.
    fn sinterstore<D: ToRedisArgs, K: ToRedisArgs>(dstkey: D, keys: K) {
        cmd("SINTERSTORE").arg(dstkey).arg(keys)
    }

    /// Determine if a given value is a member of a set.
    fn sismember<K: ToRedisArgs, M: ToRedisArgs>(key: K, member: M) {
        cmd("SISMEMBER").arg(key).arg(member)
    }

    /// Determine if given values are members of a set.
    fn smismember<K: ToRedisArgs, M: ToRedisArgs>(key: K, members: M) {
        cmd("SMISMEMBER").arg(key).arg(members)
    }

    /// Get all the members in a set.
    fn smembers<K: ToRedisArgs>(key: K) {
        cmd("SMEMBERS").arg(key)
    }

    /// Move a member from one set to another.
    fn smove<S: ToRedisArgs, D: ToRedisArgs, M: ToRedisArgs>(srckey: S, dstkey: D, member: M) {
        cmd("SMOVE").arg(srckey).arg(dstkey).arg(member)
    }

    /// Remove and return a random member from a set.
    fn spop<K: ToRedisArgs>(key: K) {
        cmd("SPOP").arg(key)
    }

    /// Get one random member from a set.
    fn srandmember<K: ToRedisArgs>(key: K) {
        cmd("SRANDMEMBER").arg(key)
    }

    /// Get multiple random members from a set.
    fn srandmember_multiple<K: ToRedisArgs>(key: K, count: usize) {
        cmd("SRANDMEMBER").arg(key).arg(count)
    }

    /// Remove one or more members from a set.
    fn srem<K: ToRedisArgs, M: ToRedisArgs>(key: K, member: M) {
        cmd("SREM").arg(key).arg(member)
    }

    /// Add multiple sets.
    fn sunion<K: ToRedisArgs>(keys: K) {
        cmd("SUNION").arg(keys)
    }

    /// Add multiple sets and store the resulting set in a key.
    fn sunionstore<D: ToRedisArgs, K: ToRedisArgs>(dstkey: D, keys: K) {
        cmd("SUNIONSTORE").arg(dstkey).arg(keys)
    }

    // sorted set commands

    /// Add one member to a sorted set, or update its score if it already exists.
    fn zadd<K: ToRedisArgs, S: ToRedisArgs, M: ToRedisArgs>(key: K, member: M, score: S) {
        cmd("ZADD").arg(key).arg(score).arg(member)
    }

    /// Add multiple members to a sorted set, or update its score if it already exists.
    fn zadd_multiple<K: ToRedisArgs, S: ToRedisArgs, M: ToRedisArgs>(key: K, items: &'a [(S, M)]) {
        cmd("ZADD").arg(key).arg(items)
    }

    /// Get the number of members in a sorted set.
    fn zcard<K: ToRedisArgs>(key: K) {
        cmd("ZCARD").arg(key)
    }

    /// Count the members in a sorted set with scores within the given values.
    fn zcount<K: ToRedisArgs, M: ToRedisArgs, MM: ToRedisArgs>(key: K, min: M, max: MM) {
        cmd("ZCOUNT").arg(key).arg(min).arg(max)
    }

    /// Increments the member in a sorted set at key by delta.
    /// If the member does not exist, it is added with delta as its score.
    fn zincr<K: ToRedisArgs, M: ToRedisArgs, D: ToRedisArgs>(key: K, member: M, delta: D) {
        cmd("ZINCRBY").arg(key).arg(delta).arg(member)
    }

    /// Intersect multiple sorted sets and store the resulting sorted set in
    /// a new key using SUM as aggregation function.
    fn zinterstore<D: ToRedisArgs, K: ToRedisArgs>(dstkey: D, keys: &'a [K]) {
        cmd("ZINTERSTORE").arg(dstkey).arg(keys.len()).arg(keys)
    }

    /// Intersect multiple sorted sets and store the resulting sorted set in
    /// a new key using MIN as aggregation function.
    fn zinterstore_min<D: ToRedisArgs, K: ToRedisArgs>(dstkey: D, keys: &'a [K]) {
        cmd("ZINTERSTORE").arg(dstkey).arg(keys.len()).arg(keys).arg("AGGREGATE").arg("MIN")
    }

    /// Intersect multiple sorted sets and store the resulting sorted set in
    /// a new key using MAX as aggregation function.
    fn zinterstore_max<D: ToRedisArgs, K: ToRedisArgs>(dstkey: D, keys: &'a [K]) {
        cmd("ZINTERSTORE").arg(dstkey).arg(keys.len()).arg(keys).arg("AGGREGATE").arg("MAX")
    }

    /// [`Commands::zinterstore`], but with the ability to specify a
    /// multiplication factor for each sorted set by pairing one with each key
    /// in a tuple.
    fn zinterstore_weights<D: ToRedisArgs, K: ToRedisArgs, W: ToRedisArgs>(dstkey: D, keys: &'a [(K, W)]) {
        let (keys, weights): (Vec<&K>, Vec<&W>) = keys.iter().map(|(key, weight):&(K, W)| -> (&K, &W) {(key, weight)}).unzip();
        cmd("ZINTERSTORE").arg(dstkey).arg(keys.len()).arg(keys).arg("WEIGHTS").arg(weights)
    }

    /// [`Commands::zinterstore_min`], but with the ability to specify a
    /// multiplication factor for each sorted set by pairing one with each key
    /// in a tuple.
    fn zinterstore_min_weights<D: ToRedisArgs, K: ToRedisArgs, W: ToRedisArgs>(dstkey: D, keys: &'a [(K, W)]) {
        let (keys, weights): (Vec<&K>, Vec<&W>) = keys.iter().map(|(key, weight):&(K, W)| -> (&K, &W) {(key, weight)}).unzip();
        cmd("ZINTERSTORE").arg(dstkey).arg(keys.len()).arg(keys).arg("AGGREGATE").arg("MIN").arg("WEIGHTS").arg(weights)
    }

    /// [`Commands::zinterstore_max`], but with the ability to specify a
    /// multiplication factor for each sorted set by pairing one with each key
    /// in a tuple.
    fn zinterstore_max_weights<D: ToRedisArgs, K: ToRedisArgs, W: ToRedisArgs>(dstkey: D, keys: &'a [(K, W)]) {
        let (keys, weights): (Vec<&K>, Vec<&W>) = keys.iter().map(|(key, weight):&(K, W)| -> (&K, &W) {(key, weight)}).unzip();
        cmd("ZINTERSTORE").arg(dstkey).arg(keys.len()).arg(keys).arg("AGGREGATE").arg("MAX").arg("WEIGHTS").arg(weights)
    }

    /// Count the number of members in a sorted set between a given lexicographical range.
    fn zlexcount<K: ToRedisArgs, M: ToRedisArgs, MM: ToRedisArgs>(key: K, min: M, max: MM) {
        cmd("ZLEXCOUNT").arg(key).arg(min).arg(max)
    }

    /// Removes and returns the member with the highest score in a sorted set.
    /// Blocks until a member is available otherwise.
    fn bzpopmax<K: ToRedisArgs>(key: K, timeout: f64) {
        cmd("BZPOPMAX").arg(key).arg(timeout)
    }

    /// Removes and returns up to count members with the highest scores in a sorted set
    fn zpopmax<K: ToRedisArgs>(key: K, count: isize) {
        cmd("ZPOPMAX").arg(key).arg(count)
    }

    /// Removes and returns the member with the lowest score in a sorted set.
    /// Blocks until a member is available otherwise.
    fn bzpopmin<K: ToRedisArgs>(key: K, timeout: f64) {
        cmd("BZPOPMIN").arg(key).arg(timeout)
    }

    /// Removes and returns up to count members with the lowest scores in a sorted set
    fn zpopmin<K: ToRedisArgs>(key: K, count: isize) {
        cmd("ZPOPMIN").arg(key).arg(count)
    }

    /// Removes and returns up to count members with the highest scores,
    /// from the first non-empty sorted set in the provided list of key names.
    /// Blocks until a member is available otherwise.
    fn bzmpop_max<K: ToRedisArgs>(timeout: f64, keys: &'a [K], count: isize) {
        cmd("BZMPOP").arg(timeout).arg(keys.len()).arg(keys).arg("MAX").arg("COUNT").arg(count)
    }

    /// Removes and returns up to count members with the highest scores,
    /// from the first non-empty sorted set in the provided list of key names.
    fn zmpop_max<K: ToRedisArgs>(keys: &'a [K], count: isize) {
        cmd("ZMPOP").arg(keys.len()).arg(keys).arg("MAX").arg("COUNT").arg(count)
    }

    /// Removes and returns up to count members with the lowest scores,
    /// from the first non-empty sorted set in the provided list of key names.
    /// Blocks until a member is available otherwise.
    fn bzmpop_min<K: ToRedisArgs>(timeout: f64, keys: &'a [K], count: isize) {
        cmd("BZMPOP").arg(timeout).arg(keys.len()).arg(keys).arg("MIN").arg("COUNT").arg(count)
    }

    /// Removes and returns up to count members with the lowest scores,
    /// from the first non-empty sorted set in the provided list of key names.
    fn zmpop_min<K: ToRedisArgs>(keys: &'a [K], count: isize) {
        cmd("ZMPOP").arg(keys.len()).arg(keys).arg("MIN").arg("COUNT").arg(count)
    }

    /// Return up to count random members in a sorted set (or 1 if `count == None`)
    fn zrandmember<K: ToRedisArgs>(key: K, count: Option<isize>) {
        cmd("ZRANDMEMBER").arg(key).arg(count)
    }

    /// Return up to count random members in a sorted set with scores
    fn zrandmember_withscores<K: ToRedisArgs>(key: K, count: isize) {
        cmd("ZRANDMEMBER").arg(key).arg(count).arg("WITHSCORES")
    }

    /// Return a range of members in a sorted set, by index
    fn zrange<K: ToRedisArgs>(key: K, start: isize, stop: isize) {
        cmd("ZRANGE").arg(key).arg(start).arg(stop)
    }

    /// Return a range of members in a sorted set, by index with scores.
    fn zrange_withscores<K: ToRedisArgs>(key: K, start: isize, stop: isize) {
        cmd("ZRANGE").arg(key).arg(start).arg(stop).arg("WITHSCORES")
    }

    /// Return a range of members in a sorted set, by lexicographical range.
    fn zrangebylex<K: ToRedisArgs, M: ToRedisArgs, MM: ToRedisArgs>(key: K, min: M, max: MM) {
        cmd("ZRANGEBYLEX").arg(key).arg(min).arg(max)
    }

    /// Return a range of members in a sorted set, by lexicographical
    /// range with offset and limit.
    fn zrangebylex_limit<K: ToRedisArgs, M: ToRedisArgs, MM: ToRedisArgs>(
            key: K, min: M, max: MM, offset: isize, count: isize) {
        cmd("ZRANGEBYLEX").arg(key).arg(min).arg(max).arg("LIMIT").arg(offset).arg(count)
    }

    /// Return a range of members in a sorted set, by lexicographical range.
    fn zrevrangebylex<K: ToRedisArgs, MM: ToRedisArgs, M: ToRedisArgs>(key: K, max: MM, min: M) {
        cmd("ZREVRANGEBYLEX").arg(key).arg(max).arg(min)
    }

    /// Return a range of members in a sorted set, by lexicographical
    /// range with offset and limit.
    fn zrevrangebylex_limit<K: ToRedisArgs, MM: ToRedisArgs, M: ToRedisArgs>(
            key: K, max: MM, min: M, offset: isize, count: isize) {
        cmd("ZREVRANGEBYLEX").arg(key).arg(max).arg(min).arg("LIMIT").arg(offset).arg(count)
    }

    /// Return a range of members in a sorted set, by score.
    fn zrangebyscore<K: ToRedisArgs, M: ToRedisArgs, MM: ToRedisArgs>(key: K, min: M, max: MM) {
        cmd("ZRANGEBYSCORE").arg(key).arg(min).arg(max)
    }

    /// Return a range of members in a sorted set, by score with scores.
    fn zrangebyscore_withscores<K: ToRedisArgs, M: ToRedisArgs, MM: ToRedisArgs>(key: K, min: M, max: MM) {
        cmd("ZRANGEBYSCORE").arg(key).arg(min).arg(max).arg("WITHSCORES")
    }

    /// Return a range of members in a sorted set, by score with limit.
    fn zrangebyscore_limit<K: ToRedisArgs, M: ToRedisArgs, MM: ToRedisArgs>
            (key: K, min: M, max: MM, offset: isize, count: isize) {
        cmd("ZRANGEBYSCORE").arg(key).arg(min).arg(max).arg("LIMIT").arg(offset).arg(count)
    }

    /// Return a range of members in a sorted set, by score with limit with scores.
    fn zrangebyscore_limit_withscores<K: ToRedisArgs, M: ToRedisArgs, MM: ToRedisArgs>
            (key: K, min: M, max: MM, offset: isize, count: isize) {
        cmd("ZRANGEBYSCORE").arg(key).arg(min).arg(max).arg("WITHSCORES")
            .arg("LIMIT").arg(offset).arg(count)
    }

    /// Determine the index of a member in a sorted set.
    fn zrank<K: ToRedisArgs, M: ToRedisArgs>(key: K, member: M) {
        cmd("ZRANK").arg(key).arg(member)
    }

    /// Remove one or more members from a sorted set.
    fn zrem<K: ToRedisArgs, M: ToRedisArgs>(key: K, members: M) {
        cmd("ZREM").arg(key).arg(members)
    }

    /// Remove all members in a sorted set between the given lexicographical range.
    fn zrembylex<K: ToRedisArgs, M: ToRedisArgs, MM: ToRedisArgs>(key: K, min: M, max: MM) {
        cmd("ZREMRANGEBYLEX").arg(key).arg(min).arg(max)
    }

    /// Remove all members in a sorted set within the given indexes.
    fn zremrangebyrank<K: ToRedisArgs>(key: K, start: isize, stop: isize) {
        cmd("ZREMRANGEBYRANK").arg(key).arg(start).arg(stop)
    }

    /// Remove all members in a sorted set within the given scores.
    fn zrembyscore<K: ToRedisArgs, M: ToRedisArgs, MM: ToRedisArgs>(key: K, min: M, max: MM) {
        cmd("ZREMRANGEBYSCORE").arg(key).arg(min).arg(max)
    }

    /// Return a range of members in a sorted set, by index, with scores
    /// ordered from high to low.
    fn zrevrange<K: ToRedisArgs>(key: K, start: isize, stop: isize) {
        cmd("ZREVRANGE").arg(key).arg(start).arg(stop)
    }

    /// Return a range of members in a sorted set, by index, with scores
    /// ordered from high to low.
    fn zrevrange_withscores<K: ToRedisArgs>(key: K, start: isize, stop: isize) {
        cmd("ZREVRANGE").arg(key).arg(start).arg(stop).arg("WITHSCORES")
    }

    /// Return a range of members in a sorted set, by score.
    fn zrevrangebyscore<K: ToRedisArgs, MM: ToRedisArgs, M: ToRedisArgs>(key: K, max: MM, min: M) {
        cmd("ZREVRANGEBYSCORE").arg(key).arg(max).arg(min)
    }

    /// Return a range of members in a sorted set, by score with scores.
    fn zrevrangebyscore_withscores<K: ToRedisArgs, MM: ToRedisArgs, M: ToRedisArgs>(key: K, max: MM, min: M) {
        cmd("ZREVRANGEBYSCORE").arg(key).arg(max).arg(min).arg("WITHSCORES")
    }

    /// Return a range of members in a sorted set, by score with limit.
    fn zrevrangebyscore_limit<K: ToRedisArgs, MM: ToRedisArgs, M: ToRedisArgs>
            (key: K, max: MM, min: M, offset: isize, count: isize) {
        cmd("ZREVRANGEBYSCORE").arg(key).arg(max).arg(min).arg("LIMIT").arg(offset).arg(count)
    }

    /// Return a range of members in a sorted set, by score with limit with scores.
    fn zrevrangebyscore_limit_withscores<K: ToRedisArgs, MM: ToRedisArgs, M: ToRedisArgs>
            (key: K, max: MM, min: M, offset: isize, count: isize) {
        cmd("ZREVRANGEBYSCORE").arg(key).arg(max).arg(min).arg("WITHSCORES")
            .arg("LIMIT").arg(offset).arg(count)
    }

    /// Determine the index of a member in a sorted set, with scores ordered from high to low.
    fn zrevrank<K: ToRedisArgs, M: ToRedisArgs>(key: K, member: M) {
        cmd("ZREVRANK").arg(key).arg(member)
    }

    /// Get the score associated with the given member in a sorted set.
    fn zscore<K: ToRedisArgs, M: ToRedisArgs>(key: K, member: M) {
        cmd("ZSCORE").arg(key).arg(member)
    }

    /// Get the scores associated with multiple members in a sorted set.
    fn zscore_multiple<K: ToRedisArgs, M: ToRedisArgs>(key: K, members: &'a [M]) {
        cmd("ZMSCORE").arg(key).arg(members)
    }

    /// Unions multiple sorted sets and store the resulting sorted set in
    /// a new key using SUM as aggregation function.
    fn zunionstore<D: ToRedisArgs, K: ToRedisArgs>(dstkey: D, keys: &'a [K]) {
        cmd("ZUNIONSTORE").arg(dstkey).arg(keys.len()).arg(keys)
    }

    /// Unions multiple sorted sets and store the resulting sorted set in
    /// a new key using MIN as aggregation function.
    fn zunionstore_min<D: ToRedisArgs, K: ToRedisArgs>(dstkey: D, keys: &'a [K]) {
        cmd("ZUNIONSTORE").arg(dstkey).arg(keys.len()).arg(keys).arg("AGGREGATE").arg("MIN")
    }

    /// Unions multiple sorted sets and store the resulting sorted set in
    /// a new key using MAX as aggregation function.
    fn zunionstore_max<D: ToRedisArgs, K: ToRedisArgs>(dstkey: D, keys: &'a [K]) {
        cmd("ZUNIONSTORE").arg(dstkey).arg(keys.len()).arg(keys).arg("AGGREGATE").arg("MAX")
    }

    /// [`Commands::zunionstore`], but with the ability to specify a
    /// multiplication factor for each sorted set by pairing one with each key
    /// in a tuple.
    fn zunionstore_weights<D: ToRedisArgs, K: ToRedisArgs, W: ToRedisArgs>(dstkey: D, keys: &'a [(K, W)]) {
        let (keys, weights): (Vec<&K>, Vec<&W>) = keys.iter().map(|(key, weight):&(K, W)| -> (&K, &W) {(key, weight)}).unzip();
        cmd("ZUNIONSTORE").arg(dstkey).arg(keys.len()).arg(keys).arg("WEIGHTS").arg(weights)
    }

    /// [`Commands::zunionstore_min`], but with the ability to specify a
    /// multiplication factor for each sorted set by pairing one with each key
    /// in a tuple.
    fn zunionstore_min_weights<D: ToRedisArgs, K: ToRedisArgs, W: ToRedisArgs>(dstkey: D, keys: &'a [(K, W)]) {
        let (keys, weights): (Vec<&K>, Vec<&W>) = keys.iter().map(|(key, weight):&(K, W)| -> (&K, &W) {(key, weight)}).unzip();
        cmd("ZUNIONSTORE").arg(dstkey).arg(keys.len()).arg(keys).arg("AGGREGATE").arg("MIN").arg("WEIGHTS").arg(weights)
    }

    /// [`Commands::zunionstore_max`], but with the ability to specify a
    /// multiplication factor for each sorted set by pairing one with each key
    /// in a tuple.
    fn zunionstore_max_weights<D: ToRedisArgs, K: ToRedisArgs, W: ToRedisArgs>(dstkey: D, keys: &'a [(K, W)]) {
        let (keys, weights): (Vec<&K>, Vec<&W>) = keys.iter().map(|(key, weight):&(K, W)| -> (&K, &W) {(key, weight)}).unzip();
        cmd("ZUNIONSTORE").arg(dstkey).arg(keys.len()).arg(keys).arg("AGGREGATE").arg("MAX").arg("WEIGHTS").arg(weights)
    }

    // hyperloglog commands

    /// Adds the specified elements to the specified HyperLogLog.
    fn pfadd<K: ToRedisArgs, E: ToRedisArgs>(key: K, element: E) {
        cmd("PFADD").arg(key).arg(element)
    }

    /// Return the approximated cardinality of the set(s) observed by the
    /// HyperLogLog at key(s).
    fn pfcount<K: ToRedisArgs>(key: K) {
        cmd("PFCOUNT").arg(key)
    }

    /// Merge N different HyperLogLogs into a single one.
    fn pfmerge<D: ToRedisArgs, S: ToRedisArgs>(dstkey: D, srckeys: S) {
        cmd("PFMERGE").arg(dstkey).arg(srckeys)
    }

    /// Posts a message to the given channel.
    fn publish<K: ToRedisArgs, E: ToRedisArgs>(channel: K, message: E) {
        cmd("PUBLISH").arg(channel).arg(message)
    }

    // Object commands

    /// Returns the encoding of a key.
    fn object_encoding<K: ToRedisArgs>(key: K) {
        cmd("OBJECT").arg("ENCODING").arg(key)
    }

    /// Returns the time in seconds since the last access of a key.
    fn object_idletime<K: ToRedisArgs>(key: K) {
        cmd("OBJECT").arg("IDLETIME").arg(key)
    }

    /// Returns the logarithmic access frequency counter of a key.
    fn object_freq<K: ToRedisArgs>(key: K) {
        cmd("OBJECT").arg("FREQ").arg(key)
    }

    /// Returns the reference count of a key.
    fn object_refcount<K: ToRedisArgs>(key: K) {
        cmd("OBJECT").arg("REFCOUNT").arg(key)
    }
}

/// Allows pubsub callbacks to stop receiving messages.
///
/// Arbitrary data may be returned from `Break`.
pub enum ControlFlow<U> {
    /// Continues.
    Continue,
    /// Breaks with a value.
    Break(U),
}

/// The PubSub trait allows subscribing to one or more channels
/// and receiving a callback whenever a message arrives.
///
/// Each method handles subscribing to the list of keys, waiting for
/// messages, and unsubscribing from the same list of channels once
/// a ControlFlow::Break is encountered.
///
/// Once (p)subscribe returns Ok(U), the connection is again safe to use
/// for calling other methods.
///
/// # Examples
///
/// ```rust,no_run
/// # fn do_something() -> redis::RedisResult<()> {
/// use redis::{PubSubCommands, ControlFlow};
/// let client = redis::Client::open("redis://127.0.0.1/")?;
/// let mut con = client.get_connection(None)?;
/// let mut count = 0;
/// con.subscribe(&["foo"], |msg| {
///     // do something with message
///     assert_eq!(msg.get_channel(), Ok(String::from("foo")));
///
///     // increment messages seen counter
///     count += 1;
///     match count {
///         // stop after receiving 10 messages
///         10 => ControlFlow::Break(()),
///         _ => ControlFlow::Continue,
///     }
/// })?;
/// # Ok(()) }
/// ```
// TODO In the future, it would be nice to implement Try such that `?` will work
//      within the closure.
pub trait PubSubCommands: Sized {
    /// Subscribe to a list of channels using SUBSCRIBE and run the provided
    /// closure for each message received.
    ///
    /// For every `Msg` passed to the provided closure, either
    /// `ControlFlow::Break` or `ControlFlow::Continue` must be returned. This
    /// method will not return until `ControlFlow::Break` is observed.
    fn subscribe<C, F, U>(&mut self, _: C, _: F) -> RedisResult<U>
    where
        F: FnMut(Msg) -> ControlFlow<U>,
        C: ToRedisArgs;

    /// Subscribe to a list of channels using PSUBSCRIBE and run the provided
    /// closure for each message received.
    ///
    /// For every `Msg` passed to the provided closure, either
    /// `ControlFlow::Break` or `ControlFlow::Continue` must be returned. This
    /// method will not return until `ControlFlow::Break` is observed.
    fn psubscribe<P, F, U>(&mut self, _: P, _: F) -> RedisResult<U>
    where
        F: FnMut(Msg) -> ControlFlow<U>,
        P: ToRedisArgs;
}

impl<T> Commands for T where T: ConnectionLike {}

#[cfg(feature = "aio")]
impl<T> AsyncCommands for T where T: crate::aio::ConnectionLike + Send + Sized {}

impl PubSubCommands for Connection {
    fn subscribe<C, F, U>(&mut self, channels: C, mut func: F) -> RedisResult<U>
    where
        F: FnMut(Msg) -> ControlFlow<U>,
        C: ToRedisArgs,
    {
        let mut pubsub = self.as_pubsub();
        pubsub.subscribe(channels)?;

        loop {
            let msg = pubsub.get_message()?;
            match func(msg) {
                ControlFlow::Continue => continue,
                ControlFlow::Break(value) => return Ok(value),
            }
        }
    }

    fn psubscribe<P, F, U>(&mut self, patterns: P, mut func: F) -> RedisResult<U>
    where
        F: FnMut(Msg) -> ControlFlow<U>,
        P: ToRedisArgs,
    {
        let mut pubsub = self.as_pubsub();
        pubsub.psubscribe(patterns)?;

        loop {
            let msg = pubsub.get_message()?;
            match func(msg) {
                ControlFlow::Continue => continue,
                ControlFlow::Break(value) => return Ok(value),
            }
        }
    }
}

/// Options for the [LPOS](https://redis.io/commands/lpos) command
///
/// # Example
///
/// ```rust,no_run
/// use redis::{Commands, RedisResult, LposOptions};
/// fn fetch_list_position(
///     con: &mut redis::Connection,
///     key: &str,
///     value: &str,
///     count: usize,
///     rank: isize,
///     maxlen: usize,
/// ) -> RedisResult<Vec<usize>> {
///     let opts = LposOptions::default()
///         .count(count)
///         .rank(rank)
///         .maxlen(maxlen);
///     con.lpos(key, value, opts)
/// }
/// ```
#[derive(Default)]
pub struct LposOptions {
    count: Option<usize>,
    maxlen: Option<usize>,
    rank: Option<isize>,
}

impl LposOptions {
    /// Limit the results to the first N matching items.
    pub fn count(mut self, n: usize) -> Self {
        self.count = Some(n);
        self
    }

    /// Return the value of N from the matching items.
    pub fn rank(mut self, n: isize) -> Self {
        self.rank = Some(n);
        self
    }

    /// Limit the search to N items in the list.
    pub fn maxlen(mut self, n: usize) -> Self {
        self.maxlen = Some(n);
        self
    }
}

impl ToRedisArgs for LposOptions {
    fn write_redis_args<W>(&self, out: &mut W)
    where
        W: ?Sized + RedisWrite,
    {
        if let Some(n) = self.count {
            out.write_arg(b"COUNT");
            out.write_arg_fmt(n);
        }

        if let Some(n) = self.rank {
            out.write_arg(b"RANK");
            out.write_arg_fmt(n);
        }

        if let Some(n) = self.maxlen {
            out.write_arg(b"MAXLEN");
            out.write_arg_fmt(n);
        }
    }

    fn is_single_arg(&self) -> bool {
        false
    }
}

/// Enum for the LEFT | RIGHT args used by some commands
pub enum Direction {
    /// Targets the first element (head) of the list
    Left,
    /// Targets the last element (tail) of the list
    Right,
}

impl ToRedisArgs for Direction {
    fn write_redis_args<W>(&self, out: &mut W)
    where
        W: ?Sized + RedisWrite,
    {
        let s: &[u8] = match self {
            Direction::Left => b"LEFT",
            Direction::Right => b"RIGHT",
        };
        out.write_arg(s);
    }
}

/// Options for the [SET](https://redis.io/commands/set) command
///
/// # Example
/// ```rust,no_run
/// use redis::{Commands, RedisResult, SetOptions, SetExpiry, ExistenceCheck};
/// fn set_key_value(
///     con: &mut redis::Connection,
///     key: &str,
///     value: &str,
/// ) -> RedisResult<Vec<usize>> {
///     let opts = SetOptions::default()
///         .conditional_set(ExistenceCheck::NX)
///         .get(true)
///         .with_expiration(SetExpiry::EX(60));
///     con.set_options(key, value, opts)
/// }
/// ```
#[derive(Clone, Copy, Default)]
pub struct SetOptions {
    conditional_set: Option<ExistenceCheck>,
    get: bool,
    expiration: Option<SetExpiry>,
}

impl SetOptions {
    /// Set the existence check for the SET command
    pub fn conditional_set(mut self, existence_check: ExistenceCheck) -> Self {
        self.conditional_set = Some(existence_check);
        self
    }

    /// Set the GET option for the SET command
    pub fn get(mut self, get: bool) -> Self {
        self.get = get;
        self
    }

    /// Set the expiration for the SET command
    pub fn with_expiration(mut self, expiration: SetExpiry) -> Self {
        self.expiration = Some(expiration);
        self
    }
}

impl ToRedisArgs for SetOptions {
    fn write_redis_args<W>(&self, out: &mut W)
    where
        W: ?Sized + RedisWrite,
    {
        if let Some(ref conditional_set) = self.conditional_set {
            match conditional_set {
                ExistenceCheck::NX => {
                    out.write_arg(b"NX");
                }
                ExistenceCheck::XX => {
                    out.write_arg(b"XX");
                }
            }
        }
        if self.get {
            out.write_arg(b"GET");
        }
        if let Some(ref expiration) = self.expiration {
            match expiration {
                SetExpiry::EX(secs) => {
                    out.write_arg(b"EX");
                    out.write_arg(format!("{secs}").as_bytes());
                }
                SetExpiry::PX(millis) => {
                    out.write_arg(b"PX");
                    out.write_arg(format!("{millis}").as_bytes());
                }
                SetExpiry::EXAT(unix_time) => {
                    out.write_arg(b"EXAT");
                    out.write_arg(format!("{unix_time}").as_bytes());
                }
                SetExpiry::PXAT(unix_time) => {
                    out.write_arg(b"PXAT");
                    out.write_arg(format!("{unix_time}").as_bytes());
                }
                SetExpiry::KEEPTTL => {
                    out.write_arg(b"KEEPTTL");
                }
            }
        }
    }
}

/// Creates HELLO command for RESP3 with RedisConnectionInfo
pub fn resp3_hello(connection_info: &RedisConnectionInfo) -> Cmd {
    let mut hello_cmd = cmd("HELLO");
    hello_cmd.arg("3");
    if let Some(password) = &connection_info.password {
        let username: &str = match connection_info.username.as_ref() {
            None => "default",
            Some(username) => username,
        };
        hello_cmd.arg("AUTH").arg(username).arg(password);
    }
    hello_cmd
}
