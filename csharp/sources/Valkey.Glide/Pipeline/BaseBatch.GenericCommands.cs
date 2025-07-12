// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Commands.Options;

using static Valkey.Glide.Internals.Request;

namespace Valkey.Glide.Pipeline;

/// <summary>
/// Generic commands for BaseBatch.
/// </summary>
public abstract partial class BaseBatch<T>
{
    /// <inheritdoc cref="IBatchGenericCommands.KeyDelete(ValkeyKey)" />
    public T KeyDelete(ValkeyKey key) => AddCmd(KeyDeleteAsync(key));

    /// <inheritdoc cref="IBatchGenericCommands.KeyDelete(ValkeyKey[])" />
    public T KeyDelete(ValkeyKey[] keys) => AddCmd(KeyDeleteAsync(keys));

    /// <inheritdoc cref="IBatchGenericCommands.KeyUnlink(ValkeyKey)" />
    public T KeyUnlink(ValkeyKey key) => AddCmd(KeyUnlinkAsync(key));

    /// <inheritdoc cref="IBatchGenericCommands.KeyUnlink(ValkeyKey[])" />
    public T KeyUnlink(ValkeyKey[] keys) => AddCmd(KeyUnlinkAsync(keys));

    /// <inheritdoc cref="IBatchGenericCommands.KeyExists(ValkeyKey)" />
    public T KeyExists(ValkeyKey key) => AddCmd(KeyExistsAsync(key));

    /// <inheritdoc cref="IBatchGenericCommands.KeyExists(ValkeyKey[])" />
    public T KeyExists(ValkeyKey[] keys) => AddCmd(KeyExistsAsync(keys));

    /// <inheritdoc cref="IBatchGenericCommands.KeyExpire(ValkeyKey, TimeSpan?, ExpireWhen)" />
    public T KeyExpire(ValkeyKey key, TimeSpan? expiry, ExpireWhen when = ExpireWhen.Always) => AddCmd(KeyExpireAsync(key, expiry, when));

    /// <inheritdoc cref="IBatchGenericCommands.KeyExpire(ValkeyKey, DateTime?, ExpireWhen)" />
    public T KeyExpire(ValkeyKey key, DateTime? expiry, ExpireWhen when = ExpireWhen.Always) => AddCmd(KeyExpireAsync(key, expiry, when));

    /// <inheritdoc cref="IBatchGenericCommands.KeyTimeToLive(ValkeyKey)" />
    public T KeyTimeToLive(ValkeyKey key) => AddCmd(KeyTimeToLiveAsync(key));

    /// <inheritdoc cref="IBatchGenericCommands.KeyType(ValkeyKey)" />
    public T KeyType(ValkeyKey key) => AddCmd(KeyTypeAsync(key));

    /// <inheritdoc cref="IBatchGenericCommands.KeyRename(ValkeyKey, ValkeyKey)" />
    public T KeyRename(ValkeyKey key, ValkeyKey newKey) => AddCmd(KeyRenameAsync(key, newKey));

    /// <inheritdoc cref="IBatchGenericCommands.KeyRenameNX(ValkeyKey, ValkeyKey)" />
    public T KeyRenameNX(ValkeyKey key, ValkeyKey newKey) => AddCmd(KeyRenameNXAsync(key, newKey));

    /// <inheritdoc cref="IBatchGenericCommands.KeyPersist(ValkeyKey)" />
    public T KeyPersist(ValkeyKey key) => AddCmd(KeyPersistAsync(key));

    /// <inheritdoc cref="IBatchGenericCommands.KeyDump(ValkeyKey)" />
    public T KeyDump(ValkeyKey key) => AddCmd(KeyDumpAsync(key));

    /// <inheritdoc cref="IBatchGenericCommands.KeyRestore(ValkeyKey, byte[], TimeSpan?, RestoreOptions?)" />
    public T KeyRestore(ValkeyKey key, byte[] value, TimeSpan? expiry = null, RestoreOptions? restoreOptions = null) => AddCmd(KeyRestoreAsync(key, value, expiry, restoreOptions));

    /// <inheritdoc cref="IBatchGenericCommands.KeyRestoreDateTime(ValkeyKey, byte[], DateTime?, RestoreOptions?)" />
    public T KeyRestoreDateTime(ValkeyKey key, byte[] value, DateTime? expiry = null, RestoreOptions? restoreOptions = null) => AddCmd(KeyRestoreDateTimeAsync(key, value, expiry, restoreOptions));

    /// <inheritdoc cref="IBatchGenericCommands.KeyTouch(ValkeyKey)" />
    public T KeyTouch(ValkeyKey key) => AddCmd(KeyTouchAsync(key));

    /// <inheritdoc cref="IBatchGenericCommands.KeyTouch(ValkeyKey[])" />
    public T KeyTouch(ValkeyKey[] keys) => AddCmd(KeyTouchAsync(keys));

    /// <inheritdoc cref="IBatchGenericCommands.KeyCopy(ValkeyKey, ValkeyKey, bool)" />
    public T KeyCopy(ValkeyKey sourceKey, ValkeyKey destinationKey, bool replace = false) => AddCmd(KeyCopyAsync(sourceKey, destinationKey, replace));

    // Explicit interface implementations for IBatchGenericCommands
    IBatch IBatchGenericCommands.KeyDelete(ValkeyKey key) => KeyDelete(key);
    IBatch IBatchGenericCommands.KeyDelete(ValkeyKey[] keys) => KeyDelete(keys);
    IBatch IBatchGenericCommands.KeyUnlink(ValkeyKey key) => KeyUnlink(key);
    IBatch IBatchGenericCommands.KeyUnlink(ValkeyKey[] keys) => KeyUnlink(keys);
    IBatch IBatchGenericCommands.KeyExists(ValkeyKey key) => KeyExists(key);
    IBatch IBatchGenericCommands.KeyExists(ValkeyKey[] keys) => KeyExists(keys);
    IBatch IBatchGenericCommands.KeyExpire(ValkeyKey key, TimeSpan? expiry, ExpireWhen when) => KeyExpire(key, expiry, when);
    IBatch IBatchGenericCommands.KeyExpire(ValkeyKey key, DateTime? expiry, ExpireWhen when) => KeyExpire(key, expiry, when);
    IBatch IBatchGenericCommands.KeyTimeToLive(ValkeyKey key) => KeyTimeToLive(key);
    IBatch IBatchGenericCommands.KeyType(ValkeyKey key) => KeyType(key);
    IBatch IBatchGenericCommands.KeyRename(ValkeyKey key, ValkeyKey newKey) => KeyRename(key, newKey);
    IBatch IBatchGenericCommands.KeyRenameNX(ValkeyKey key, ValkeyKey newKey) => KeyRenameNX(key, newKey);
    IBatch IBatchGenericCommands.KeyPersist(ValkeyKey key) => KeyPersist(key);
    IBatch IBatchGenericCommands.KeyDump(ValkeyKey key) => KeyDump(key);
    IBatch IBatchGenericCommands.KeyRestore(ValkeyKey key, byte[] value, TimeSpan? expiry, RestoreOptions? restoreOptions) => KeyRestore(key, value, expiry, restoreOptions);
    IBatch IBatchGenericCommands.KeyRestoreDateTime(ValkeyKey key, byte[] value, DateTime? expiry, RestoreOptions? restoreOptions) => KeyRestoreDateTime(key, value, expiry, restoreOptions);
    IBatch IBatchGenericCommands.KeyTouch(ValkeyKey key) => KeyTouch(key);
    IBatch IBatchGenericCommands.KeyTouch(ValkeyKey[] keys) => KeyTouch(keys);
    IBatch IBatchGenericCommands.KeyCopy(ValkeyKey sourceKey, ValkeyKey destinationKey, bool replace) => KeyCopy(sourceKey, destinationKey, replace);
}
