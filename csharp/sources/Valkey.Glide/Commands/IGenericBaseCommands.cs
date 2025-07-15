// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Commands.Options;

namespace Valkey.Glide.Commands;

/// <summary>
/// Supports commands for the "Generic Commands" group for standalone and cluster clients.
/// <br/>
/// See more on <see href="https://valkey.io/commands/?group=generic">valkey.io</see>.
/// </summary>
public interface IGenericBaseCommands
{
    /// <summary>
    /// Removes the specified key from the database. A key is ignored if it does not exist.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/del"/>
    /// <param name="key">The key to delete.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns><see langword="true"/> if the key was removed.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// bool result = await client.KeyDeleteAsync(key);
    /// </code>
    /// </example>
    /// </remarks>
    Task<bool> KeyDeleteAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Removes the specified keys from the database. A key is ignored if it does not exist.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/del"/>
    /// <note>When in cluster mode, if keys in keys map to different hash slots, the command
    /// will be split across these slots and executed separately for each. This means the command
    /// is atomic only at the slot level. If one or more slot-specific requests fail, the entire
    /// call will return the first encountered error, even though some requests may have succeeded
    /// while others did not. If this behavior impacts your application logic, consider splitting
    /// the request into sub-requests per slot to ensure atomicity.</note>
    /// <param name="keys">The keys to delete.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>The number of keys that were removed.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// long result = await client.KeyDeleteAsync([key1, key2]);
    /// </code>
    /// </example>
    /// </remarks>
    Task<long> KeyDeleteAsync(ValkeyKey[] keys, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Unlinks (removes) the specified key from the database. A key is ignored if it does not exist.
    /// This command is similar to <seealso cref="KeyDeleteAsync(ValkeyKey, CommandFlags)"/>, however, this command does not block the server.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/unlink"/>
    /// <param name="key">The key to unlink.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns><see langword="true"/> if the key was unlinked.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// bool result = await client.KeyUnlinkAsync(key);
    /// </code>
    /// </example>
    /// </remarks>
    Task<bool> KeyUnlinkAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Unlinks (removes) the specified key from the database. A key is ignored if it does not exist.
    /// This command is similar to <seealso cref="KeyDeleteAsync(ValkeyKey[], CommandFlags)"/>, however, this command does not block the server.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/unlink"/>
    /// <note>When in cluster mode, if keys in keys map to different hash slots, the command
    /// will be split across these slots and executed separately for each. This means the command
    /// is atomic only at the slot level. If one or more slot-specific requests fail, the entire
    /// call will return the first encountered error, even though some requests may have succeeded
    /// while others did not. If this behavior impacts your application logic, consider splitting
    /// the request into sub-requests per slot to ensure atomicity.</note>
    /// <param name="keys">The keys to unlink.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>The number of keys that were unlinked.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// long result = await client.KeyUnlinkAsync([key1, key2]);
    /// </code>
    /// </example>
    /// </remarks>
    Task<long> KeyUnlinkAsync(ValkeyKey[] keys, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Returns if key exists.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/exists"/>
    /// <param name="key">The key to check.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns><see langword="true"/> if the key exists. <see langword="false"/> if the key does not exist.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// bool result = await client.KeyExistsAsync(key);
    /// </code>
    /// </example>
    /// </remarks>
    Task<bool> KeyExistsAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Returns the number of keys that exist in the database.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/exists"/>
    /// <note>When in cluster mode, if keys in keys map to different hash slots, the command
    /// will be split across these slots and executed separately for each. This means the command
    /// is atomic only at the slot level. If one or more slot-specific requests fail, the entire
    /// call will return the first encountered error, even though some requests may have succeeded
    /// while others did not. If this behavior impacts your application logic, consider splitting
    /// the request into sub-requests per slot to ensure atomicity.</note>
    /// <param name="keys">The keys to check.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>The number of existing keys.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// long result = await client.KeyExistsAsync([key1, key2]);
    /// </code>
    /// </example>
    /// </remarks>
    Task<long> KeyExistsAsync(ValkeyKey[] keys, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Set a timeout on key. After the timeout has expired, the key will automatically be deleted.<br/>
    /// If key already has an existing expire set, the time to live is updated to the new value.
    /// If expiry is a non-positive value, the key will be deleted rather than expired.
    /// The timeout will only be cleared by commands that delete or overwrite the contents of key.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/expire"/>
    /// <param name="key">The key to expire.</param>
    /// <param name="expiry">Duration for the key to expire.</param>
    /// <param name="when">The option to set expiry.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns><see langword="true"/> if the timeout was set. <see langword="false"/> if key does not exist or the timeout could not be set.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// bool result = await client.KeyExpireAsync(key, TimeSpan.FromSeconds(10));
    /// </code>
    /// </example>
    /// </remarks>
    Task<bool> KeyExpireAsync(ValkeyKey key, TimeSpan? expiry, ExpireWhen when = ExpireWhen.Always, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Sets a timeout on key. It takes an absolute Unix timestamp (seconds since January 1, 1970) instead of
    /// specifying the duration. A timestamp in the past will delete the key immediately. After the timeout has
    /// expired, the key will automatically be deleted.<br/>
    /// If key already has an existing expire set, the time to live is updated to the new value.
    /// The timeout will only be cleared by commands that delete or overwrite the contents of key
    /// If key already has an existing expire set, the time to live is updated to the new value.
    /// If expiry is a non-positive value, the key will be deleted rather than expired.
    /// The timeout will only be cleared by commands that delete or overwrite the contents of key.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/expireat"/>
    /// <param name="key">The key to expire.</param>
    /// <param name="expiry">The timestamp for expiry.</param>
    /// <param name="when">In Valkey 7+, the option to set expiry.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns><see langword="true"/> if the timeout was set. <see langword="false"/> if key does not exist or the timeout could not be set.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// bool result = await client.KeyExpireAsync(key, DateTime.UtcNow.AddMinutes(5));
    /// </code>
    /// </example>
    /// </remarks>
    Task<bool> KeyExpireAsync(ValkeyKey key, DateTime? expiry, ExpireWhen when = ExpireWhen.Always, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Returns the remaining time to live of a key that has a timeout.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/ttl"/>
    /// <param name="key">The key to return its timeout.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>TTL, or <see langword="null"/> when key does not exist or key exists but has no associated expiration.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// TimeSpan? result = await client.KeyTimeToLiveAsync(key);
    /// </code>
    /// </example>
    /// </remarks>
    Task<TimeSpan?> KeyTimeToLiveAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Returns the ValkeyType of the value stored at key.
    /// The different types that can be returned are: String, List, Set, SortedSet, Hash and Stream.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/type"/>
    /// <param name="key">The key to check its data type.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>Type of key, or none when key does not exist.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// ValkeyType result = await client.KeyTypeAsync(key);
    /// </code>
    /// </example>
    /// </remarks>
    Task<ValkeyType> KeyTypeAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Renames key to newKey.
    /// If newKey already exists it is overwritten.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/rename"/>
    /// <note>When in cluster mode, both key and newKey must map to the same hash slot.</note>
    /// <param name="key">The key to rename.</param>
    /// <param name="newKey">The new name of the key.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns><see langword="true"/> if the key was renamed. If key does not exist, an error is thrown.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// bool result = await client.KeyRenameAsync(oldKey, newKey);
    /// </code>
    /// </example>
    /// </remarks>
    Task<bool> KeyRenameAsync(ValkeyKey key, ValkeyKey newKey, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Renames key to newKey if newKey does not yet exist.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/renamenx"/>
    /// <note>When in cluster mode, both key and newKey must map to the same hash slot.</note>
    /// <param name="key">The key to rename.</param>
    /// <param name="newKey">The new name of the key.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns><see langword="true"/> if the key was renamed, <see langword="false"/> if newKey already exists.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// bool result = await client.KeyRenameNXAsync(oldKey, newKey);
    /// </code>
    /// </example>
    /// </remarks>
    Task<bool> KeyRenameNXAsync(ValkeyKey key, ValkeyKey newKey, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Removes the existing timeout on key, turning the key from volatile
    /// (a key with an expire set) to persistent (a key that will never expire as no timeout is associated).
    /// </summary>
    /// <seealso href="https://valkey.io/commands/persist"/>
    /// <param name="key">The key to remove the existing timeout on.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns><see langword="true"/> if the timeout was removed. <see langword="false"/> if key does not exist or does not have an associated timeout.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// bool result = await client.KeyPersistAsync(key);
    /// </code>
    /// </example>
    /// </remarks>
    Task<bool> KeyPersistAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Serializes the value stored at key in a Valkey-specific format.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/dump"/>
    /// <param name="key">The key to serialize.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>
    /// The serialized value of the data stored at key.
    /// If key does not exist, <see langword="null"/> will be returned.
    /// </returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// byte[]? result = await client.KeyDumpAsync(key);
    /// </code>
    /// </example>
    /// </remarks>
    Task<byte[]?> KeyDumpAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Creates a key associated with a value that is obtained by
    /// deserializing the provided serialized value (obtained via <seealso cref="KeyDumpAsync"/>).
    /// This method takes a duration for the expiry.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/restore"/>
    /// <note>When in cluster mode, both source and destination must map to the same hash slot.</note>
    /// <param name="key">The key to create.</param>
    /// <param name="value">The serialized value to deserialize and assign to key.</param>
    /// <param name="expiry">The expiry to set as a duration. Default value is set to persist.</param>
    /// <param name="restoreOptions">Set restore options with replace and absolute TTL modifiers, object idletime and frequency.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <remarks>
    /// <example>
    /// <code>
    /// await client.KeyRestoreAsync(key, serializedValue, TimeSpan.FromMinutes(5));
    /// </code>
    /// </example>
    /// </remarks>
    Task KeyRestoreAsync(ValkeyKey key, byte[] value, TimeSpan? expiry = null, RestoreOptions? restoreOptions = null, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Creates a key associated with a value that is obtained by
    /// deserializing the provided serialized value (obtained via <seealso cref="KeyDumpAsync"/>).
    /// This method takes an exact date and time for the expiry.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/restore"/>
    /// <note>When in cluster mode, both source and destination must map to the same hash slot.</note>
    /// <param name="key">The key to create.</param>
    /// <param name="value">The serialized value to deserialize and assign to key.</param>
    /// <param name="expiry">The expiry to set as a date and time. Default value is set to persist.</param>
    /// <param name="restoreOptions">Set restore options with replace and absolute TTL modifiers, object idletime and frequency.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <remarks>
    /// <example>
    /// <code>
    /// await client.KeyRestoreAsync(key, serializedValue, TimeSpan.FromMinutes(5));
    /// </code>
    /// </example>
    /// </remarks>
    Task KeyRestoreDateTimeAsync(ValkeyKey key, byte[] value, DateTime? expiry = null, RestoreOptions? restoreOptions = null, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Alters the last access time of a key(s). A key is ignored if it does not exist.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/touch"/>
    /// <param name="key">The keys to update last access time.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns><see langword="true"/> if the key was touched, <see langword="false"/> otherwise.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// bool result = await client.KeyTouchAsync(key);
    /// </code>
    /// </example>
    /// </remarks>
    Task<bool> KeyTouchAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Alters the last access time of a key(s). A key is ignored if it does not exist.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/touch"/>
    /// <note>When in cluster mode, if keys in keys map to different hash slots, the command
    /// will be split across these slots and executed separately for each. This means the command
    /// is atomic only at the slot level. If one or more slot-specific requests fail, the entire
    /// call will return the first encountered error, even though some requests may have succeeded
    /// while others did not. If this behavior impacts your application logic, consider splitting
    /// the request into sub-requests per slot to ensure atomicity.</note>
    /// <param name="keys">The keys to update last access time.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>The number of keys that were updated.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// long result = await client.KeyTouchAsync([key1, key2]);
    /// </code>
    /// </example>
    /// </remarks>
    Task<long> KeyTouchAsync(ValkeyKey[] keys, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Copies the value stored at the source to the destination key. When
    /// replace is true, removes the destination key first if it already
    /// exists, otherwise performs no action.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/copy"/>
    /// <note>Since Valkey 6.2.0 and above.</note>
    /// <note>When in cluster mode, both sourceKey and destinationKey must map to the same hash slot.</note>
    /// <param name="sourceKey">The key to the source value.</param>
    /// <param name="destinationKey">The key where the value should be copied to.</param>
    /// <param name="replace">Whether to overwrite an existing values at destinationKey.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns><see langword="true"/> if sourceKey was copied. <see langword="false"/> if sourceKey was not copied.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// bool result = await client.KeyCopyAsync(sourceKey, destKey, replace: true);
    /// </code>
    /// </example>
    /// </remarks>
    Task<bool> KeyCopyAsync(ValkeyKey sourceKey, ValkeyKey destinationKey, bool replace = false, CommandFlags flags = CommandFlags.None);
}
