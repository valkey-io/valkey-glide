// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Commands.Constants;
using Valkey.Glide.Commands.Options;

using static Valkey.Glide.Internals.FFI;

namespace Valkey.Glide.Internals;

internal partial class Request
{
    public static Cmd<long, bool> KeyDeleteAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None)
    {
        if (flags != CommandFlags.None)
        {
            throw new NotImplementedException("Command flags are not supported by GLIDE");
        }
        GlideString[] args = [key.ToGlideString()];
        return Boolean<long>(RequestType.Del, args);
    }

    public static Cmd<long, long> KeyDeleteAsync(ValkeyKey[] keys, CommandFlags flags = CommandFlags.None)
    {
        if (flags != CommandFlags.None)
        {
            throw new NotImplementedException("Command flags are not supported by GLIDE");
        }
        GlideString[] args = keys.ToGlideStrings();
        return Simple<long>(RequestType.Del, args);
    }

    public static Cmd<long, bool> KeyUnlinkAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None)
    {
        if (flags != CommandFlags.None)
        {
            throw new NotImplementedException("Command flags are not supported by GLIDE");
        }
        GlideString[] args = [key.ToGlideString()];
        return Boolean<long>(RequestType.Unlink, args);
    }

    public static Cmd<long, long> KeyUnlinkAsync(ValkeyKey[] keys, CommandFlags flags = CommandFlags.None)
    {
        if (flags != CommandFlags.None)
        {
            throw new NotImplementedException("Command flags are not supported by GLIDE");
        }
        GlideString[] args = keys.ToGlideStrings();
        return Simple<long>(RequestType.Unlink, args);
    }

    public static Cmd<long, bool> KeyExistsAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None)
    {
        if (flags != CommandFlags.None)
        {
            throw new NotImplementedException("Command flags are not supported by GLIDE");
        }
        GlideString[] args = [key.ToGlideString()];
        return Boolean<long>(RequestType.Exists, args);
    }

    public static Cmd<long, long> KeyExistsAsync(ValkeyKey[] keys, CommandFlags flags = CommandFlags.None)
    {
        if (flags != CommandFlags.None)
        {
            throw new NotImplementedException("Command flags are not supported by GLIDE");
        }
        GlideString[] args = keys.ToGlideStrings();
        return Simple<long>(RequestType.Exists, args);
    }

    public static Cmd<bool, bool> KeyExpireAsync(ValkeyKey key, TimeSpan? expiry, ExpireWhen when = ExpireWhen.Always, CommandFlags flags = CommandFlags.None)
    {
        if (flags != CommandFlags.None)
        {
            throw new NotImplementedException("Command flags are not supported by GLIDE");
        }
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

    public static Cmd<bool, bool> KeyExpireAsync(ValkeyKey key, DateTime? expiry, ExpireWhen when = ExpireWhen.Always, CommandFlags flags = CommandFlags.None)
    {
        if (flags != CommandFlags.None)
        {
            throw new NotImplementedException("Command flags are not supported by GLIDE");
        }
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

    public static Cmd<long, TimeSpan?> KeyTimeToLiveAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None)
    {
        if (flags != CommandFlags.None)
        {
            throw new NotImplementedException("Command flags are not supported by GLIDE");
        }
        GlideString[] args = [key.ToGlideString()];
        return new(RequestType.TTL, args, true, response =>
            response is -1 or -2 ? null : TimeSpan.FromSeconds(response));
    }

    public static Cmd<GlideString, ValkeyType> KeyTypeAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None)
    {
        if (flags != CommandFlags.None)
        {
            throw new NotImplementedException("Command flags are not supported by GLIDE");
        }
        GlideString[] args = [key.ToGlideString()];
        return new(RequestType.Type, args, false, response =>
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
    }

    public static Cmd<string, bool> KeyRenameAsync(ValkeyKey key, ValkeyKey newKey, CommandFlags flags = CommandFlags.None)
    {
        if (flags != CommandFlags.None)
        {
            throw new NotImplementedException("Command flags are not supported by GLIDE");
        }
        GlideString[] args = [key.ToGlideString(), newKey.ToGlideString()];
        return OKToBool(RequestType.Rename, args);
    }

    public static Cmd<bool, bool> KeyRenameNXAsync(ValkeyKey key, ValkeyKey newKey, CommandFlags flags = CommandFlags.None)
    {
        if (flags != CommandFlags.None)
        {
            throw new NotImplementedException("Command flags are not supported by GLIDE");
        }
        GlideString[] args = [key.ToGlideString(), newKey.ToGlideString()];
        return Simple<bool>(RequestType.RenameNX, args);
    }

    public static Cmd<bool, bool> KeyPersistAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None)
    {
        if (flags != CommandFlags.None)
        {
            throw new NotImplementedException("Command flags are not supported by GLIDE");
        }
        GlideString[] args = [key.ToGlideString()];
        return Simple<bool>(RequestType.Persist, args);
    }

    public static Cmd<GlideString, byte[]?> KeyDumpAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None)
    {
        if (flags != CommandFlags.None)
        {
            throw new NotImplementedException("Command flags are not supported by GLIDE");
        }
        GlideString[] args = [key.ToGlideString()];
        return new(RequestType.Dump, args, true, response => response?.Bytes);
    }

    public static Cmd<string, string> KeyRestoreAsync(ValkeyKey key, byte[] value, TimeSpan? expiry = null, RestoreOptions? restoreOptions = null, CommandFlags flags = CommandFlags.None)
    {
        if (flags != CommandFlags.None)
        {
            throw new NotImplementedException("Command flags are not supported by GLIDE");
        }
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

    public static Cmd<string, string> KeyRestoreDateTimeAsync(ValkeyKey key, byte[] value, DateTime? expiry = null, RestoreOptions? restoreOptions = null, CommandFlags flags = CommandFlags.None)
    {
        if (flags != CommandFlags.None)
        {
            throw new NotImplementedException("Command flags are not supported by GLIDE");
        }
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

    public static Cmd<long, bool> KeyTouchAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None)
    {
        if (flags != CommandFlags.None)
        {
            throw new NotImplementedException("Command flags are not supported by GLIDE");
        }
        GlideString[] args = [key.ToGlideString()];
        return Boolean<long>(RequestType.Touch, args);
    }

    public static Cmd<long, long> KeyTouchAsync(ValkeyKey[] keys, CommandFlags flags = CommandFlags.None)
    {
        if (flags != CommandFlags.None)
        {
            throw new NotImplementedException("Command flags are not supported by GLIDE");
        }
        GlideString[] args = keys.ToGlideStrings();
        return Simple<long>(RequestType.Touch, args);
    }

    public static Cmd<bool, bool> KeyCopyAsync(ValkeyKey sourceKey, ValkeyKey destinationKey, bool replace = false, CommandFlags flags = CommandFlags.None)
    {
        if (flags != CommandFlags.None)
        {
            throw new NotImplementedException("Command flags are not supported by GLIDE");
        }
        List<GlideString> args = [sourceKey.ToGlideString(), destinationKey.ToGlideString()];

        if (replace)
        {
            args.Add(Constants.ReplaceKeyword);
        }

        return Simple<bool>(RequestType.Copy, [.. args]);
    }

    public static Cmd<bool, bool> KeyCopyAsync(ValkeyKey sourceKey, ValkeyKey destinationKey, int destinationDatabase, bool replace = false, CommandFlags flags = CommandFlags.None)
    {
        if (flags != CommandFlags.None)
        {
            throw new NotImplementedException("Command flags are not supported by GLIDE");
        }
        List<GlideString> args = [sourceKey.ToGlideString(), destinationKey.ToGlideString()];

        args.AddRange([Constants.DbKeyword, destinationDatabase.ToGlideString()]);

        if (replace)
        {
            args.Add(Constants.ReplaceKeyword);
        }

        return Simple<bool>(RequestType.Copy, [.. args]);
    }

    public static Cmd<bool, bool> KeyMoveAsync(ValkeyKey key, int database, CommandFlags flags = CommandFlags.None)
    {
        if (flags != CommandFlags.None)
        {
            throw new NotImplementedException("Command flags are not supported by GLIDE");
        }
        GlideString[] args = [key.ToGlideString(), database.ToGlideString()];
        return Simple<bool>(RequestType.Move, args);
    }
}
