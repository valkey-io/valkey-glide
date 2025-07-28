// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Commands.Constants;

using static Valkey.Glide.Internals.FFI;

namespace Valkey.Glide.Internals;

internal partial class Request
{
    public static Cmd<long, bool> SetAddAsync(ValkeyKey key, ValkeyValue value)
        => Boolean<long>(RequestType.SAdd, [key.ToGlideString(), value.ToGlideString()]);

    public static Cmd<long, long> SetAddAsync(ValkeyKey key, ValkeyValue[] values)
        => Simple<long>(RequestType.SAdd, [key.ToGlideString(), .. values.ToGlideStrings()]);

    public static Cmd<long, bool> SetRemoveAsync(ValkeyKey key, ValkeyValue value)
        => Boolean<long>(RequestType.SRem, [key.ToGlideString(), value.ToGlideString()]);

    public static Cmd<long, long> SetRemoveAsync(ValkeyKey key, ValkeyValue[] values)
        => Simple<long>(RequestType.SRem, [key.ToGlideString(), .. values.ToGlideStrings()]);

    public static Cmd<HashSet<object>, ValkeyValue[]> SetMembersAsync(ValkeyKey key)
        => new(RequestType.SMembers, [key.ToGlideString()], false, set => [.. set.Cast<GlideString>().Select(gs => (ValkeyValue)gs)]);

    public static Cmd<long, long> SetLengthAsync(ValkeyKey key)
        => Simple<long>(RequestType.SCard, [key.ToGlideString()]);

    public static Cmd<long, long> SetIntersectionLengthAsync(ValkeyKey[] keys, long limit = 0)
    {
        List<GlideString> args = [keys.Length.ToGlideString(), .. keys.ToGlideStrings()];
        if (limit > 0)
        {
            args.AddRange([Constants.LimitKeyword, limit.ToGlideString()]);
        }
        return Simple<long>(RequestType.SInterCard, [.. args]);
    }

    public static Cmd<GlideString, GlideString> SetPopAsync(ValkeyKey key)
        => Simple<GlideString>(RequestType.SPop, [key.ToGlideString()], true);

    public static Cmd<HashSet<object>, ValkeyValue[]> SetPopAsync(ValkeyKey key, long count)
        => new(RequestType.SPop, [key.ToGlideString(), count.ToGlideString()], false, set => [.. set.Cast<GlideString>().Select(gs => (ValkeyValue)gs)]);

    public static Cmd<HashSet<object>, ValkeyValue[]> SetUnionAsync(ValkeyKey[] keys)
        => new(RequestType.SUnion, keys.ToGlideStrings(), false, set => [.. set.Cast<GlideString>().Select(gs => (ValkeyValue)gs)]);

    public static Cmd<HashSet<object>, ValkeyValue[]> SetIntersectAsync(ValkeyKey[] keys)
        => new(RequestType.SInter, keys.ToGlideStrings(), false, set => [.. set.Cast<GlideString>().Select(gs => (ValkeyValue)gs)]);

    public static Cmd<HashSet<object>, ValkeyValue[]> SetDifferenceAsync(ValkeyKey[] keys)
        => new(RequestType.SDiff, keys.ToGlideStrings(), false, set => [.. set.Cast<GlideString>().Select(gs => (ValkeyValue)gs)]);

    public static Cmd<long, long> SetUnionStoreAsync(ValkeyKey destination, ValkeyKey[] keys)
        => Simple<long>(RequestType.SUnionStore, [destination.ToGlideString(), .. keys.ToGlideStrings()]);

    public static Cmd<long, long> SetIntersectStoreAsync(ValkeyKey destination, ValkeyKey[] keys)
        => Simple<long>(RequestType.SInterStore, [destination.ToGlideString(), .. keys.ToGlideStrings()]);

    public static Cmd<long, long> SetDifferenceStoreAsync(ValkeyKey destination, ValkeyKey[] keys)
        => Simple<long>(RequestType.SDiffStore, [destination.ToGlideString(), .. keys.ToGlideStrings()]);
}
