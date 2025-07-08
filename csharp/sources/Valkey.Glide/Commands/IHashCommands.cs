// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Valkey.Glide.Commands;

/// <summary>
/// Supports commands for the "Hash Commands" group for standalone and cluster clients.
/// <br/>
/// See more on <see href="https://valkey.io/commands/?group=hash#hash">valkey.io</see>.
/// </summary>
public interface IHashCommands
{
    /// <summary>
    /// Returns the value associated with field in the hash stored at key.
    /// </summary>
    /// <param name="key">The key of the hash.</param>
    /// <param name="hashField">The field in the hash to get.</param>
    /// <param name="ignored">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>The value associated with field, or <see cref="ValkeyValue.Null"/> when field is not present in the hash or key does not exist.</returns>
    /// <remarks>
    /// <seealso href="https://valkey.io/commands/hget"/>
    /// <example>
    /// <code>
    /// ValkeyValue value = await client.HashGetAsync(key, hashField);
    /// </code>
    /// </example>
    /// </remarks>
    Task<ValkeyValue> HashGetAsync(ValkeyKey key, ValkeyValue hashField, CommandFlags ignored = CommandFlags.None);

    /// <summary>
    /// Returns the values associated with the specified fields in the hash stored at key.
    /// For every field that does not exist in the hash, a <see cref="ValkeyValue.Null"/> value is returned.
    /// Because non-existing keys are treated as empty hashes, running HMGET against a non-existing key will return a list of <see cref="ValkeyValue.Null"/> values.
    /// </summary>
    /// <param name="key">The key of the hash.</param>
    /// <param name="hashFields">The fields in the hash to get.</param>
    /// <param name="ignored">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>List of values associated with the given fields, in the same order as they are requested.</returns>
    /// <remarks>
    /// <seealso href="https://valkey.io/commands/hmget"/>
    /// <example>
    /// <code>
    /// ValkeyValue[] values = await client.HashGetAsync(key, new ValkeyValue[] { field1, field2 });
    /// </code>
    /// </example>
    /// </remarks>
    Task<ValkeyValue[]> HashGetAsync(ValkeyKey key, ValkeyValue[] hashFields, CommandFlags ignored = CommandFlags.None);

    /// <summary>
    /// Returns all fields and values of the hash stored at key.
    /// </summary>
    /// <param name="key">The key of the hash to get all entries from.</param>
    /// <param name="ignored">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>List of fields and their values stored in the hash, or an empty list when key does not exist.</returns>
    /// <remarks>
    /// <seealso href="https://valkey.io/commands/hgetall"/>
    /// <example>
    /// <code>
    /// HashEntry[] entries = await client.HashGetAllAsync(key);
    /// </code>
    /// </example>
    /// </remarks>
    Task<HashEntry[]> HashGetAllAsync(ValkeyKey key, CommandFlags ignored = CommandFlags.None);

    /// <summary>
    /// Sets the specified fields to their respective values in the hash stored at key.
    /// This command overwrites any specified fields that already exist in the hash, leaving other unspecified fields untouched.
    /// If key does not exist, a new key holding a hash is created.
    /// </summary>
    /// <param name="key">The key of the hash.</param>
    /// <param name="hashFields">The entries to set in the hash.</param>
    /// <param name="ignored">The flags to use for this operation. Currently flags are ignored.</param>
    /// <remarks>
    /// <seealso href="https://valkey.io/commands/hmset"/>
    /// <example>
    /// <code>
    /// await client.HashSetAsync(key, new HashEntry[] { new HashEntry(field1, value1), new HashEntry(field2, value2) });
    /// </code>
    /// </example>
    /// </remarks>
    Task HashSetAsync(ValkeyKey key, HashEntry[] hashFields, CommandFlags ignored = CommandFlags.None);

    /// <summary>
    /// Sets field in the hash stored at key to value.
    /// If key does not exist, a new key holding a hash is created.
    /// If field already exists in the hash, it is overwritten.
    /// </summary>
    /// <param name="key">The key of the hash.</param>
    /// <param name="hashField">The field to set in the hash.</param>
    /// <param name="value">The value to set.</param>
    /// <param name="when">Which conditions under which to set the field value (defaults to always).</param>
    /// <param name="ignored">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns><see langword="true"/> if field is a new field in the hash and value was set, <see langword="false"/> if field already exists in the hash and the value was updated.</returns>
    /// <remarks>
    /// <seealso href="https://valkey.io/commands/hset"/>
    /// <seealso href="https://valkey.io/commands/hsetnx"/>
    /// <example>
    /// <code>
    /// bool isNewField = await client.HashSetAsync(key, hashField, value);
    /// </code>
    /// </example>
    /// </remarks>
    Task<bool> HashSetAsync(ValkeyKey key, ValkeyValue hashField, ValkeyValue value, When when = When.Always, CommandFlags ignored = CommandFlags.None);

    /// <summary>
    /// Removes the specified field from the hash stored at key.
    /// </summary>
    /// <param name="key">The key of the hash.</param>
    /// <param name="hashField">The field to remove from the hash.</param>
    /// <param name="ignored">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns><see langword="true"/> if the field was removed, <see langword="false"/> if the field was not found or the key does not exist.</returns>
    /// <remarks>
    /// <seealso href="https://valkey.io/commands/hdel"/>
    /// <example>
    /// <code>
    /// bool removed = await client.HashDeleteAsync(key, hashField);
    /// </code>
    /// </example>
    /// </remarks>
    Task<bool> HashDeleteAsync(ValkeyKey key, ValkeyValue hashField, CommandFlags ignored = CommandFlags.None);

    /// <summary>
    /// Removes the specified fields from the hash stored at key.
    /// </summary>
    /// <param name="key">The key of the hash.</param>
    /// <param name="hashFields">The fields to remove from the hash.</param>
    /// <param name="ignored">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>The number of fields that were removed from the hash, not including specified but non-existing fields.</returns>
    /// <remarks>
    /// <seealso href="https://valkey.io/commands/hdel"/>
    /// <example>
    /// <code>
    /// long removedCount = await client.HashDeleteAsync(key, new ValkeyValue[] { field1, field2 });
    /// </code>
    /// </example>
    /// </remarks>
    Task<long> HashDeleteAsync(ValkeyKey key, ValkeyValue[] hashFields, CommandFlags ignored = CommandFlags.None);

    /// <summary>
    /// Returns if field is an existing field in the hash stored at key.
    /// </summary>
    /// <param name="key">The key of the hash.</param>
    /// <param name="hashField">The field to check in the hash.</param>
    /// <param name="ignored">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns><see langword="true"/> if the hash contains the field, <see langword="false"/> if the hash does not contain the field or key does not exist.</returns>
    /// <remarks>
    /// <seealso href="https://valkey.io/commands/hexists"/>
    /// <example>
    /// <code>
    /// bool exists = await client.HashExistsAsync(key, hashField);
    /// </code>
    /// </example>
    /// </remarks>
    Task<bool> HashExistsAsync(ValkeyKey key, ValkeyValue hashField, CommandFlags ignored = CommandFlags.None);

    /// <summary>
    /// Returns the number of fields contained in the hash stored at key.
    /// </summary>
    /// <param name="key">The key of the hash.</param>
    /// <param name="ignored">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>The number of fields in the hash, or 0 when key does not exist.</returns>
    /// <remarks>
    /// <seealso href="https://valkey.io/commands/hlen"/>
    /// <example>
    /// <code>
    /// long fieldCount = await client.HashLengthAsync(key);
    /// </code>
    /// </example>
    /// </remarks>
    Task<long> HashLengthAsync(ValkeyKey key, CommandFlags ignored = CommandFlags.None);

    /// <summary>
    /// Returns the string length of the value associated with field in the hash stored at key.
    /// </summary>
    /// <param name="key">The key of the hash.</param>
    /// <param name="hashField">The field containing the string.</param>
    /// <param name="ignored">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>The length of the string at field, or 0 when field is not present in the hash or key does not exist.</returns>
    /// <remarks>
    /// <seealso href="https://valkey.io/commands/hstrlen"/>
    /// <example>
    /// <code>
    /// long length = await client.HashStringLengthAsync(key, hashField);
    /// </code>
    /// </example>
    /// </remarks>
    Task<long> HashStringLengthAsync(ValkeyKey key, ValkeyValue hashField, CommandFlags ignored = CommandFlags.None);

    /// <summary>
    /// Returns all values in the hash stored at key.
    /// </summary>
    /// <param name="key">The key of the hash.</param>
    /// <param name="ignored">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>List of values in the hash, or an empty list when key does not exist.</returns>
    /// <remarks>
    /// <seealso href="https://valkey.io/commands/hvals"/>
    /// <example>
    /// <code>
    /// ValkeyValue[] values = await client.HashValuesAsync(key);
    /// </code>
    /// </example>
    /// </remarks>
    Task<ValkeyValue[]> HashValuesAsync(ValkeyKey key, CommandFlags ignored = CommandFlags.None);

    /// <summary>
    /// Gets a random field from the hash at key.
    /// </summary>
    /// <param name="key">The key of the hash.</param>
    /// <param name="ignored">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>A random hash field name or <see cref="ValkeyValue.Null"/> if the hash does not exist.</returns>
    /// <remarks>
    /// <seealso href="https://valkey.io/commands/hrandfield"/>
    /// <example>
    /// <code>
    /// ValkeyValue randomField = await client.HashRandomFieldAsync(key);
    /// </code>
    /// </example>
    /// </remarks>
    Task<ValkeyValue> HashRandomFieldAsync(ValkeyKey key, CommandFlags ignored = CommandFlags.None);

    /// <summary>
    /// Gets count field names from the hash at key.
    /// </summary>
    /// <param name="key">The key of the hash.</param>
    /// <param name="count">The number of fields to return.</param>
    /// <param name="ignored">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>An array of hash field names of size of at most count, or an empty array if the hash does not exist.</returns>
    /// <remarks>
    /// <seealso href="https://valkey.io/commands/hrandfield"/>
    /// <example>
    /// <code>
    /// ValkeyValue[] randomFields = await client.HashRandomFieldsAsync(key, 3);
    /// </code>
    /// </example>
    /// </remarks>
    Task<ValkeyValue[]> HashRandomFieldsAsync(ValkeyKey key, long count, CommandFlags ignored = CommandFlags.None);

    /// <summary>
    /// Gets count field names and values from the hash at key.
    /// </summary>
    /// <param name="key">The key of the hash.</param>
    /// <param name="count">The number of fields to return.</param>
    /// <param name="ignored">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>An array of hash entries of size of at most count, or an empty array if the hash does not exist.</returns>
    /// <remarks>
    /// <seealso href="https://valkey.io/commands/hrandfield"/>
    /// <example>
    /// <code>
    /// HashEntry[] randomEntries = await client.HashRandomFieldsWithValuesAsync(key, 3);
    /// </code>
    /// </example>
    /// </remarks>
    Task<HashEntry[]> HashRandomFieldsWithValuesAsync(ValkeyKey key, long count, CommandFlags ignored = CommandFlags.None);
}
