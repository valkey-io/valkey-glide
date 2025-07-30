// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Valkey.Glide.Commands;

/// <summary>
/// Supports commands for the "Set Commands" group for standalone and cluster clients.
/// <br />
/// See more on <see href="https://valkey.io/commands#set">valkey.io</see>.
/// </summary>
public interface ISetCommands
{
    /// <summary>
    /// Adds specified members to the set stored at key.
    /// Specified members that are already a member of this set are ignored.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/sadd"/>
    /// <param name="key">The key where members will be added to its set.</param>
    /// <param name="value">The value to add to the set.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns><see langword="true"/> if the specified member was not already present in the set, else <see langword="false"/>.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// bool result = await client.SetAddAsync(key, value);
    /// </code>
    /// </example>
    /// </remarks>
    Task<bool> SetAddAsync(ValkeyKey key, ValkeyValue value, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Adds specified members to the set stored at key.
    /// Specified members that are already a member of this set are ignored.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/sadd"/>
    /// <param name="key">The key where members will be added to its set.</param>
    /// <param name="values">The values to add to the set.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>The number of elements that were added to the set, not including all the elements already present into the set.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// long result = await client.SetAddAsync(key, [value1, value2]);
    /// </code>
    /// </example>
    /// </remarks>
    Task<long> SetAddAsync(ValkeyKey key, ValkeyValue[] values, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Removes specified members from the set stored at key.
    /// Specified members that are not a member of this set are ignored.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/srem"/>
    /// <param name="key">The key from which members will be removed.</param>
    /// <param name="value">The value to remove.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns><see langword="true"/> if the specified member was already present in the set, <see langword="false"/> otherwise.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// bool result = await client.SetRemoveAsync(key, value);
    /// </code>
    /// </example>
    /// </remarks>
    Task<bool> SetRemoveAsync(ValkeyKey key, ValkeyValue value, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Removes specified members from the set stored at key.
    /// Specified members that are not a member of this set are ignored.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/srem"/>
    /// <param name="key">The key from which members will be removed.</param>
    /// <param name="values">The values to remove.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>The number of members that were removed from the set, excluding non-existing members.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// long result = await client.SetRemoveAsync(key, [value1, value2]);
    /// </code>
    /// </example>
    /// </remarks>
    Task<long> SetRemoveAsync(ValkeyKey key, ValkeyValue[] values, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Retrieves all the members of the set value stored at key.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/smembers"/>
    /// <param name="key">The key from which to retrieve the set members.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>An array of <see cref="ValkeyValue" />s containing all members of the set. Returns an empty collection if key does not exist.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// ValkeyValue[] result = await client.SetMembersAsync(key);
    /// </code>
    /// </example>
    /// </remarks>
    Task<ValkeyValue[]> SetMembersAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Retrieves the set cardinality (number of elements) of the set stored at key.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/scard"/>
    /// <param name="key">The key from which to retrieve the number of set members.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>The cardinality (number of elements) of the set, or 0 if the key does not exist.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// long result = await client.SetLengthAsync(key);
    /// </code>
    /// </example>
    /// </remarks>
    Task<long> SetLengthAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Gets the cardinality of the intersection of all the given sets.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/sintercard"/>
    /// <note>Since Valkey 7.0 and above.</note>
    /// <note>When in cluster mode, all keys must map to the same hash slot.</note>
    /// <param name="keys">The keys of the sets to intersect.</param>
    /// <param name="limit">The limit for the intersection cardinality value.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>
    ///	The cardinality of the intersection result, or the limit if reached.
    ///	If one or more sets do not exist, 0 is returned.
    ///	If the intersection cardinality reaches 'limit' partway through the computation, returns limit as the cardinality.
    /// </returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// long result = await client.SetIntersectionLengthAsync([key1, key2], 2);
    /// </code>
    /// </example>
    /// </remarks>
    Task<long> SetIntersectionLengthAsync(ValkeyKey[] keys, long limit = 0, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Removes and returns one random member from the set stored at key.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/spop"/>
    /// <param name="key">The key of the set.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>The removed element, or <see cref="ValkeyValue.Null"/> when key does not exist.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// ValkeyValue result = await client.SetPopAsync(key);
    /// </code>
    /// </example>
    /// </remarks>
    Task<ValkeyValue> SetPopAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Removes and returns the specified number of random elements from the set value stored at key.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/spop"/>
    /// <param name="key">The key of the set.</param>
    /// <param name="count">
    /// The number of members to return.
    ///	If count is positive, returns unique elements.
    ///	If count is larger than the set's cardinality, returns the entire set.
    /// </param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>An array of popped elements as <see cref="ValkeyValue" />s, or an empty array when key does not exist.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// ValkeyValue[] result = await client.SetPopAsync(key, 2);
    /// </code>
    /// </example>
    /// </remarks>
    Task<ValkeyValue[]> SetPopAsync(ValkeyKey key, long count, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Returns the members of the set resulting from the union of all the given sets.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/sunion"/>
    /// <note>When in cluster mode, all keys must map to the same hash slot.</note>
    /// <param name="first">The key of the first set.</param>
    /// <param name="second">The key of the second set.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>An array with members of the resulting set.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// ValkeyValue[] result = await client.SetUnionAsync(key1, key2);
    /// </code>
    /// </example>
    /// </remarks>
    Task<ValkeyValue[]> SetUnionAsync(ValkeyKey first, ValkeyKey second, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Returns the members of the set resulting from the union of all the given sets.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/sunion"/>
    /// <note>When in cluster mode, all keys must map to the same hash slot.</note>
    /// <param name="keys">The keys of the sets to operate on.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>An array with members of the resulting set.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// ValkeyValue[] result = await client.SetUnionAsync([key1, key2]);
    /// </code>
    /// </example>
    /// </remarks>
    Task<ValkeyValue[]> SetUnionAsync(ValkeyKey[] keys, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Returns the members of the set resulting from the intersection of all the given sets.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/sinter"/>
    /// <note>When in cluster mode, all keys must map to the same hash slot.</note>
    /// <param name="first">The key of the first set.</param>
    /// <param name="second">The key of the second set.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>An array with members of the resulting set.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// ValkeyValue[] result = await client.SetIntersectAsync(key1, key2);
    /// </code>
    /// </example>
    /// </remarks>
    Task<ValkeyValue[]> SetIntersectAsync(ValkeyKey first, ValkeyKey second, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Returns the members of the set resulting from the intersection of all the given sets.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/sinter"/>
    /// <note>When in cluster mode, all keys must map to the same hash slot.</note>
    /// <param name="keys">The keys of the sets to operate on.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>An array with members of the resulting set.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// ValkeyValue[] result = await client.SetIntersectAsync([key1, key2]);
    /// </code>
    /// </example>
    /// </remarks>
    Task<ValkeyValue[]> SetIntersectAsync(ValkeyKey[] keys, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Returns the members of the set resulting from the difference between the first set and all the successive sets.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/sdiff"/>
    /// <note>When in cluster mode, all keys must map to the same hash slot.</note>
    /// <param name="first">The key of the first set.</param>
    /// <param name="second">The key of the second set.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>An array with members of the resulting set.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// ValkeyValue[] result = await client.SetDifferenceAsync(key1, key2);
    /// </code>
    /// </example>
    /// </remarks>
    Task<ValkeyValue[]> SetDifferenceAsync(ValkeyKey first, ValkeyKey second, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Returns the members of the set resulting from the difference between the first set and all the successive sets.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/sdiff"/>
    /// <note>When in cluster mode, all keys must map to the same hash slot.</note>
    /// <param name="keys">The keys of the sets to operate on.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>An array with members of the resulting set.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// ValkeyValue[] result = await client.SetDifferenceAsync([key1, key2]);
    /// </code>
    /// </example>
    /// </remarks>
    Task<ValkeyValue[]> SetDifferenceAsync(ValkeyKey[] keys, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Stores the members of the set resulting from the union of all the given sets into destination.
    /// If destination already exists, it is overwritten.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/sunionstore"/>
    /// <note>When in cluster mode, all keys must map to the same hash slot.</note>
    /// <param name="destination">The key of the destination set.</param>
    /// <param name="first">The key of the first set.</param>
    /// <param name="second">The key of the second set.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>The number of elements in the resulting set.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// long result = await client.SetUnionStoreAsync(dest, key1, key2);
    /// </code>
    /// </example>
    /// </remarks>
    Task<long> SetUnionStoreAsync(ValkeyKey destination, ValkeyKey first, ValkeyKey second, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Stores the members of the set resulting from the union of all the given sets into destination.
    /// If destination already exists, it is overwritten.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/sunionstore"/>
    /// <note>When in cluster mode, all keys must map to the same hash slot.</note>
    /// <param name="destination">The key of the destination set.</param>
    /// <param name="keys">The keys of the sets to operate on.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>The number of elements in the resulting set.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// long result = await client.SetUnionStoreAsync(dest, [key1, key2]);
    /// </code>
    /// </example>
    /// </remarks>
    Task<long> SetUnionStoreAsync(ValkeyKey destination, ValkeyKey[] keys, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Stores the members of the set resulting from the intersection of all the given sets into destination.
    /// If destination already exists, it is overwritten.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/sinterstore"/>
    /// <note>When in cluster mode, all keys must map to the same hash slot.</note>
    /// <param name="destination">The key of the destination set.</param>
    /// <param name="first">The key of the first set.</param>
    /// <param name="second">The key of the second set.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>The number of elements in the resulting set.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// long result = await client.SetIntersectStoreAsync(dest, key1, key2);
    /// </code>
    /// </example>
    /// </remarks>
    Task<long> SetIntersectStoreAsync(ValkeyKey destination, ValkeyKey first, ValkeyKey second, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Stores the members of the set resulting from the intersection of all the given sets into destination.
    /// If destination already exists, it is overwritten.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/sinterstore"/>
    /// <note>When in cluster mode, all keys must map to the same hash slot.</note>
    /// <param name="destination">The key of the destination set.</param>
    /// <param name="keys">The keys of the sets to operate on.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>The number of elements in the resulting set.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// long result = await client.SetIntersectStoreAsync(dest, [key1, key2]);
    /// </code>
    /// </example>
    /// </remarks>
    Task<long> SetIntersectStoreAsync(ValkeyKey destination, ValkeyKey[] keys, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Stores the members of the set resulting from the difference between the first set and all the successive sets into destination.
    /// If destination already exists, it is overwritten.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/sdiffstore"/>
    /// <note>When in cluster mode, all keys must map to the same hash slot.</note>
    /// <param name="destination">The key of the destination set.</param>
    /// <param name="first">The key of the first set.</param>
    /// <param name="second">The key of the second set.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>The number of elements in the resulting set.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// long result = await client.SetDifferenceStoreAsync(dest, key1, key2);
    /// </code>
    /// </example>
    /// </remarks>
    Task<long> SetDifferenceStoreAsync(ValkeyKey destination, ValkeyKey first, ValkeyKey second, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Stores the members of the set resulting from the difference between the first set and all the successive sets into destination.
    /// If destination already exists, it is overwritten.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/sdiffstore"/>
    /// <note>When in cluster mode, all keys must map to the same hash slot.</note>
    /// <param name="destination">The key of the destination set.</param>
    /// <param name="keys">The keys of the sets to operate on.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>The number of elements in the resulting set.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// long result = await client.SetDifferenceStoreAsync(dest, [key1, key2]);
    /// </code>
    /// </example>
    /// </remarks>
    Task<long> SetDifferenceStoreAsync(ValkeyKey destination, ValkeyKey[] keys, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Returns whether value is a member of the set stored at key.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/sismember"/>
    /// <param name="key">The key of the set.</param>
    /// <param name="value">The member to check for existence in the set.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns><see langword="true"/> if the element exists in the set, <see langword="false"/> if the element is not a member of the set, or if key does not exist (treated as an empty set).</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// bool result = await client.SetContainsAsync(key, value);
    /// </code>
    /// </example>
    /// </remarks>
    Task<bool> SetContainsAsync(ValkeyKey key, ValkeyValue value, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Returns whether each value is a member of the set stored at key.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/smismember"/>
    /// <param name="key">The key of the set.</param>
    /// <param name="values">The members to check.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>An array of <see langword="bool"/> values, each indicating whether the corresponding element is a member of the set.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// bool[] result = await client.SetContainsAsync(key, [value1, value2]);
    /// </code>
    /// </example>
    /// </remarks>
    Task<bool[]> SetContainsAsync(ValkeyKey key, ValkeyValue[] values, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Returns a random element from the set value stored at key.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/srandmember"/>
    /// <param name="key">The key from which to retrieve the set member.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>The randomly selected element, or <see cref="ValkeyValue.Null"/> when key does not exist.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// ValkeyValue result = await client.SetRandomMemberAsync(key);
    /// </code>
    /// </example>
    /// </remarks>
    Task<ValkeyValue> SetRandomMemberAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Returns multiple random members from the set value stored at key.
    /// If <paramref name="count"/> is positive, returns unique elements (no repetition) up to count or the set size, whichever is smaller.
    /// If <paramref name="count"/> is negative, returns elements with possible repetition (the same element may be returned multiple times),
    /// and the number of returned elements is the absolute value of count.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/srandmember"/>
    /// <param name="key">The key from which to retrieve the set members.</param>
    /// <param name="count">The number of members to return.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>
    ///	An array of random elements from the set.
    ///	When count is positive, the returned elements are unique (no repetitions).
    ///	When count is negative, the returned elements may contain duplicates.
    ///	If the set does not exist or is empty, an empty array is returned.
    /// </returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// ValkeyValue[] result = await client.SetRandomMembersAsync(key, 3);
    /// </code>
    /// </example>
    /// </remarks>
    Task<ValkeyValue[]> SetRandomMembersAsync(ValkeyKey key, long count, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Moves <paramref name="value"/> from the set at <paramref name="source"/>` to the set at <paramref name="destination"/>, removing it from the source set.
    /// Creates a new destination set if needed. The operation is atomic.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/smove"/>
    /// <note>When in cluster mode, <paramref name="source"/> and <paramref name="destination"/> must map to the same hash slot.</note>
    /// <param name="source">The key of the set to remove the element from.</param>
    /// <param name="destination">The key of the set to add the element to.</param>
    /// <param name="value">The set element to move.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns><see langword="true"/> if the element is moved, <see langword="false"/> if the source set does not exist or the element is not a member of the source set.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// bool result = await client.SetMoveAsync(sourceKey, destKey, value);
    /// </code>
    /// </example>
    /// </remarks>
    Task<bool> SetMoveAsync(ValkeyKey source, ValkeyKey destination, ValkeyValue value, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Iterates elements over a set.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/sscan"/>
    /// <param name="key">The key of the set.</param>
    /// <param name="pattern">The pattern to match.</param>
    /// <param name="pageSize">The page size to iterate by.</param>
    /// <param name="cursor">The cursor position to start at.</param>
    /// <param name="pageOffset">The page offset to start at.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>An <see cref="IAsyncEnumerable{T}"/> that yields all matching elements of the set.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// await foreach (ValkeyValue value in client.SetScanAsync(key, "*pattern*"))
    /// {
    ///     // Process each value
    /// }
    /// </code>
    /// </example>
    /// </remarks>
    IAsyncEnumerable<ValkeyValue> SetScanAsync(ValkeyKey key, ValkeyValue pattern = default, int pageSize = 250, long cursor = 0, int pageOffset = 0, CommandFlags flags = CommandFlags.None);
}
