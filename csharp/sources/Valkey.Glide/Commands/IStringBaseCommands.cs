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
    /// Sets the given <paramref name="key" /> with the given <paramref name="value" />.
    /// </summary>
    /// <remarks>
    /// <example>
    /// <code>
    /// string result = await client.Set(key, value);
    /// </code>
    /// </example>
    /// </remarks>
    /// <param name="key">The <paramref name="key" /> to store.</param>
    /// <param name="value">The value to store with the given <paramref name="key" />.</param>
    /// <returns>A simple <c>"OK"</c> response.</returns>
    Task<string> Set(GlideString key, GlideString value);

    /// <summary>
    /// Gets the value associated with the given <paramref name="key" />, or <see langword="null" /> if no such <paramref name="key" /> exists.
    /// </summary>
    /// <remarks>
    /// <example>
    /// <code>
    /// string value = await client.Get(key);
    /// </code>
    /// </example>
    /// </remarks>
    /// <param name="key">The <paramref name="key" /> to retrieve from the database.</param>
    /// <returns>
    /// If <paramref name="key" /> exists, returns the value of <paramref name="key" /> as a <see langword="string" />.<br/>
    /// Otherwise, returns <see langword="null" />.
    /// </returns>
    Task<GlideString?> Get(GlideString key);

    /// <summary>
    /// Retrieves the values of multiple <paramref name="keys" />.
    /// </summary>
    /// <remarks>
    /// <example>
    /// <code>
    /// GlideString[] keys = { "key1", "key2", "key3" };
    /// GlideString?[] values = await client.MGet(keys);
    /// Console.WriteLine(values[0]); // Output: value1 or null if key1 doesn't exist
    /// </code>
    /// </example>
    /// </remarks>
    /// <param name="keys">A list of keys to retrieve values for.</param>
    /// <returns>
    /// An array of values corresponding to the provided <paramref name="keys" />.<br/>
    /// If a key is not found, its corresponding value in the list will be <see langword="null" />.
    /// </returns>
    /// <seealso href="https://valkey.io/commands/mget/">valkey.io</seealso>
    Task<GlideString?[]> MGet(GlideString[] keys);

    /// <summary>
    /// Sets multiple keys to multiple values in a single operation.
    /// </summary>
    /// <remarks>
    /// <example>
    /// <code>
    /// var keyValueMap = new Dictionary&lt;GlideString, GlideString&gt;
    /// {
    ///     { "key1", "value1" },
    ///     { "key2", "value2" }
    /// };
    /// string result = await client.MSet(keyValueMap);
    /// Console.WriteLine(result); // Output: "OK"
    /// </code>
    /// </example>
    /// </remarks>
    /// <param name="keyValueMap">A key-value map consisting of keys and their respective values to set.</param>
    /// <returns>A simple <c>"OK"</c> response.</returns>
    /// <seealso href="https://valkey.io/commands/mset/">valkey.io</seealso>
    Task<string> MSet(Dictionary<GlideString, GlideString> keyValueMap);

    /// <summary>
    /// Returns the length of the string value stored at <paramref name="key" />.
    /// </summary>
    /// <remarks>
    /// <example>
    /// <code>
    /// await client.Set("key", "GLIDE");
    /// long len = await client.Strlen("key");
    /// Console.WriteLine(len); // Output: 5
    /// 
    /// long len2 = await client.Strlen("non_existing_key");
    /// Console.WriteLine(len2); // Output: 0
    /// </code>
    /// </example>
    /// </remarks>
    /// <param name="key">The <paramref name="key" /> to check its length.</param>
    /// <returns>
    /// The length of the string value stored at <paramref name="key" />.<br/>
    /// If <paramref name="key" /> does not exist, it is treated as an empty string, and the command returns <c>0</c>.
    /// </returns>
    /// <seealso href="https://valkey.io/commands/strlen/">valkey.io</seealso>
    Task<long> Strlen(GlideString key);

    /// <summary>
    /// Returns the substring of the string value stored at <paramref name="key" />, determined by the offsets 
    /// <paramref name="start" /> and <paramref name="end" /> (both are inclusive).
    /// Negative offsets can be used in order to provide an offset starting from the end of the string. 
    /// So -1 means the last character, -2 the penultimate and so forth.
    /// </summary>
    /// <remarks>
    /// <example>
    /// <code>
    /// await client.Set("key", "This is a string");
    /// string result = await client.GetRange("key", 0, 3);
    /// Console.WriteLine(result); // Output: "This"
    /// 
    /// string result2 = await client.GetRange("key", -3, -1);
    /// Console.WriteLine(result2); // Output: "ing"
    /// 
    /// string result3 = await client.GetRange("non_existing_key", 0, 5);
    /// Console.WriteLine(result3); // Output: ""
    /// </code>
    /// </example>
    /// </remarks>
    /// <param name="key">The <paramref name="key" /> of the string.</param>
    /// <param name="start">The starting offset.</param>
    /// <param name="end">The ending offset.</param>
    /// <returns>
    /// A substring extracted from the value stored at <paramref name="key" />.<br/>
    /// An empty string is returned if the <paramref name="key" /> does not exist or if the start and end offsets are out of range.
    /// </returns>
    /// <seealso href="https://valkey.io/commands/getrange/">valkey.io</seealso>
    Task<GlideString> GetRange(GlideString key, long start, long end);

    /// <summary>
    /// Overwrites part of the string stored at <paramref name="key" />, starting at the specified <paramref name="offset" />, 
    /// for the entire length of <paramref name="value" />.
    /// If the <paramref name="offset" /> is larger than the current length of the string at <paramref name="key" />, 
    /// the string is padded with zero bytes to make <paramref name="offset" /> fit. Creates the <paramref name="key" /> if it doesn't exist.
    /// </summary>
    /// <remarks>
    /// <example>
    /// <code>
    /// long len = await client.SetRange("key", 6, "GLIDE");
    /// Console.WriteLine(len); // Output: 11 (New key was created with length of 11 symbols)
    /// 
    /// string value = await client.Get("key");
    /// Console.WriteLine(value); // Output: "\0\0\0\0\0\0GLIDE" (The string was padded with zero bytes)
    /// </code>
    /// </example>
    /// </remarks>
    /// <param name="key">The <paramref name="key" /> of the string to update.</param>
    /// <param name="offset">The position in the string where <paramref name="value" /> should be written.</param>
    /// <param name="value">The string written with <paramref name="offset" />.</param>
    /// <returns>The length of the string stored at <paramref name="key" /> after it was modified.</returns>
    /// <seealso href="https://valkey.io/commands/setrange/">valkey.io</seealso>
    Task<long> SetRange(GlideString key, long offset, GlideString value);
}
