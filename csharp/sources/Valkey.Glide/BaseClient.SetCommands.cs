// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Commands;
using Valkey.Glide.Internals;

namespace Valkey.Glide;

public abstract partial class BaseClient : ISetCommands
{
    public async Task<bool> SetAddAsync(ValkeyKey key, ValkeyValue value, CommandFlags flags = CommandFlags.None)
        => await Command(Request.SetAddAsync(key, value, flags));

    public async Task<long> SetAddAsync(ValkeyKey key, ValkeyValue[] values, CommandFlags flags = CommandFlags.None)
        => await Command(Request.SetAddAsync(key, values, flags));

    public async Task<bool> SetRemoveAsync(ValkeyKey key, ValkeyValue value, CommandFlags flags = CommandFlags.None)
        => await Command(Request.SetRemoveAsync(key, value, flags));

    public async Task<long> SetRemoveAsync(ValkeyKey key, ValkeyValue[] values, CommandFlags flags = CommandFlags.None)
        => await Command(Request.SetRemoveAsync(key, values, flags));

    public async Task<ValkeyValue[]> SetMembersAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None)
        => await Command(Request.SetMembersAsync(key, flags));

    public async Task<long> SetLengthAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None)
        => await Command(Request.SetLengthAsync(key, flags));

    public async Task<long> SetIntersectionLengthAsync(ValkeyKey[] keys, long limit = 0, CommandFlags flags = CommandFlags.None)
        => await Command(Request.SetIntersectionLengthAsync(keys, limit, flags));

    public async Task<ValkeyValue> SetPopAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None)
        => await Command(Request.SetPopAsync(key, flags));

    public async Task<ValkeyValue[]> SetPopAsync(ValkeyKey key, long count, CommandFlags flags = CommandFlags.None)
        => await Command(Request.SetPopAsync(key, count, flags));

    public async Task<ValkeyValue[]> SetUnionAsync(ValkeyKey first, ValkeyKey second, CommandFlags flags = CommandFlags.None)
        => await SetUnionAsync([first, second], flags);

    public async Task<ValkeyValue[]> SetUnionAsync(ValkeyKey[] keys, CommandFlags flags = CommandFlags.None)
        => await Command(Request.SetUnionAsync(keys, flags));

    public async Task<ValkeyValue[]> SetIntersectAsync(ValkeyKey first, ValkeyKey second, CommandFlags flags = CommandFlags.None)
        => await SetIntersectAsync([first, second], flags);

    public async Task<ValkeyValue[]> SetIntersectAsync(ValkeyKey[] keys, CommandFlags flags = CommandFlags.None)
        => await Command(Request.SetIntersectAsync(keys, flags));

    public async Task<ValkeyValue[]> SetDifferenceAsync(ValkeyKey first, ValkeyKey second, CommandFlags flags = CommandFlags.None)
        => await SetDifferenceAsync([first, second], flags);

    public async Task<ValkeyValue[]> SetDifferenceAsync(ValkeyKey[] keys, CommandFlags flags = CommandFlags.None)
        => await Command(Request.SetDifferenceAsync(keys, flags));

    public async Task<long> SetUnionStoreAsync(ValkeyKey destination, ValkeyKey first, ValkeyKey second, CommandFlags flags = CommandFlags.None)
        => await SetUnionStoreAsync(destination, [first, second], flags);

    public async Task<long> SetUnionStoreAsync(ValkeyKey destination, ValkeyKey[] keys, CommandFlags flags = CommandFlags.None)
        => await Command(Request.SetUnionStoreAsync(destination, keys, flags));

    public async Task<long> SetIntersectStoreAsync(ValkeyKey destination, ValkeyKey first, ValkeyKey second, CommandFlags flags = CommandFlags.None)
        => await SetIntersectStoreAsync(destination, [first, second], flags);

    public async Task<long> SetIntersectStoreAsync(ValkeyKey destination, ValkeyKey[] keys, CommandFlags flags = CommandFlags.None)
        => await Command(Request.SetIntersectStoreAsync(destination, keys, flags));

    public async Task<long> SetDifferenceStoreAsync(ValkeyKey destination, ValkeyKey first, ValkeyKey second, CommandFlags flags = CommandFlags.None)
        => await SetDifferenceStoreAsync(destination, [first, second], flags);

    public async Task<long> SetDifferenceStoreAsync(ValkeyKey destination, ValkeyKey[] keys, CommandFlags flags = CommandFlags.None)
        => await Command(Request.SetDifferenceStoreAsync(destination, keys, flags));
}
