// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System;
using System.ComponentModel;

namespace Valkey.Glide.Commands
{
    /// <summary>
    /// Supports commands and transactions for the "Generic Commands" group for standalone and cluster clients.
    /// Commands include: DEL (including UNLINK), EXISTS, EXPIRE (including PEXPIRE), EXPIREAT (including PEXPIREAT), 
    /// EXPIRETIME (including PEXPIRETIME), TTL (including PTTL), TOUCH, TYPE, RENAME (including RENAMENX), 
    /// PERSIST, RESTORE, OBJECT ENCODING, DUMP, OBJECT FREQ, OBJECT IDLETIME, OBJECT REFCOUNT, 
    /// SORT (including SORT_RO), COPY.
    /// 
    /// See <seealso href="https://valkey.io/commands/?group=Generic"/> for details.
    /// </summary>
    public interface IGenericBaseCommands
    {
        /// <summary>
        /// Removes the specified keys from the database. A key is ignored if it does not exist.
        /// 
        /// Note:
        /// 
        /// In cluster mode, if keys in keys map to different hash slots, the command
        /// will be split across these slots and executed separately for each. This means the command
        /// is atomic only at the slot level. If one or more slot-specific requests fail, the entire
        /// call will return the first encountered error, even though some requests may have succeeded
        /// while others did not. If this behavior impacts your application logic, consider splitting
        /// the request into sub-requests per slot to ensure atomicity.
        /// </summary>
        /// <param name="keys">One or more keys to delete.</param>
        /// <param name="flags">The flags to use for this operation.</param>
        /// <returns>Returns the number of keys that were removed.</returns>
        /// <remarks><seealso href="https://valkey.io/commands/del"/></remarks>
        long KeyDelete(RedisKey[] keys, CommandFlags flags = CommandFlags.None);

        /// <summary>
        /// Removes the specified key from the database. A key is ignored if it does not exist.
        /// </summary>
        /// <param name="key">The key to delete.</param>
        /// <param name="flags">The flags to use for this operation.</param>
        /// <returns><see langword="true"/> if the key was removed.</returns>
        /// <remarks><seealso href="https://valkey.io/commands/del"/></remarks>
        bool KeyDelete(RedisKey key, CommandFlags flags = CommandFlags.None);

        /// <summary>
        /// Indicates how many of the supplied keys exists.
        /// 
        /// Note:
        /// 
        /// In cluster mode, if keys in keys map to different hash slots, the command
        /// will be split across these slots and executed separately for each. This means the command
        /// is atomic only at the slot level. If one or more slot-specific requests fail, the entire
        /// call will return the first encountered error, even though some requests may have succeeded
        /// while others did not. If this behavior impacts your application logic, consider splitting
        /// the request into sub-requests per slot to ensure atomicity.
        /// </summary>
        /// <param name="keys">The keys to check.</param>
        /// <param name="flags">The flags to use for this operation.</param>
        /// <returns>The number of keys that existed.</returns>
        /// <remarks><seealso href="https://valkey.io/commands/exists"/></remarks>
        long KeyExists(RedisKey[] keys, CommandFlags flags = CommandFlags.None);

        /// <summary>
        /// Returns if key exists.
        /// </summary>
        /// <param name="key">The key to check.</param>
        /// <param name="flags">The flags to use for this operation.</param>
        /// <returns><see langword="true"/> if the key exists. <see langword="false"/> if the key does not exist.</returns>
        /// <remarks><seealso href="https://valkey.io/commands/exists"/></remarks>
        bool KeyExists(RedisKey key, CommandFlags flags = CommandFlags.None);

        /// <summary>
        /// Set a timeout on key. After the timeout has expired, the key will automatically be deleted.
        /// A key with an associated timeout is said to be volatile in Redis terminology.
        /// </summary>
        /// <param name="key">The key to set the expiration for.</param>
        /// <param name="expiry">The timeout to set.</param>
        /// <param name="when">In Redis 7+, we can choose under which condition the expiration will be set using <see cref="ExpireWhen"/>.</param>
        /// <param name="flags">The flags to use for this operation.</param>
        /// <returns><see langword="true"/> if the timeout was set. <see langword="false"/> if key does not exist or the timeout could not be set.</returns>
        /// <remarks>
        /// See
        /// <seealso href="https://valkey.io/commands/expire"/>,
        /// <seealso href="https://valkey.io/commands/pexpire"/>.
        /// </remarks>
        bool KeyExpire(RedisKey key, TimeSpan? expiry, ExpireWhen when = ExpireWhen.Always, CommandFlags flags = CommandFlags.None);

        /// <summary>
        /// Set a timeout on key. After the timeout has expired, the key will automatically be deleted.
        /// A key with an associated timeout is said to be volatile in Redis terminology.
        /// </summary>
        /// <param name="key">The key to set the expiration for.</param>
        /// <param name="expiry">The timeout to set.</param>
        /// <param name="when">In Redis 7+, we choose under which condition the expiration will be set using <see cref="ExpireWhen"/>.</param>
        /// <param name="flags">The flags to use for this operation.</param>
        /// <returns><see langword="true"/> if the timeout was set. <see langword="false"/> if key does not exist or the timeout could not be set.</returns>
        /// <remarks>
        /// See
        /// <seealso href="https://valkey.io/commands/expire"/>,
        /// <seealso href="https://valkey.io/commands/pexpire"/>.
        /// </remarks>
        bool KeyExpire(RedisKey key, DateTime? expiry, ExpireWhen when = ExpireWhen.Always, CommandFlags flags = CommandFlags.None);

        /// <summary>
        /// Returns the absolute time at which the given key will expire, if it exists and has an expiration.
        /// </summary>
        /// <param name="key">The key to get the expiration for.</param>
        /// <param name="flags">The flags to use for this operation.</param>
        /// <returns>The time at which the given key will expire, or <see langword="null"/> if the key does not exist or has no associated expiration time.</returns>
        /// <remarks>
        /// See
        /// <seealso href="https://valkey.io/commands/expiretime"/>,
        /// <seealso href="https://valkey.io/commands/pexpiretime"/>.
        /// </remarks>
        DateTime? KeyExpireTime(RedisKey key, CommandFlags flags = CommandFlags.None);

        /// <summary>
        /// Returns the remaining time to live of a key that has a timeout.
        /// This introspection capability allows a Redis client to check how many seconds a given key will continue to be part of the dataset.
        /// </summary>
        /// <param name="key">The key to check.</param>
        /// <param name="flags">The flags to use for this operation.</param>
        /// <returns>TTL, or <see langword="null"/> when key does not exist or does not have a timeout.</returns>
        /// <remarks><seealso href="https://valkey.io/commands/ttl"/></remarks>
        TimeSpan? KeyTimeToLive(RedisKey key, CommandFlags flags = CommandFlags.None);

        /// <summary>
        /// Alters the last access time of the specified keys. A key is ignored if it does not exist.
        /// 
        /// Note:
        /// 
        /// In cluster mode, if keys in keys map to different hash slots, the command
        /// will be split across these slots and executed separately for each. This means the command
        /// is atomic only at the slot level. If one or more slot-specific requests fail, the entire
        /// call will return the first encountered error, even though some requests may have succeeded
        /// while others did not. If this behavior impacts your application logic, consider splitting
        /// the request into sub-requests per slot to ensure atomicity.
        /// </summary>
        /// <param name="keys">The keys to touch.</param>
        /// <param name="flags">The flags to use for this operation.</param>
        /// <returns>The number of keys that were touched.</returns>
        /// <remarks><seealso href="https://valkey.io/commands/touch"/></remarks>
        long KeyTouch(RedisKey[] keys, CommandFlags flags = CommandFlags.None);

        /// <summary>
        /// Alters the last access time of a key.
        /// </summary>
        /// <param name="key">The key to touch.</param>
        /// <param name="flags">The flags to use for this operation.</param>
        /// <returns><see langword="true"/> if the key was touched, <see langword="false"/> otherwise.</returns>
        /// <remarks><seealso href="https://valkey.io/commands/touch"/></remarks>
        bool KeyTouch(RedisKey key, CommandFlags flags = CommandFlags.None);

        /// <summary>
        /// Returns the string representation of the type of the value stored at key.
        /// The different types that can be returned are: string, list, set, zset and hash.
        /// </summary>
        /// <param name="key">The key to get the type of.</param>
        /// <param name="flags">The flags to use for this operation.</param>
        /// <returns>Type of key, or none when key does not exist.</returns>
        /// <remarks><seealso href="https://valkey.io/commands/type"/></remarks>
        RedisType KeyType(RedisKey key, CommandFlags flags = CommandFlags.None);

        /// <summary>
        /// Renames key to newKey.
        /// It returns an error when the source and destination names are the same, or when key does not exist.
        /// 
        /// Note:
        /// 
        /// When in cluster mode, both key and newKey must map to the same hash slot.
        /// </summary>
        /// <param name="key">The key to rename.</param>
        /// <param name="newKey">The key to rename to.</param>
        /// <param name="when">What conditions to rename under (defaults to always).</param>
        /// <param name="flags">The flags to use for this operation.</param>
        /// <returns><see langword="true"/> if the key was renamed, <see langword="false"/> otherwise.</returns>
        /// <remarks>
        /// See
        /// <seealso href="https://valkey.io/commands/rename"/>,
        /// <seealso href="https://valkey.io/commands/renamenx"/>.
        /// </remarks>
        bool KeyRename(RedisKey key, RedisKey newKey, When when = When.Always, CommandFlags flags = CommandFlags.None);

        /// <summary>
        /// Remove the existing timeout on key, turning the key from volatile (a key with an expire set) to persistent (a key that will never expire as no timeout is associated).
        /// </summary>
        /// <param name="key">The key to persist.</param>
        /// <param name="flags">The flags to use for this operation.</param>
        /// <returns><see langword="true"/> if the timeout was removed. <see langword="false"/> if key does not exist or does not have an associated timeout.</returns>
        /// <remarks><seealso href="https://valkey.io/commands/persist"/></remarks>
        bool KeyPersist(RedisKey key, CommandFlags flags = CommandFlags.None);

        /// <summary>
        /// Create a key associated with a value that is obtained by deserializing the provided serialized value (obtained via DUMP).
        /// If expiry is 0 the key is created without any expire, otherwise the specified expire time (in milliseconds) is set.
        /// 
        /// Note:
        /// 
        /// When in cluster mode, both source and destination must map to the same hash slot.
        /// </summary>
        /// <param name="key">The key to restore.</param>
        /// <param name="value">The value of the key.</param>
        /// <param name="expiry">The expiry to set.</param>
        /// <param name="flags">The flags to use for this operation.</param>
        /// <remarks><seealso href="https://valkey.io/commands/restore"/></remarks>
        void KeyRestore(RedisKey key, byte[] value, TimeSpan? expiry = null, CommandFlags flags = CommandFlags.None);

        /// <summary>
        /// Returns the internal encoding for the Redis object stored at key.
        /// </summary>
        /// <param name="key">The key to dump.</param>
        /// <param name="flags">The flags to use for this operation.</param>
        /// <returns>The Redis encoding for the value or <see langword="null"/> is the key does not exist.</returns>
        /// <remarks><seealso href="https://valkey.io/commands/object-encoding"/></remarks>
        string? KeyEncoding(RedisKey key, CommandFlags flags = CommandFlags.None);

        /// <summary>
        /// Serialize the value stored at key in a Redis-specific format and return it to the user.
        /// The returned value can be synthesized back into a Redis key using the RESTORE command.
        /// </summary>
        /// <param name="key">The key to dump.</param>
        /// <param name="flags">The flags to use for this operation.</param>
        /// <returns>The serialized value.</returns>
        /// <remarks><seealso href="https://valkey.io/commands/dump"/></remarks>
        byte[]? KeyDump(RedisKey key, CommandFlags flags = CommandFlags.None);

        /// <summary>
        /// Returns the logarithmic access frequency counter of the object stored at key.
        /// The command is only available when the maxmemory-policy configuration directive is set to
        /// one of the LFU policies.
        /// </summary>
        /// <param name="key">The key to get a frequency count for.</param>
        /// <param name="flags">The flags to use for this operation.</param>
        /// <returns>The number of logarithmic access frequency counter, (<see langword="null"/> if the key does not exist).</returns>
        /// <remarks><seealso href="https://valkey.io/commands/object-freq"/></remarks>
        long? KeyFrequency(RedisKey key, CommandFlags flags = CommandFlags.None);

        /// <summary>
        /// Returns the time since the object stored at the specified key is idle (not requested by read or write operations).
        /// </summary>
        /// <param name="key">The key to get the time of.</param>
        /// <param name="flags">The flags to use for this operation.</param>
        /// <returns>The time since the object stored at the specified key is idle.</returns>
        /// <remarks><seealso href="https://valkey.io/commands/object"/></remarks>
        TimeSpan? KeyIdleTime(RedisKey key, CommandFlags flags = CommandFlags.None);

        /// <summary>
        /// Returns the reference count of the object stored at key.
        /// </summary>
        /// <param name="key">The key to get a reference count for.</param>
        /// <param name="flags">The flags to use for this operation.</param>
        /// <returns>The number of references (<see langword="Null"/> if the key does not exist).</returns>
        /// <remarks><seealso href="https://valkey.io/commands/object-refcount"/></remarks>
        long? KeyRefCount(RedisKey key, CommandFlags flags = CommandFlags.None);

        /// <summary>
        /// Sorts a list, set or sorted set (numerically or alphabetically, ascending by default).
        /// By default, the elements themselves are compared, but the values can also be used to perform external key-lookups using the by parameter.
        /// By default, the elements themselves are returned, but external key-lookups (one or many) can be performed instead by specifying
        /// the get parameter (note that # specifies the element itself, when used in get).
        /// Referring to the redis SORT documentation for examples is recommended.
        /// When used in hashes, by and get can be used to specify fields using -> notation (again, refer to redis documentation).
        /// Uses SORT_RO when possible.
        /// </summary>
        /// <param name="key">The key of the list, set, or sorted set.</param>
        /// <param name="skip">How many entries to skip on the return.</param>
        /// <param name="take">How many entries to take on the return.</param>
        /// <param name="order">The ascending or descending order (defaults to ascending).</param>
        /// <param name="sortType">The sorting method (defaults to numeric).</param>
        /// <param name="by">The key pattern to sort by, if any. e.g. ExternalKey_* would sort by ExternalKey_{listvalue} as a lookup.</param>
        /// <param name="get">The key pattern to sort by, if any e.g. ExternalKey_* would return the value of ExternalKey_{listvalue} for each entry.</param>
        /// <param name="flags">The flags to use for this operation.</param>
        /// <returns>The sorted elements, or the external values if get is specified.</returns>
        /// <remarks>
        /// See
        /// <seealso href="https://valkey.io/commands/sort"/>,
        /// <seealso href="https://valkey.io/commands/sort_ro"/>.
        /// </remarks>
        RedisValue[] Sort(RedisKey key, long skip = 0, long take = -1, Order order = Order.Ascending, SortType sortType = SortType.Numeric, RedisValue by = default, RedisValue[]? get = null, CommandFlags flags = CommandFlags.None);

        /// <summary>
        /// Sorts a list, set or sorted set (numerically or alphabetically, ascending by default).
        /// By default, the elements themselves are compared, but the values can also be used to perform external key-lookups using the by parameter.
        /// By default, the elements themselves are returned, but external key-lookups (one or many) can be performed instead by specifying
        /// the get parameter (note that # specifies the element itself, when used in get).
        /// Referring to the redis SORT documentation for examples is recommended.
        /// When used in hashes, by and get can be used to specify fields using -> notation (again, refer to redis documentation).
        /// </summary>
        /// <param name="destination">The destination key to store results in.</param>
        /// <param name="key">The key of the list, set, or sorted set.</param>
        /// <param name="skip">How many entries to skip on the return.</param>
        /// <param name="take">How many entries to take on the return.</param>
        /// <param name="order">The ascending or descending order (defaults to ascending).</param>
        /// <param name="sortType">The sorting method (defaults to numeric).</param>
        /// <param name="by">The key pattern to sort by, if any. e.g. ExternalKey_* would sort by ExternalKey_{listvalue} as a lookup.</param>
        /// <param name="get">The key pattern to sort by, if any e.g. ExternalKey_* would return the value of ExternalKey_{listvalue} for each entry.</param>
        /// <param name="flags">The flags to use for this operation.</param>
        /// <returns>The number of elements stored in the new list.</returns>
        /// <remarks><seealso href="https://valkey.io/commands/sort"/></remarks>
        long SortAndStore(RedisKey destination, RedisKey key, long skip = 0, long take = -1, Order order = Order.Ascending, SortType sortType = SortType.Numeric, RedisValue by = default, RedisValue[]? get = null, CommandFlags flags = CommandFlags.None);

        /// <summary>
        /// Copies the value from the sourceKey to the specified destinationKey.
        /// 
        /// Note:
        /// 
        /// When in cluster mode, both sourceKey and destinationKey must map to the same hash slot.
        /// </summary>
        /// <param name="sourceKey">The key of the source value to copy.</param>
        /// <param name="destinationKey">The destination key to copy the source to.</param>
        /// <param name="destinationDatabase">The database ID to store destinationKey in. If default (-1), current database is used.</param>
        /// <param name="replace">Whether to overwrite an existing values at destinationKey. If <see langword="false"/> and the key exists, the copy will not succeed.</param>
        /// <param name="flags">The flags to use for this operation.</param>
        /// <returns><see langword="true"/> if key was copied. <see langword="false"/> if key was not copied.</returns>
        /// <remarks><seealso href="https://valkey.io/commands/copy"/></remarks>
        bool KeyCopy(RedisKey sourceKey, RedisKey destinationKey, int destinationDatabase = -1, bool replace = false, CommandFlags flags = CommandFlags.None);
    }
}
