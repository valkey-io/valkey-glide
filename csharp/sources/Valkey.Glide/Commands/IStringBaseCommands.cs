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
    /// Set key to hold the string value. If key already holds a value, it is overwritten, regardless of its type.
    /// </summary>
    /// <remarks>
    /// <example>
    /// <code>
    /// string result = await client.StringSet("key", "value");
    /// Console.WriteLine(result); // Output: "OK"
    /// </code>
    /// </example>
    /// </remarks>
    /// <param name="key">The key to store.</param>
    /// <param name="value">The value to store with the given key.</param>
    /// <returns>A simple <c>"OK"</c> response.</returns>
    /// <seealso href="https://valkey.io/commands/set/">valkey.io</seealso>
    Task<string> StringSet(GlideString key, GlideString value);

    /// <summary>
    /// Get the value of key. If the key does not exist the special value <see langword="null" /> is returned.
    /// </summary>
    /// <remarks>
    /// <example>
    /// <code>
    /// GlideString? value = await client.StringGet("key");
    /// Console.WriteLine(value?.ToString()); // Output: "value" or null if key doesn't exist
    /// </code>
    /// </example>
    /// </remarks>
    /// <param name="key">The key to retrieve from the database.</param>
    /// <returns>
    /// If key exists, returns the value of key as a <see cref="GlideString" />.<br/>
    /// Otherwise, returns <see langword="null" />.
    /// </returns>
    /// <seealso href="https://valkey.io/commands/get/">valkey.io</seealso>
    Task<GlideString?> StringGet(GlideString key);

    /// <summary>
    /// Returns the values of all specified keys.
    /// </summary>
    /// <remarks>
    /// <example>
    /// <code>
    /// GlideString?[] values = await client.StringGet(new GlideString[] { "key1", "key2", "key3" });
    /// Console.WriteLine(values[0]?.ToString()); // Output: value of key1 or null
    /// Console.WriteLine(values[1]?.ToString()); // Output: value of key2 or null
    /// Console.WriteLine(values[2]?.ToString()); // Output: value of key3 or null
    /// </code>
    /// </example>
    /// </remarks>
    /// <param name="keys">A list of keys to retrieve values for.</param>
    /// <returns>
    /// An array of values corresponding to the provided keys.<br/>
    /// If a key is not found, its corresponding value in the list will be <see langword="null" />.
    /// </returns>
    /// <seealso href="https://valkey.io/commands/mget/">valkey.io</seealso>
    Task<GlideString?[]> StringGet(GlideString[] keys);

    /// <summary>
    /// Sets the given keys to their respective values.
    /// </summary>
    /// <remarks>
    /// <example>
    /// <code>
    /// KeyValuePair&lt;GlideString, GlideString&gt;[] values = [
    ///     new("key1", "value1"),
    ///     new("key2", "value2")
    /// ];
    /// string result = await client.StringSet(values);
    /// Console.WriteLine(result); // Output: "OK"
    /// </code>
    /// </example>
    /// </remarks>
    /// <param name="values">An array of key-value pairs to set.</param>
    /// <returns>A simple <c>"OK"</c> response.</returns>
    /// <seealso href="https://valkey.io/commands/mset/">valkey.io</seealso>
    Task<string> StringSet(KeyValuePair<GlideString, GlideString>[] values);

    /// <summary>
    /// Returns the substring of the string value stored at key, determined by the offsets 
    /// start and end (both are inclusive).
    /// </summary>
    /// <remarks>
    /// <example>
    /// <code>
    /// await client.StringSet("key", "Hello World");
    /// GlideString result = await client.StringGetRange("key", 0, 4);
    /// Console.WriteLine(result.ToString()); // Output: "Hello"
    /// </code>
    /// </example>
    /// </remarks>
    /// <param name="key">The key of the string.</param>
    /// <param name="start">The starting offset.</param>
    /// <param name="end">The ending offset.</param>
    /// <returns>
    /// A substring extracted from the value stored at key.<br/>
    /// An empty string is returned if the key does not exist or if the start and end offsets are out of range.
    /// </returns>
    /// <seealso href="https://valkey.io/commands/getrange/">valkey.io</seealso>
    Task<GlideString> StringGetRange(GlideString key, long start, long end);

    /// <summary>
    /// Overwrites part of the string stored at key, starting at the specified offset, 
    /// for the entire length of value.
    /// </summary>
    /// <remarks>
    /// <example>
    /// <code>
    /// await client.StringSet("key", "Hello World");
    /// long newLength = await client.StringSetRange("key", 6, "Valkey");
    /// Console.WriteLine(newLength); // Output: 12
    /// 
    /// GlideString? value = await client.StringGet("key");
    /// Console.WriteLine(value?.ToString()); // Output: "Hello Valkey"
    /// </code>
    /// </example>
    /// </remarks>
    /// <param name="key">The key of the string to update.</param>
    /// <param name="offset">The position in the string where value should be written.</param>
    /// <param name="value">The string written with offset.</param>
    /// <returns>The length of the string stored at key after it was modified.</returns>
    /// <seealso href="https://valkey.io/commands/setrange/">valkey.io</seealso>
    Task<long> StringSetRange(GlideString key, long offset, GlideString value);

    /// <summary>
    /// Returns the length of the string value stored at key.
    /// </summary>
    /// <remarks>
    /// <example>
    /// <code>
    /// await client.StringSet("key", "Hello World");
    /// long length = await client.StringLength("key");
    /// Console.WriteLine(length); // Output: 11
    /// </code>
    /// </example>
    /// </remarks>
    /// <param name="key">The key to check its length.</param>
    /// <returns>
    /// The length of the string value stored at key.<br/>
    /// If key does not exist, it is treated as an empty string, and the command returns <c>0</c>.
    /// </returns>
    /// <seealso href="https://valkey.io/commands/strlen/">valkey.io</seealso>
    Task<long> StringLength(GlideString key);
}
