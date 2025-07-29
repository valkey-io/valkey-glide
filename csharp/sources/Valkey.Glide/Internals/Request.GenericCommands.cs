// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Commands.Constants;
using Valkey.Glide.Commands.Options;

using static Valkey.Glide.Internals.FFI;

namespace Valkey.Glide.Internals;

internal partial class Request
{
    public static Cmd<long, bool> KeyDeleteAsync(ValkeyKey key)
        => Boolean<long>(RequestType.Del, [key.ToGlideString()]);

    public static Cmd<long, long> KeyDeleteAsync(ValkeyKey[] keys)
        => Simple<long>(RequestType.Del, keys.ToGlideStrings());

    public static Cmd<long, bool> KeyUnlinkAsync(ValkeyKey key)
        => Boolean<long>(RequestType.Unlink, [key.ToGlideString()]);

    public static Cmd<long, long> KeyUnlinkAsync(ValkeyKey[] keys)
        => Simple<long>(RequestType.Unlink, keys.ToGlideStrings());

    public static Cmd<long, bool> KeyExistsAsync(ValkeyKey key)
        => Boolean<long>(RequestType.Exists, [key.ToGlideString()]);

    public static Cmd<long, long> KeyExistsAsync(ValkeyKey[] keys)
        => Simple<long>(RequestType.Exists, keys.ToGlideStrings());

    public static Cmd<bool, bool> KeyExpireAsync(ValkeyKey key, TimeSpan? expiry, ExpireWhen when = ExpireWhen.Always)
    {
        List<GlideString> args = [key.ToGlideString()];

        if (expiry.HasValue)
        {
            args.Add(((long)expiry.Value.TotalSeconds).ToGlideString());
        }
        else
        {
            args.Add((-1).ToGlideString()); // Instant expiry
        }

        if (when != ExpireWhen.Always)
        {
            args.Add(when.ToLiteral().ToGlideString());
        }

        return Simple<bool>(RequestType.Expire, [.. args]);
    }

    public static Cmd<bool, bool> KeyExpireAsync(ValkeyKey key, DateTime? expiry, ExpireWhen when = ExpireWhen.Always)
    {
        List<GlideString> args = [key.ToGlideString()];

        if (expiry.HasValue)
        {
            long unixTimestamp = ((DateTimeOffset)expiry.Value).ToUnixTimeSeconds();
            args.Add(unixTimestamp.ToGlideString());
        }
        else
        {
            args.Add((-1).ToGlideString()); // Instant expiry
        }

        if (when != ExpireWhen.Always)
        {
            args.Add(when.ToLiteral().ToGlideString());
        }

        return Simple<bool>(RequestType.ExpireAt, [.. args]);
    }

    public static Cmd<long, TimeSpan?> KeyTimeToLiveAsync(ValkeyKey key)
        => new(RequestType.TTL, [key.ToGlideString()], true, response =>
            response is -1 or -2 ? null : TimeSpan.FromSeconds(response));

    public static Cmd<GlideString, ValkeyType> KeyTypeAsync(ValkeyKey key)
        => new(RequestType.Type, [key.ToGlideString()], false, response =>
        {
            string typeStr = response.ToString();
            return typeStr switch
            {
                "string" => ValkeyType.String,
                "list" => ValkeyType.List,
                "set" => ValkeyType.Set,
                "zset" => ValkeyType.SortedSet,
                "hash" => ValkeyType.Hash,
                "stream" => ValkeyType.Stream,
                _ => ValkeyType.None
            };
        });

    public static Cmd<string, bool> KeyRenameAsync(ValkeyKey key, ValkeyKey newKey)
        => OKToBool(RequestType.Rename, [key.ToGlideString(), newKey.ToGlideString()]);

    public static Cmd<bool, bool> KeyRenameNXAsync(ValkeyKey key, ValkeyKey newKey)
        => Simple<bool>(RequestType.RenameNX, [key.ToGlideString(), newKey.ToGlideString()]);

    public static Cmd<bool, bool> KeyPersistAsync(ValkeyKey key)
        => Simple<bool>(RequestType.Persist, [key.ToGlideString()]);

    public static Cmd<GlideString, byte[]?> KeyDumpAsync(ValkeyKey key)
        => new(RequestType.Dump, [key.ToGlideString()], true, response => response?.Bytes);

    public static Cmd<string, string> KeyRestoreAsync(ValkeyKey key, byte[] value, TimeSpan? expiry = null, RestoreOptions? restoreOptions = null)
    {
        List<GlideString> args = [key.ToGlideString()];

        if (expiry.HasValue)
        {
            args.Add(((long)expiry.Value.TotalMilliseconds).ToGlideString());
        }
        else
        {
            args.Add(0.ToGlideString());
        }

        args.Add(value.ToGlideString());

        if (restoreOptions != null)
        {
            args.AddRange(restoreOptions.ToArgs());
        }

        return OK(RequestType.Restore, [.. args]);
    }

    public static Cmd<string, string> KeyRestoreDateTimeAsync(ValkeyKey key, byte[] value, DateTime? expiry = null, RestoreOptions? restoreOptions = null)
    {
        List<GlideString> args = [key.ToGlideString()];

        if (expiry.HasValue)
        {
            args.Add(((DateTimeOffset)expiry).ToUnixTimeMilliseconds().ToGlideString());
        }
        else
        {
            args.Add(0.ToGlideString());
        }

        args.Add(value.ToGlideString());
        args.Add(Constants.AbsttlKeyword); // By default needs to be added here

        if (restoreOptions != null)
        {
            args.AddRange(restoreOptions.ToArgs());
        }

        return OK(RequestType.Restore, [.. args]);
    }

    public static Cmd<long, bool> KeyTouchAsync(ValkeyKey key)
        => Boolean<long>(RequestType.Touch, [key.ToGlideString()]);

    public static Cmd<long, long> KeyTouchAsync(ValkeyKey[] keys)
        => Simple<long>(RequestType.Touch, keys.ToGlideStrings());

    public static Cmd<bool, bool> KeyCopyAsync(ValkeyKey sourceKey, ValkeyKey destinationKey, bool replace = false)
    {
        List<GlideString> args = [sourceKey.ToGlideString(), destinationKey.ToGlideString()];

        if (replace)
        {
            args.Add(Constants.ReplaceKeyword);
        }

        return Simple<bool>(RequestType.Copy, [.. args]);
    }

    public static Cmd<bool, bool> KeyCopyAsync(ValkeyKey sourceKey, ValkeyKey destinationKey, int destinationDatabase, bool replace = false)
    {
        List<GlideString> args = [sourceKey.ToGlideString(), destinationKey.ToGlideString()];

        args.AddRange([Constants.DbKeyword, destinationDatabase.ToGlideString()]);

        if (replace)
        {
            args.Add(Constants.ReplaceKeyword);
        }

        return Simple<bool>(RequestType.Copy, [.. args]);
    }

    public static Cmd<bool, bool> KeyMoveAsync(ValkeyKey key, int database)
        => Simple<bool>(RequestType.Move, [key.ToGlideString(), database.ToGlideString()]);
}
