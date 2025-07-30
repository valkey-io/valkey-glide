// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Valkey.Glide.Commands;

/// <summary>
/// Supports commands for the "String Commands" group for standalone and cluster clients.
/// <br />
/// See more on <see href="https://valkey.io/commands/#string">valkey.io</see>.
/// </summary>
public interface IStringCommands
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
    /// Sets multiple keys to multiple values in a single operation based on a specified condition.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/mset/">valkey.io</seealso>
    /// <seealso href="https://valkey.io/commands/msetnx/">valkey.io</seealso>
    /// <note>In cluster mode, if keys in <paramref name="values"/> map to different hash slots, the command
    /// will be split across these slots and executed separately for each. This means the command
    /// is atomic only at the slot level. If one or more slot-specific requests fail, the entire
    /// call will return the first encountered error, even though some requests may have succeeded
    /// while others did not. If this behavior impacts your application logic, consider splitting
    /// the request into sub-requests per slot to ensure atomicity.</note>
    /// <param name="values">An array of key-value pairs to set.</param>
    /// <param name="when">The condition to specify. If specified to Not-Exists, sets multiple keys to values only if the key does not exist. Defaults to Always.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns><see langword="true"/> if all the keys were set, <see langword="false"/> if no key was set (at least one key already existed).</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// KeyValuePair&lt;ValkeyKey, ValkeyValue&gt;[] values = [
    ///     new("key1", "value1"),
    ///     new("key2", "value2")
    /// ];
    /// bool result = await client.StringSetAsync(values);
    /// Console.WriteLine(result); // Output: true (if neither key existed)
    /// </code>
    /// </example>
    /// </remarks>
    Task<bool> StringSetAsync(KeyValuePair<ValkeyKey, ValkeyValue>[] values, When when = When.Always, CommandFlags flags = CommandFlags.None);

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

    /// <summary>
    /// Appends a value to the string stored at key. If the key does not exist, it is created and set to an empty string before performing the operation.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/append/">valkey.io</seealso>
    /// <param name="key">The key of the string to append to.</param>
    /// <param name="value">The value to append to the string.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>
    /// The length of the string after the append operation.<br/>
    /// If key does not exist, it is treated as an empty string, and the command returns the length of the appended value.
    /// </returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// await client.StringSetAsync("key", "Hello");
    /// long newLength = await client.StringAppendAsync("key", " World");
    /// Console.WriteLine(newLength); // Output: 11
    /// 
    /// ValkeyValue value = await client.StringGetAsync("key");
    /// Console.WriteLine(value.ToString()); // Output: "Hello World"
    /// </code>
    /// </example>
    /// </remarks>
    Task<long> StringAppendAsync(ValkeyKey key, ValkeyValue value, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Decrements the number stored at key by one. If the key does not exist, it is set to 0 before performing the operation.
    /// An error is returned if the key contains a value of the wrong type or contains a string that is not representable as integer.
    /// This operation is limited to 64 bit signed integers.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/decr/">valkey.io</seealso>
    /// <param name="key">The key of the string to decrement.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>The value of key after the decrement.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// await client.StringSetAsync("key", "10");
    /// long newValue = await client.StringDecrementAsync("key");
    /// Console.WriteLine(newValue); // Output: 9
    /// </code>
    /// </example>
    /// </remarks>
    Task<long> StringDecrementAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Decrements the number stored at key by the specified decrement. If the key does not exist, it is set to 0 before performing the operation.
    /// An error is returned if the key contains a value of the wrong type or contains a string that is not representable as integer.
    /// This operation is limited to 64 bit signed integers.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/decrby/">valkey.io</seealso>
    /// <param name="key">The key of the string to decrement.</param>
    /// <param name="decrement">The amount to decrement by.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>The value of key after the decrement.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// await client.StringSetAsync("key", "10");
    /// long newValue = await client.StringDecrementAsync("key", 5);
    /// Console.WriteLine(newValue); // Output: 5
    /// </code>
    /// </example>
    /// </remarks>
    Task<long> StringDecrementAsync(ValkeyKey key, long decrement, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Increments the number stored at key by one. If the key does not exist, it is set to 0 before performing the operation.
    /// An error is returned if the key contains a value of the wrong type or contains a string that is not representable as integer.
    /// This operation is limited to 64 bit signed integers.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/incr/">valkey.io</seealso>
    /// <param name="key">The key of the string to increment.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>The value of key after the increment.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// await client.StringSetAsync("key", "10");
    /// long newValue = await client.StringIncrementAsync("key");
    /// Console.WriteLine(newValue); // Output: 11
    /// </code>
    /// </example>
    /// </remarks>
    Task<long> StringIncrementAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Increments the number stored at key by the specified increment. If the key does not exist, it is set to 0 before performing the operation.
    /// An error is returned if the key contains a value of the wrong type or contains a string that is not representable as integer.
    /// This operation is limited to 64 bit signed integers.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/incrby/">valkey.io</seealso>
    /// <param name="key">The key of the string to increment.</param>
    /// <param name="increment">The amount to increment by.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>The value of key after the increment.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// await client.StringSetAsync("key", "10");
    /// long newValue = await client.StringIncrementAsync("key", 5);
    /// Console.WriteLine(newValue); // Output: 15
    /// </code>
    /// </example>
    /// </remarks>
    Task<long> StringIncrementAsync(ValkeyKey key, long increment, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Increments the string representing a floating point number stored at key by the specified increment.
    /// If the key does not exist, it is set to 0 before performing the operation.
    /// An error is returned if the key contains a value of the wrong type or contains a string that is not representable as a floating point number.
    /// The precision of the output is fixed at 17 digits after the decimal point regardless of the actual internal precision of the computation.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/incrbyfloat/">valkey.io</seealso>
    /// <param name="key">The key of the string to increment.</param>
    /// <param name="increment">The amount to increment by.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>The value of key after the increment as a double precision floating point number.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// await client.StringSetAsync("key", "10.5");
    /// double newValue = await client.StringIncrementAsync("key", 0.1);
    /// Console.WriteLine(newValue); // Output: 10.6
    /// </code>
    /// </example>
    /// </remarks>
    Task<double> StringIncrementAsync(ValkeyKey key, double increment, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Get the value of key and delete the key. If the key does not exist the special value <see cref="ValkeyValue.Null"/> is returned.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/getdel/">valkey.io</seealso>
    /// <param name="key">The key to get and delete.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>The value of key, or <see cref="ValkeyValue.Null"/> when key does not exist.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// await client.StringSetAsync("key", "value");
    /// ValkeyValue result = await client.StringGetDeleteAsync("key");
    /// Console.WriteLine(result.ToString()); // Output: "value"
    /// 
    /// ValkeyValue result2 = await client.StringGetAsync("key");
    /// Console.WriteLine(result2.IsNull); // Output: true (key was deleted)
    /// </code>
    /// </example>
    /// </remarks>
    Task<ValkeyValue> StringGetDeleteAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Gets the string value associated with the key and optionally update its (relative) expiry.
    /// If the key does not exist, the result will be <see cref="ValkeyValue.Null"/>.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/getex/">valkey.io</seealso>
    /// <note>Since Valkey 6.2.0 and above.</note>
    /// <param name="key">The key to be retrieved from the database.</param>
    /// <param name="expiry">The expiry to set. <see langword="null"/> will remove expiry.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>The value of key, or <see cref="ValkeyValue.Null"/> when key does not exist.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// await client.StringSetAsync("key", "value");
    /// ValkeyValue result = await client.StringGetSetExpiryAsync("key", TimeSpan.FromSeconds(10));
    /// Console.WriteLine(result.ToString()); // Output: "value"
    /// </code>
    /// </example>
    /// </remarks>
    Task<ValkeyValue> StringGetSetExpiryAsync(ValkeyKey key, TimeSpan? expiry, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Gets the string value associated with the key and optionally update its (absolute) expiry.
    /// If the key does not exist, the result will be <see cref="ValkeyValue.Null"/>.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/getex/">valkey.io</seealso>
    /// <note>Since Valkey 6.2.0 and above.</note>
    /// <param name="key">The key to be retrieved from the database.</param>
    /// <param name="expiry">The exact date and time to expire at.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>The value of key, or <see cref="ValkeyValue.Null"/> when key does not exist.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// await client.StringSetAsync("key", "value");
    /// ValkeyValue result = await client.StringGetSetExpiryAsync("key", DateTime.UtcNow.AddMinutes(5));
    /// Console.WriteLine(result.ToString()); // Output: "value"
    /// </code>
    /// </example>
    /// </remarks>
    Task<ValkeyValue> StringGetSetExpiryAsync(ValkeyKey key, DateTime expiry, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Returns the longest common subsequence between the values at <paramref name="first"/> and <paramref name="second"/>,
    /// returning a string containing the common sequence.
    /// Note that this is different than the longest common string algorithm,
    /// since matching characters in the string does not need to be contiguous.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/lcs/">valkey.io</seealso>
    /// <note>Since Valkey 7.0 and above.</note>
    /// <note>When in cluster mode, both <paramref name="first"/> and <paramref name="second"/> must map to the same hash slot.</note>
    /// <param name="first">The key that stores the first string.</param>
    /// <param name="second">The key that stores the second string.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>A string (sequence of characters) of the LCS match.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// await client.StringSetAsync("key1", "abcdef");
    /// await client.StringSetAsync("key2", "acef");
    /// string? result = await client.StringLongestCommonSubsequenceAsync("key1", "key2");
    /// Console.WriteLine(result); // Output: "acef"
    /// </code>
    /// </example>
    /// </remarks>
    Task<string?> StringLongestCommonSubsequenceAsync(ValkeyKey first, ValkeyKey second, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Returns the longest common subsequence between the values at <paramref name="first"/> and <paramref name="second"/>,
    /// returning the length of the common sequence.
    /// Note that this is different than the longest common string algorithm,
    /// since matching characters in the string does not need to be contiguous.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/lcs/">valkey.io</seealso>
    /// <note>Since Valkey 7.0 and above.</note>
    /// <note>When in cluster mode, both <paramref name="first"/> and <paramref name="second"/> must map to the same hash slot.</note>
    /// <param name="first">The key that stores the first string.</param>
    /// <param name="second">The key that stores the second string.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>The length of the LCS match.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// await client.StringSetAsync("key1", "abcdef");
    /// await client.StringSetAsync("key2", "acef");
    /// long length = await client.StringLongestCommonSubsequenceLengthAsync("key1", "key2");
    /// Console.WriteLine(length); // Output: 4
    /// </code>
    /// </example>
    /// </remarks>
    Task<long> StringLongestCommonSubsequenceLengthAsync(ValkeyKey first, ValkeyKey second, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Returns the longest common subsequence between the values at <paramref name="first"/> and <paramref name="second"/>,
    /// returning a list of all common sequences with their positions and match information.
    /// Note that this is different than the longest common string algorithm,
    /// since matching characters in the string does not need to be contiguous.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/lcs/">valkey.io</seealso>
    /// <param name="first">The key that stores the first string.</param>
    /// <param name="second">The key that stores the second string.</param>
    /// <param name="minLength">Can be used to restrict the list of matches to the ones of a given minimum length. Defaults to 0.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>The result of LCS algorithm, containing match positions and lengths based on the given parameters.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// await client.StringSetAsync("key1", "abcdefghijk");
    /// await client.StringSetAsync("key2", "b1fh");
    /// LCSMatchResult result = await client.StringLongestCommonSubsequenceWithMatchesAsync("key1", "key2");
    /// Console.WriteLine($"LCS Length: {result.LongestMatchLength}");
    /// foreach (var match in result.Matches)
    /// {
    ///     Console.WriteLine($"Match at positions: first[{match.FirstStringIndex}], second[{match.SecondStringIndex}], length: {match.Length}");
    /// }
    /// </code>
    /// </example>
    /// </remarks>
    Task<LCSMatchResult> StringLongestCommonSubsequenceWithMatchesAsync(ValkeyKey first, ValkeyKey second, long minLength = 0, CommandFlags flags = CommandFlags.None);
}
