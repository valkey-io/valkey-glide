// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Valkey.Glide.Commands;

/// <summary>
/// Supports commands for the "Set Commands" group for standalone and cluster clients.
/// <br/>
/// See more on <see href="https://valkey.io/commands/?group=set#set">valkey.io</see>.
/// </summary>
internal interface ISetCommands
{
    /// <summary>
    /// SAdd adds specified members to the set stored at key.
    /// Specified members that are already a member of this set are ignored.
    /// </summary>
    /// <remarks>
    /// <seealso href="https://valkey.io/commands/sadd"/>
    /// <example>
    /// <code>
    /// bool result = await client.SetAdd(key, value);
    /// </code>
    /// </example>
    /// </remarks>
    /// <param name="key">The key where members will be added to its set.</param>
    /// <param name="value">The value to add to the set.</param>
    /// <param name="flags">The flags to use for this operation.</param>
    /// <returns><see langword="true"/> if the specified member was not already present in the set, else <see langword="false"/>.</returns>
    Task<bool> SetAdd(RedisKey key, RedisValue value, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// SAdd adds specified members to the set stored at key.
    /// Specified members that are already a member of this set are ignored.
    /// </summary>
    /// <remarks>
    /// <seealso href="https://valkey.io/commands/sadd"/>
    /// <example>
    /// <code>
    /// long result = await client.SetAdd(key, [value1, value2]);
    /// </code>
    /// </example>
    /// </remarks>
    /// <param name="key">The key where members will be added to its set.</param>
    /// <param name="values">The values to add to the set.</param>
    /// <param name="flags">The flags to use for this operation.</param>
    /// <returns>The number of elements that were added to the set, not including all the elements already present into the set.</returns>
    Task<long> SetAdd(RedisKey key, RedisValue[] values, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// SetRemove removes specified members from the set stored at key.
    /// Specified members that are not a member of this set are ignored.
    /// </summary>
    /// <remarks>
    /// <seealso href="https://valkey.io/commands/srem"/>
    /// <example>
    /// <code>
    /// bool result = await client.SetRemove(key, value);
    /// </code>
    /// </example>
    /// </remarks>
    /// <param name="key">The key from which members will be removed.</param>
    /// <param name="value">The value to remove.</param>
    /// <param name="flags">The flags to use for this operation.</param>
    /// <returns><see langword="true"/> if the specified member was already present in the set, <see langword="false"/> otherwise.</returns>
    Task<bool> SetRemove(RedisKey key, RedisValue value, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// SetRemove removes specified members from the set stored at key.
    /// Specified members that are not a member of this set are ignored.
    /// </summary>
    /// <remarks>
    /// <seealso href="https://valkey.io/commands/srem"/>
    /// <example>
    /// <code>
    /// long result = await client.SetRemove(key, [value1, value2]);
    /// </code>
    /// </example>
    /// </remarks>
    /// <param name="key">The key from which members will be removed.</param>
    /// <param name="values">The values to remove.</param>
    /// <param name="flags">The flags to use for this operation.</param>
    /// <returns>The number of members that were removed from the set, excluding non-existing members.</returns>
    Task<long> SetRemove(RedisKey key, RedisValue[] values, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// SetMembers retrieves all the members of the set value stored at key.
    /// </summary>
    /// <remarks>
    /// <seealso href="https://valkey.io/commands/smembers"/>
    /// <example>
    /// <code>
    /// RedisValue[] result = await client.SetMembers(key);
    /// </code>
    /// </example>
    /// </remarks>
    /// <param name="key">The key from which to retrieve the set members.</param>
    /// <param name="flags">The flags to use for this operation.</param>
    /// <returns>An array of <see cref="RedisValue" />s containing all members of the set. Returns an empty collection if key does not exist.</returns>
    Task<RedisValue[]> SetMembers(RedisKey key, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// SetLength retrieves the set cardinality (number of elements) of the set stored at key.
    /// </summary>
    /// <remarks>
    /// <seealso href="https://valkey.io/commands/scard"/>
    /// <example>
    /// <code>
    /// long result = await client.SetLength(key);
    /// </code>
    /// </example>
    /// </remarks>
    /// <param name="key">The key from which to retrieve the number of set members.</param>
    /// <param name="flags">The flags to use for this operation.</param>
    /// <returns>The cardinality (number of elements) of the set, or 0 if the key does not exist.</returns>
    Task<long> SetLength(RedisKey key, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// SetIntersectionLength gets the cardinality of the intersection of all the given sets.
    /// </summary>
    /// <remarks>
    /// Since Valkey 7.0 and above.
    /// 
    /// When in cluster mode, all keys must map to the same hash slot.
    /// 
    /// <seealso href="https://valkey.io/commands/sintercard"/>
    /// <example>
    /// <code>
    /// long result = await client.SetIntersectionLength([key1, key2], 2);
    /// </code>
    /// </example>
    /// </remarks>
    /// <param name="keys">The keys of the sets to intersect.</param>
    /// <param name="limit">The limit for the intersection cardinality value.</param>
    /// <param name="flags">The flags to use for this operation.</param>
    /// <returns>
    ///	The cardinality of the intersection result, or the limit if reached.
    ///	If one or more sets do not exist, 0 is returned.
    ///	If the intersection cardinality reaches 'limit' partway through the computation, returns limit as the cardinality.
    /// </returns>
    Task<long> SetIntersectionLength(RedisKey[] keys, long limit = 0, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// SetPop removes and returns one random member from the set stored at key.
    /// </summary>
    /// <remarks>
    /// <seealso href="https://valkey.io/commands/spop"/>
    /// <example>
    /// <code>
    /// RedisValue result = await client.SetPop(key);
    /// </code>
    /// </example>
    /// </remarks>
    /// <param name="key">The key of the set.</param>
    /// <param name="flags">The flags to use for this operation.</param>
    /// <returns>The removed element, or <see cref="RedisValue.Null"/> when key does not exist.</returns>
    Task<RedisValue> SetPop(RedisKey key, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Removes and returns the specified number of random elements from the set value stored at key.
    /// </summary>
    /// <remarks>
    /// <seealso href="https://valkey.io/commands/spop"/>
    /// <example>
    /// <code>
    /// RedisValue[] result = await client.SetPop(key, 2);
    /// </code>
    /// </example>
    /// </remarks>
    /// <param name="key">The key of the set.</param>
    /// <param name="count">
    /// The number of members to return.
    ///	If count is positive, returns unique elements.
    ///	If count is larger than the set's cardinality, returns the entire set.
    /// </param>
    /// <param name="flags">The flags to use for this operation.</param>
    /// <returns>An array of popped elements as <see cref="RedisValue" />s, or an empty array when key does not exist.</returns>
    Task<RedisValue[]> SetPop(RedisKey key, long count, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Returns the members of the set resulting from the specified operation against the given sets.
    /// </summary>
    /// <remarks>
    /// When in cluster mode, all keys must map to the same hash slot.
    /// 
    /// See
    /// <seealso href="https://valkey.io/commands/sunion"/>,
    /// <seealso href="https://valkey.io/commands/sinter"/>,
    /// <seealso href="https://valkey.io/commands/sdiff"/>.
    /// 
    /// <example>
    /// <code>
    /// RedisValue[] result = await client.SetCombine(SetOperation.Union, key1, key2);
    /// </code>
    /// </example>
    /// </remarks>
    /// <param name="operation">The operation to perform.</param>
    /// <param name="first">The key of the first set.</param>
    /// <param name="second">The key of the second set.</param>
    /// <param name="flags">The flags to use for this operation.</param>
    /// <returns>An array with members of the resulting set.</returns>
    Task<RedisValue[]> SetCombine(SetOperation operation, RedisKey first, RedisKey second, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Returns the members of the set resulting from the specified operation against the given sets.
    /// </summary>
    /// <remarks>
    /// When in cluster mode, all keys must map to the same hash slot.
    /// 
    /// See
    /// <seealso href="https://valkey.io/commands/sunion"/>,
    /// <seealso href="https://valkey.io/commands/sinter"/>,
    /// <seealso href="https://valkey.io/commands/sdiff"/>.
    /// 
    /// <example>
    /// <code>
    /// RedisValue[] result = await client.SetCombine(SetOperation.Union, [key1, key2]);
    /// </code>
    /// </example>
    /// </remarks>
    /// <param name="operation">The operation to perform.</param>
    /// <param name="keys">The keys of the sets to operate on.</param>
    /// <param name="flags">The flags to use for this operation.</param>
    /// <returns>An array with members of the resulting set.</returns>
    Task<RedisValue[]> SetCombine(SetOperation operation, RedisKey[] keys, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// This command is equal to SetCombine, but instead of returning the resulting set, it is stored in destination.
    /// If destination already exists, it is overwritten.
    /// </summary>
    /// <remarks>
    /// When in cluster mode, all keys must map to the same hash slot.
    /// 
    /// See
    /// <seealso href="https://valkey.io/commands/sunion"/>,
    /// <seealso href="https://valkey.io/commands/sinter"/>,
    /// <seealso href="https://valkey.io/commands/sdiff"/>.
    /// 
    /// <example>
    /// <code>
    /// long result = await client.SetCombineAndStore(SetOperation.Union, dest, key1, key2);
    /// </code>
    /// </example>
    /// </remarks>
    /// <param name="operation">The operation to perform.</param>
    /// <param name="destination">The key of the destination set.</param>
    /// <param name="first">The key of the first set.</param>
    /// <param name="second">The key of the second set.</param>
    /// <param name="flags">The flags to use for this operation.</param>
    /// <returns>The number of elements in the resulting set.</returns>
    Task<long> SetCombineAndStore(SetOperation operation, RedisKey destination, RedisKey first, RedisKey second, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// This command is equal to SetCombine, but instead of returning the resulting set, it is stored in destination.
    /// If destination already exists, it is overwritten.
    /// </summary>
    /// <remarks>
    /// When in cluster mode, all keys must map to the same hash slot.
    /// 
    /// See
    /// <seealso href="https://valkey.io/commands/sunion"/>,
    /// <seealso href="https://valkey.io/commands/sinter"/>,
    /// <seealso href="https://valkey.io/commands/sdiff"/>.
    /// 
    /// <example>
    /// <code>
    /// long result = await client.SetCombineAndStore(SetOperation.Union, dest, [key1, key2]);
    /// </code>
    /// </example>
    /// </remarks>
    /// <param name="operation">The operation to perform.</param>
    /// <param name="destination">The key of the destination set.</param>
    /// <param name="keys">The keys of the sets to operate on.</param>
    /// <param name="flags">The flags to use for this operation.</param>
    /// <returns>The number of elements in the resulting set.</returns>
    Task<long> SetCombineAndStore(SetOperation operation, RedisKey destination, RedisKey[] keys, CommandFlags flags = CommandFlags.None);

}
