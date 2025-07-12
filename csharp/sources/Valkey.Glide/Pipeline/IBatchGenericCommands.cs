// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Commands;
using Valkey.Glide.Commands.Options;

namespace Valkey.Glide.Pipeline;

internal interface IBatchGenericCommands
{
    /// <inheritdoc cref="IGenericBaseCommands.KeyDeleteAsync(ValkeyKey, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="IGenericBaseCommands.KeyDeleteAsync(ValkeyKey, CommandFlags)" /></returns>
    IBatch KeyDelete(ValkeyKey key);

    /// <inheritdoc cref="IGenericBaseCommands.KeyDeleteAsync(ValkeyKey[], CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="IGenericBaseCommands.KeyDeleteAsync(ValkeyKey[], CommandFlags)" /></returns>
    IBatch KeyDelete(ValkeyKey[] keys);

    /// <inheritdoc cref="IGenericBaseCommands.KeyUnlinkAsync(ValkeyKey, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="IGenericBaseCommands.KeyUnlinkAsync(ValkeyKey, CommandFlags)" /></returns>
    IBatch KeyUnlink(ValkeyKey key);

    /// <inheritdoc cref="IGenericBaseCommands.KeyUnlinkAsync(ValkeyKey[], CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="IGenericBaseCommands.KeyUnlinkAsync(ValkeyKey[], CommandFlags)" /></returns>
    IBatch KeyUnlink(ValkeyKey[] keys);

    /// <inheritdoc cref="IGenericBaseCommands.KeyExistsAsync(ValkeyKey, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="IGenericBaseCommands.KeyExistsAsync(ValkeyKey, CommandFlags)" /></returns>
    IBatch KeyExists(ValkeyKey key);

    /// <inheritdoc cref="IGenericBaseCommands.KeyExistsAsync(ValkeyKey[], CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="IGenericBaseCommands.KeyExistsAsync(ValkeyKey[], CommandFlags)" /></returns>
    IBatch KeyExists(ValkeyKey[] keys);

    /// <inheritdoc cref="IGenericBaseCommands.KeyExpireAsync(ValkeyKey, TimeSpan?, ExpireWhen, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="IGenericBaseCommands.KeyExpireAsync(ValkeyKey, TimeSpan?, ExpireWhen, CommandFlags)" /></returns>
    IBatch KeyExpire(ValkeyKey key, TimeSpan? expiry, ExpireWhen when = ExpireWhen.Always);

    /// <inheritdoc cref="IGenericBaseCommands.KeyExpireAsync(ValkeyKey, DateTime?, ExpireWhen, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="IGenericBaseCommands.KeyExpireAsync(ValkeyKey, DateTime?, ExpireWhen, CommandFlags)" /></returns>
    IBatch KeyExpire(ValkeyKey key, DateTime? expiry, ExpireWhen when = ExpireWhen.Always);

    /// <inheritdoc cref="IGenericBaseCommands.KeyTimeToLiveAsync(ValkeyKey, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="IGenericBaseCommands.KeyTimeToLiveAsync(ValkeyKey, CommandFlags)" /></returns>
    IBatch KeyTimeToLive(ValkeyKey key);

    /// <inheritdoc cref="IGenericBaseCommands.KeyTypeAsync(ValkeyKey, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="IGenericBaseCommands.KeyTypeAsync(ValkeyKey, CommandFlags)" /></returns>
    IBatch KeyType(ValkeyKey key);

    /// <inheritdoc cref="IGenericBaseCommands.KeyRenameAsync(ValkeyKey, ValkeyKey, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="IGenericBaseCommands.KeyRenameAsync(ValkeyKey, ValkeyKey, CommandFlags)" /></returns>
    IBatch KeyRename(ValkeyKey key, ValkeyKey newKey);

    /// <inheritdoc cref="IGenericBaseCommands.KeyRenameNXAsync(ValkeyKey, ValkeyKey, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="IGenericBaseCommands.KeyRenameNXAsync(ValkeyKey, ValkeyKey, CommandFlags)" /></returns>
    IBatch KeyRenameNX(ValkeyKey key, ValkeyKey newKey);

    /// <inheritdoc cref="IGenericBaseCommands.KeyPersistAsync(ValkeyKey, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="IGenericBaseCommands.KeyPersistAsync(ValkeyKey, CommandFlags)" /></returns>
    IBatch KeyPersist(ValkeyKey key);

    /// <inheritdoc cref="IGenericBaseCommands.KeyDumpAsync(ValkeyKey, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="IGenericBaseCommands.KeyDumpAsync(ValkeyKey, CommandFlags)" /></returns>
    IBatch KeyDump(ValkeyKey key);

    /// <inheritdoc cref="IGenericBaseCommands.KeyRestoreAsync(ValkeyKey, byte[], TimeSpan?, RestoreOptions, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="IGenericBaseCommands.KeyRestoreAsync(ValkeyKey, byte[], TimeSpan?, RestoreOptions?, CommandFlags)" /></returns>
    IBatch KeyRestore(ValkeyKey key, byte[] value, TimeSpan? expiry = null, RestoreOptions? restoreOptions = null);

    /// <inheritdoc cref="IGenericBaseCommands.KeyRestoreDateTimeAsync(ValkeyKey, byte[], DateTime?, RestoreOptions?, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="IGenericBaseCommands.KeyRestoreDateTimeAsync(ValkeyKey, byte[], DateTime?, RestoreOptions?, CommandFlags)" /></returns>
    IBatch KeyRestoreDateTime(ValkeyKey key, byte[] value, DateTime? expiry = null, RestoreOptions? restoreOptions = null);

    /// <inheritdoc cref="IGenericBaseCommands.KeyTouchAsync(ValkeyKey, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="IGenericBaseCommands.KeyTouchAsync(ValkeyKey, CommandFlags)" /></returns>
    IBatch KeyTouch(ValkeyKey key);

    /// <inheritdoc cref="IGenericBaseCommands.KeyTouchAsync(ValkeyKey[], CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="IGenericBaseCommands.KeyTouchAsync(ValkeyKey[], CommandFlags)" /></returns>
    IBatch KeyTouch(ValkeyKey[] keys);

    /// <inheritdoc cref="IGenericBaseCommands.KeyCopyAsync(ValkeyKey, ValkeyKey, bool, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="IGenericBaseCommands.KeyCopyAsync(ValkeyKey, ValkeyKey, bool, CommandFlags)" /></returns>
    IBatch KeyCopy(ValkeyKey sourceKey, ValkeyKey destinationKey, bool replace = false);
}
