// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Valkey.Glide.Commands;

/// <summary>
/// Supports commands for the "String Commands" group for standalone and cluster clients.
/// <br/>
/// See more on <see href="https://valkey.io/commands/?group=string">valkey.io</see>.
/// </summary>
public interface IStringBaseCommands
{
    /// <summary>
    /// Sets the value of a key to a string. If the key already holds a value, it is overwritten, regardless of its type.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/set/">valkey.io</seealso>
    /// <param name="key">The key to store.</param>
    /// <param name="value">The value to store with the given key.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns><see langword="true"/> if the string was set, <see langword="false"/> otherwise.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// bool result = await client.StringSetAsync("key", "value");
    /// Console.WriteLine(result); // Output: true
    /// </code>
    /// </example>
    /// </remarks>
    Task<bool> StringSetAsync(ValkeyKey key, ValkeyValue value, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Get the value of key. If the key does not exist the special value <see langword="null" /> is returned.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/get/">valkey.io</seealso>
    /// <param name="key">The key to retrieve from the database.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>
    /// If key exists, returns the value of key as a <see cref="ValkeyValue" />.<br/>
    /// Otherwise, returns <see cref="ValkeyValue.Null" />.
    /// </returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// ValkeyValue value = await client.StringGetAsync("key");
    /// Console.WriteLine(value.ToString()); // Output: "value" or null if key doesn't exist
    /// </code>
    /// </example>
    /// </remarks>
    Task<ValkeyValue> StringGetAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Returns the values of all specified keys.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/mget/">valkey.io</seealso>
    /// <note> In cluster mode, if keys in <paramref name="keys"/> map to different hash slots, the command
    /// will be split across these slots and executed separately for each. This means the command
    /// is atomic only at the slot level. If one or more slot-specific requests fail, the entire
    /// call will return the first encountered error, even though some requests may have succeeded
    /// while others did not. If this behavior impacts your application logic, consider splitting
    /// the request into sub-requests per slot to ensure atomicity.</note>
    /// <param name="keys">A list of keys to retrieve values for.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>
    /// An array of values corresponding to the provided keys.<br/>
    /// If a key is not found, its corresponding value in the list will be <see cref="ValkeyValue.Null" />.
    /// </returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// ValkeyValue[] values = await client.StringGetAsync(new ValkeyKey[] { "key1", "key2", "key3" });
    /// Console.WriteLine(values[0].ToString()); // Output: value of key1 or null
    /// Console.WriteLine(values[1].ToString()); // Output: value of key2 or null
    /// Console.WriteLine(values[2].ToString()); // Output: value of key3 or null
    /// </code>
    /// </example>
    /// </remarks>
    Task<ValkeyValue[]> StringGetAsync(ValkeyKey[] keys, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Sets multiple keys to multiple values in a single operation.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/mset/">valkey.io</seealso>
    /// <note>In cluster mode, if keys in <paramref name="values"/> map to different hash slots, the command
    /// will be split across these slots and executed separately for each. This means the command
    /// is atomic only at the slot level. If one or more slot-specific requests fail, the entire
    /// call will return the first encountered error, even though some requests may have succeeded
    /// while others did not. If this behavior impacts your application logic, consider splitting
    /// the request into sub-requests per slot to ensure atomicity.</note>
    /// <param name="values">An array of key-value pairs to set.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns><see langword="true"/> if the strings were set, <see langword="false"/> otherwise.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// KeyValuePair&lt;ValkeyKey, ValkeyValue&gt;[] values = [
    ///     new("key1", "value1"),
    ///     new("key2", "value2")
    /// ];
    /// bool result = await client.StringSetAsync(values);
    /// Console.WriteLine(result); // Output: true
    /// </code>
    /// </example>
    /// </remarks>
    Task<bool> StringSetAsync(KeyValuePair<ValkeyKey, ValkeyValue>[] values, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Returns the substring of the string value stored at key, determined by the offsets 
    /// start and end (both are inclusive).
    /// Negative offsets can be used in order to provide an offset starting from the end of the string. So `-1` means the last
    /// character, `-2` the penultimate and so forth.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/getrange/">valkey.io</seealso>
    /// <param name="key">The key of the string.</param>
    /// <param name="start">The starting offset.</param>
    /// <param name="end">The ending offset.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>
    /// A substring extracted from the value stored at key.<br/>
    /// An empty string is returned if the key does not exist or if the start and end offsets are out of range.
    /// </returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// await client.StringSetAsync("key", "Hello World");
    /// ValkeyValue result = await client.StringGetRangeAsync("key", 0, 4);
    /// Console.WriteLine(result.ToString()); // Output: "Hello"
    /// </code>
    /// </example>
    /// </remarks>
    Task<ValkeyValue> StringGetRangeAsync(ValkeyKey key, long start, long end, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Overwrites part of the string stored at key, starting at the specified offset, 
    /// for the entire length of value.
    /// If the offset is larger than the current length of the string at key, the string is padded with zero bytes to make
    /// offset fit.
    /// Creates the key if it doesn't exist.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/setrange/">valkey.io</seealso>
    /// <param name="key">The key of the string to update.</param>
    /// <param name="offset">The position in the string where value should be written.</param>
    /// <param name="value">The string written with offset.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>The length of the string stored at key after it was modified.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// await client.StringSetAsync("key", "Hello World");
    /// ValkeyValue newLength = await client.StringSetRangeAsync("key", 6, "Valkey");
    /// Console.WriteLine(newLength); // Output: 12
    /// 
    /// ValkeyValue value = await client.StringGetAsync("key");
    /// Console.WriteLine(value.ToString()); // Output: "Hello Valkey"
    /// </code>
    /// </example>
    /// </remarks>
    Task<ValkeyValue> StringSetRangeAsync(ValkeyKey key, long offset, ValkeyValue value, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Returns the length of the string value stored at key.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/strlen/">valkey.io</seealso>
    /// <param name="key">The key to check its length.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>
    /// The length of the string value stored at key.<br/>
    /// If key does not exist, it is treated as an empty string, and the command returns <c>0</c>.
    /// </returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// await client.StringSetAsync("key", "Hello World");
    /// long length = await client.StringLengthAsync("key");
    /// Console.WriteLine(length); // Output: 11
    /// </code>
    /// </example>
    /// </remarks>
    Task<long> StringLengthAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None);
}
