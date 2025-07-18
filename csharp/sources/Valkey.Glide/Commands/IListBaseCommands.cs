// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Valkey.Glide.Commands;

/// <summary>
/// Supports commands for the "List Commands" group for standalone and cluster clients.
/// <br/>
/// See more on <see href="https://valkey.io/commands/#list">valkey.io</see>.
/// </summary>
internal interface IListBaseCommands
{
    /// <summary>
    /// Removes and returns the first elements of the list stored at <paramref name="key" />.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/lpop"/>
    /// <param name="key">The key of the list.</param>
    /// <param name="flags">Command flags are not supported by GLIDE.</param>
    /// <returns>The value of the first element.<br/>
    /// If <paramref name="key" /> does not exist, <see cref="ValkeyValue.Null"/> will be returned.
    /// </returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// ValkeyValue result = await client.ListLeftPopAsync(key);
    /// </code>
    /// </example>
    /// </remarks>
    Task<ValkeyValue> ListLeftPopAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Removes and returns up to <paramref name="count" /> elements of the list 
    /// stored at <paramref name="key" />, depending on the list's length.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/lpop"/>
    /// <param name="key">The key of the list.</param>
    /// <param name="count">The count of the elements to pop from the list.</param>
    /// <param name="flags">Command flags are not supported by GLIDE.</param>
    /// <returns>An array of the popped elements will be returned depending on the list's length.<br/>
    /// If <paramref name="key" /> does not exist, <see langword="null"/> will be returned.
    /// </returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// ValkeyValue[] result = await client.ListLeftPopAsync(key, 2);
    /// </code>
    /// </example>
    /// </remarks>
    Task<ValkeyValue[]> ListLeftPopAsync(ValkeyKey key, long count, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Inserts all the specified values at the head of the list stored at <paramref name="key" />.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/lpush"/>
    /// <param name="key">The key of the list.</param>
    /// <param name="values">The elements to insert at the head of the list stored at <paramref name="key" />.</param>
    /// <param name="when">When is not supported by GLIDE.</param>
    /// <param name="flags">Command flags are not supported by GLIDE.</param>
    /// <returns>The length of the list after the push operation.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// long result = await client.ListLeftPushAsync(key, values);
    /// </code>
    /// </example>
    /// </remarks>
    Task<long> ListLeftPushAsync(ValkeyKey key, ValkeyValue[] values, When when = When.Always, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Inserts all the specified values at the tail of the list stored at <paramref name="key" />.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/rpush"/>
    /// <param name="key">The key of the list.</param>
    /// <param name="values">The elements to insert at the tail of the list stored at <paramref name="key" />.</param>
    /// <param name="when">When is not supported by GLIDE.</param>
    /// <param name="flags">Command flags are not supported by GLIDE.</param>
    /// <returns>The length of the list after the push operation.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// long result = await client.ListRightPushAsync(key, values);
    /// </code>
    /// </example>
    /// </remarks>
    Task<long> ListRightPushAsync(ValkeyKey key, ValkeyValue[] values, When when = When.Always, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Removes and returns the last elements of the list stored at <paramref name="key" />.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/rpop"/>
    /// <param name="key">The key of the list.</param>
    /// <param name="flags">Command flags are not supported by GLIDE.</param>
    /// <returns>The value of the last element.<br/>
    /// If <paramref name="key" /> does not exist, <see cref="ValkeyValue.Null"/> will be returned.
    /// </returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// ValkeyValue result = await client.ListRightPopAsync(key);
    /// </code>
    /// </example>
    /// </remarks>
    Task<ValkeyValue> ListRightPopAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Removes and returns up to <paramref name="count" /> elements from the tail of the list 
    /// stored at <paramref name="key" />, depending on the list's length.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/rpop"/>
    /// <param name="key">The key of the list.</param>
    /// <param name="count">The count of the elements to pop from the list.</param>
    /// <param name="flags">Command flags are not supported by GLIDE.</param>
    /// <returns>An array of the popped elements will be returned depending on the list's length.<br/>
    /// If <paramref name="key" /> does not exist, <see langword="null"/> will be returned.
    /// </returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// ValkeyValue[] result = await client.ListRightPopAsync(key, 2);
    /// </code>
    /// </example>
    /// </remarks>
    Task<ValkeyValue[]> ListRightPopAsync(ValkeyKey key, long count, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Returns the length of the list stored at <paramref name="key" />.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/llen"/>
    /// <param name="key">The key of the list.</param>
    /// <param name="flags">Command flags are not supported by GLIDE.</param>
    /// <returns>The length of the list at <paramref name="key" />.<br/>
    /// If <paramref name="key" /> does not exist, 0 is returned.
    /// </returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// long result = await client.ListLengthAsync(key);
    /// </code>
    /// </example>
    /// </remarks>
    Task<long> ListLengthAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Removes the first <paramref name="count" /> occurrences of elements equal to <paramref name="value" /> from the list stored at <paramref name="key" />.
    /// The <paramref name="count" /> argument influences the operation in the following ways:
    /// <list type="bullet">
    ///     <item><paramref name="count" /> &gt; 0: Remove elements equal to <paramref name="value" /> moving from head to tail.</item>
    ///     <item><paramref name="count" /> &lt; 0: Remove elements equal to <paramref name="value" /> moving from tail to head.</item>
    ///     <item><paramref name="count" /> = 0: Remove all elements equal to <paramref name="value" />.</item>
    /// </list>
    /// </summary>
    /// <seealso href="https://valkey.io/commands/lrem"/>
    /// <param name="key">The key of the list.</param>
    /// <param name="value">The value to remove from the list.</param>
    /// <param name="count">The count behavior (see method summary).</param>
    /// <param name="flags">Command flags are not supported by GLIDE.</param>
    /// <returns>The number of removed elements.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// long result = await client.ListRemoveAsync(key, value, count);
    /// </code>
    /// </example>
    /// </remarks>
    Task<long> ListRemoveAsync(ValkeyKey key, ValkeyValue value, long count = 0, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Trims an existing list so that it will contain only the specified range of elements specified.
    /// Both <paramref name="start" /> and <paramref name="stop" /> are zero-based indexes, where 0 is the first element of the list (the head), 1 the next element and so on.
    /// For example: <c>LTRIM foobar 0 2</c> will modify the list stored at foobar so that only the first three elements of the list will remain.
    /// <paramref name="start" /> and <paramref name="stop" /> can also be negative numbers indicating offsets from the end of the list, where -1 is the last element of the list, -2 the penultimate element and so on.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/ltrim"/>
    /// <param name="key">The key of the list.</param>
    /// <param name="start">The start index of the list to trim to.</param>
    /// <param name="stop">The end index of the list to trim to.</param>
    /// <param name="flags">Command flags are not supported by GLIDE.</param>
    /// <returns>A task that represents the asynchronous operation.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// await client.ListTrimAsync(key, start, stop);
    /// </code>
    /// </example>
    /// </remarks>
    Task ListTrimAsync(ValkeyKey key, long start, long stop, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Returns the specified elements of the list stored at <paramref name="key" />.
    /// The offsets <paramref name="start" /> and <paramref name="stop" /> are zero-based indexes, with 0 being the first element of the list (the head of the list), 1 being the next element and so on.
    /// These offsets can also be negative numbers indicating offsets starting at the end of the list. For example, -1 is the last element of the list, -2 the penultimate, and so on.
    /// Note that if you have a list of numbers from 0 to 100, LRANGE list 0 10 will return 11 elements, that is, the rightmost item is included.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/lrange"/>
    /// <param name="key">The key of the list.</param>
    /// <param name="start">The start index of the list.</param>
    /// <param name="stop">The stop index of the list.</param>
    /// <param name="flags">Command flags are not supported by GLIDE.</param>
    /// <returns>List of elements in the specified range.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// ValkeyValue[] result = await client.ListRangeAsync(key, start, stop);
    /// </code>
    /// </example>
    /// </remarks>
    Task<ValkeyValue[]> ListRangeAsync(ValkeyKey key, long start = 0, long stop = -1, CommandFlags flags = CommandFlags.None);
}
