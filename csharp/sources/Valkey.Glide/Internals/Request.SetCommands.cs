// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Commands.Constants;

using static Valkey.Glide.Internals.FFI;

namespace Valkey.Glide.Internals;

internal partial class Request
{
    public static Cmd<long, bool> SetAddAsync(ValkeyKey key, ValkeyValue value, CommandFlags flags = CommandFlags.None)
    {
        if (flags != CommandFlags.None)
        {
            throw new NotImplementedException("Command flags are not supported by GLIDE");
        }
        GlideString[] args = [key.ToGlideString(), value.ToGlideString()];
        return Boolean<long>(RequestType.SAdd, args);
    }

    public static Cmd<long, long> SetAddAsync(ValkeyKey key, ValkeyValue[] values, CommandFlags flags = CommandFlags.None)
    {
        if (flags != CommandFlags.None)
        {
            throw new NotImplementedException("Command flags are not supported by GLIDE");
        }
        GlideString[] args = [key.ToGlideString(), .. values.ToGlideStrings()];
        return Simple<long>(RequestType.SAdd, args);
    }

    public static Cmd<long, bool> SetRemoveAsync(ValkeyKey key, ValkeyValue value, CommandFlags flags = CommandFlags.None)
    {
        if (flags != CommandFlags.None)
        {
            throw new NotImplementedException("Command flags are not supported by GLIDE");
        }
        GlideString[] args = [key.ToGlideString(), value.ToGlideString()];
        return Boolean<long>(RequestType.SRem, args);
    }

    public static Cmd<long, long> SetRemoveAsync(ValkeyKey key, ValkeyValue[] values, CommandFlags flags = CommandFlags.None)
    {
        if (flags != CommandFlags.None)
        {
            throw new NotImplementedException("Command flags are not supported by GLIDE");
        }
        GlideString[] args = [key.ToGlideString(), .. values.ToGlideStrings()];
        return Simple<long>(RequestType.SRem, args);
    }

    public static Cmd<HashSet<object>, ValkeyValue[]> SetMembersAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None)
    {
        if (flags != CommandFlags.None)
        {
            throw new NotImplementedException("Command flags are not supported by GLIDE");
        }
        GlideString[] args = [key.ToGlideString()];
        return new(RequestType.SMembers, args, false, set => [.. set.Cast<GlideString>().Select(gs => (ValkeyValue)gs)]);
    }

    public static Cmd<long, long> SetLengthAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None)
    {
        if (flags != CommandFlags.None)
        {
            throw new NotImplementedException("Command flags are not supported by GLIDE");
        }
        GlideString[] args = [key.ToGlideString()];
        return Simple<long>(RequestType.SCard, args);
    }

    public static Cmd<long, long> SetIntersectionLengthAsync(ValkeyKey[] keys, long limit = 0, CommandFlags flags = CommandFlags.None)
    {
        if (flags != CommandFlags.None)
        {
            throw new NotImplementedException("Command flags are not supported by GLIDE");
        }
        List<GlideString> args = [keys.Length.ToGlideString(), .. keys.ToGlideStrings()];
        if (limit > 0)
        {
            args.AddRange([Constants.LimitKeyword, limit.ToGlideString()]);
        }
        return Simple<long>(RequestType.SInterCard, [.. args]);
    }

    public static Cmd<GlideString, GlideString> SetPopAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None)
    {
        if (flags != CommandFlags.None)
        {
            throw new NotImplementedException("Command flags are not supported by GLIDE");
        }
        GlideString[] args = [key.ToGlideString()];
        return Simple<GlideString>(RequestType.SPop, args, true);
    }

    public static Cmd<HashSet<object>, ValkeyValue[]> SetPopAsync(ValkeyKey key, long count, CommandFlags flags = CommandFlags.None)
    {
        if (flags != CommandFlags.None)
        {
            throw new NotImplementedException("Command flags are not supported by GLIDE");
        }
        GlideString[] args = [key.ToGlideString(), count.ToGlideString()];
        return new(RequestType.SPop, args, false, set => [.. set.Cast<GlideString>().Select(gs => (ValkeyValue)gs)]);
    }

    public static Cmd<HashSet<object>, ValkeyValue[]> SetUnionAsync(ValkeyKey[] keys, CommandFlags flags = CommandFlags.None)
    {
        if (flags != CommandFlags.None)
        {
            throw new NotImplementedException("Command flags are not supported by GLIDE");
        }
        GlideString[] args = keys.ToGlideStrings();
        return new(RequestType.SUnion, args, false, set => [.. set.Cast<GlideString>().Select(gs => (ValkeyValue)gs)]);
    }

    public static Cmd<HashSet<object>, ValkeyValue[]> SetIntersectAsync(ValkeyKey[] keys, CommandFlags flags = CommandFlags.None)
    {
        if (flags != CommandFlags.None)
        {
            throw new NotImplementedException("Command flags are not supported by GLIDE");
        }
        GlideString[] args = keys.ToGlideStrings();
        return new(RequestType.SInter, args, false, set => [.. set.Cast<GlideString>().Select(gs => (ValkeyValue)gs)]);
    }

    public static Cmd<HashSet<object>, ValkeyValue[]> SetDifferenceAsync(ValkeyKey[] keys, CommandFlags flags = CommandFlags.None)
    {
        if (flags != CommandFlags.None)
        {
            throw new NotImplementedException("Command flags are not supported by GLIDE");
        }
        GlideString[] args = keys.ToGlideStrings();
        return new(RequestType.SDiff, args, false, set => [.. set.Cast<GlideString>().Select(gs => (ValkeyValue)gs)]);
    }

    public static Cmd<long, long> SetUnionStoreAsync(ValkeyKey destination, ValkeyKey[] keys, CommandFlags flags = CommandFlags.None)
    {
        if (flags != CommandFlags.None)
        {
            throw new NotImplementedException("Command flags are not supported by GLIDE");
        }
        GlideString[] args = [destination.ToGlideString(), .. keys.ToGlideStrings()];
        return Simple<long>(RequestType.SUnionStore, args);
    }

    public static Cmd<long, long> SetIntersectStoreAsync(ValkeyKey destination, ValkeyKey[] keys, CommandFlags flags = CommandFlags.None)
    {
        if (flags != CommandFlags.None)
        {
            throw new NotImplementedException("Command flags are not supported by GLIDE");
        }
        GlideString[] args = [destination.ToGlideString(), .. keys.ToGlideStrings()];
        return Simple<long>(RequestType.SInterStore, args);
    }

    public static Cmd<long, long> SetDifferenceStoreAsync(ValkeyKey destination, ValkeyKey[] keys, CommandFlags flags = CommandFlags.None)
    {
        if (flags != CommandFlags.None)
        {
            throw new NotImplementedException("Command flags are not supported by GLIDE");
        }
        GlideString[] args = [destination.ToGlideString(), .. keys.ToGlideStrings()];
        return Simple<long>(RequestType.SDiffStore, args);
    }
}
