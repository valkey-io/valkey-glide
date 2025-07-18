// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Commands;
using Valkey.Glide.Commands.Options;
using Valkey.Glide.Internals;

namespace Valkey.Glide;

public abstract partial class BaseClient : IGenericBaseCommands
{
    public async Task<bool> KeyDeleteAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None)
        => await Command(Request.KeyDeleteAsync(key));

    public async Task<long> KeyDeleteAsync(ValkeyKey[] keys, CommandFlags flags = CommandFlags.None)
        => await Command(Request.KeyDeleteAsync(keys));

    public async Task<bool> KeyUnlinkAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None)
        => await Command(Request.KeyUnlinkAsync(key));

    public async Task<long> KeyUnlinkAsync(ValkeyKey[] keys, CommandFlags flags = CommandFlags.None)
        => await Command(Request.KeyUnlinkAsync(keys));

    public async Task<bool> KeyExistsAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None)
        => await Command(Request.KeyExistsAsync(key));

    public async Task<long> KeyExistsAsync(ValkeyKey[] keys, CommandFlags flags = CommandFlags.None)
        => await Command(Request.KeyExistsAsync(keys));

    public async Task<bool> KeyExpireAsync(ValkeyKey key, TimeSpan? expiry, ExpireWhen when = ExpireWhen.Always, CommandFlags flags = CommandFlags.None)
        => await Command(Request.KeyExpireAsync(key, expiry, when));

    public async Task<bool> KeyExpireAsync(ValkeyKey key, DateTime? expiry, ExpireWhen when = ExpireWhen.Always, CommandFlags flags = CommandFlags.None)
        => await Command(Request.KeyExpireAsync(key, expiry, when));

    public async Task<TimeSpan?> KeyTimeToLiveAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None)
        => await Command(Request.KeyTimeToLiveAsync(key));

    public async Task<ValkeyType> KeyTypeAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None)
        => await Command(Request.KeyTypeAsync(key));

    public async Task<bool> KeyRenameAsync(ValkeyKey key, ValkeyKey newKey, CommandFlags flags = CommandFlags.None)
        => await Command(Request.KeyRenameAsync(key, newKey));

    public async Task<bool> KeyRenameNXAsync(ValkeyKey key, ValkeyKey newKey, CommandFlags flags = CommandFlags.None)
        => await Command(Request.KeyRenameNXAsync(key, newKey));

    public async Task<bool> KeyPersistAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None)
        => await Command(Request.KeyPersistAsync(key));

    public async Task<byte[]?> KeyDumpAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None)
        => await Command(Request.KeyDumpAsync(key));

    public async Task KeyRestoreAsync(ValkeyKey key, byte[] value, TimeSpan? expiry = null, RestoreOptions? restoreOptions = null, CommandFlags flags = CommandFlags.None)
        => await Command(Request.KeyRestoreAsync(key, value, expiry, restoreOptions: restoreOptions));

    public async Task KeyRestoreDateTimeAsync(ValkeyKey key, byte[] value, DateTime? expiry = null, RestoreOptions? restoreOptions = null, CommandFlags flags = CommandFlags.None)
        => await Command(Request.KeyRestoreDateTimeAsync(key, value, expiry, restoreOptions: restoreOptions));

    public async Task<bool> KeyTouchAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None)
        => await Command(Request.KeyTouchAsync(key));

    public async Task<long> KeyTouchAsync(ValkeyKey[] keys, CommandFlags flags = CommandFlags.None)
        => await Command(Request.KeyTouchAsync(keys));

    public async Task<bool> KeyCopyAsync(ValkeyKey sourceKey, ValkeyKey destinationKey, bool replace = false, CommandFlags flags = CommandFlags.None)
        => await Command(Request.KeyCopyAsync(sourceKey, destinationKey, replace));

}
