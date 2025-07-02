// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Commands;
using Valkey.Glide.Internals;

namespace Valkey.Glide;

public abstract partial class BaseClient : ISetCommands
{
    public async Task<bool> SetAddAsync(ValkeyKey key, ValkeyValue value, CommandFlags ignored = CommandFlags.None)
        => await Command(Request.SetAddAsync(key, value));

    public async Task<long> SetAddAsync(ValkeyKey key, ValkeyValue[] values, CommandFlags ignored = CommandFlags.None)
        => await Command(Request.SetAddAsync(key, values));

    public async Task<bool> SetRemoveAsync(ValkeyKey key, ValkeyValue value, CommandFlags ignored = CommandFlags.None)
        => await Command(Request.SetRemoveAsync(key, value));

    public async Task<long> SetRemoveAsync(ValkeyKey key, ValkeyValue[] values, CommandFlags ignored = CommandFlags.None)
        => await Command(Request.SetRemoveAsync(key, values));

    public async Task<ValkeyValue[]> SetMembersAsync(ValkeyKey key, CommandFlags ignored = CommandFlags.None)
        => await Command(Request.SetMembersAsync(key));

    public async Task<long> SetLengthAsync(ValkeyKey key, CommandFlags ignored = CommandFlags.None)
        => await Command(Request.SetLengthAsync(key));

    public async Task<long> SetIntersectionLengthAsync(ValkeyKey[] keys, long limit = 0, CommandFlags ignored = CommandFlags.None)
        => await Command(Request.SetIntersectionLengthAsync(keys, limit));

    public async Task<ValkeyValue> SetPopAsync(ValkeyKey key, CommandFlags ignored = CommandFlags.None)
        => await Command(Request.SetPopAsync(key));

    public async Task<ValkeyValue[]> SetPopAsync(ValkeyKey key, long count, CommandFlags ignored = CommandFlags.None)
        => await Command(Request.SetPopAsync(key, count));

    public async Task<ValkeyValue[]> SetCombineAsync(SetOperation operation, ValkeyKey first, ValkeyKey second, CommandFlags ignored = CommandFlags.None)
        => await SetCombineAsync(operation, [first, second], ignored);

    public async Task<ValkeyValue[]> SetCombineAsync(SetOperation operation, ValkeyKey[] keys, CommandFlags ignored = CommandFlags.None)
        => await Command(Request.SetCombineAsync(operation, keys));

    public async Task<long> SetCombineAndStoreAsync(SetOperation operation, ValkeyKey destination, ValkeyKey first, ValkeyKey second, CommandFlags ignored = CommandFlags.None)
        => await SetCombineAndStoreAsync(operation, destination, [first, second], ignored);

    public async Task<long> SetCombineAndStoreAsync(SetOperation operation, ValkeyKey destination, ValkeyKey[] keys, CommandFlags ignored = CommandFlags.None)
        => await Command(Request.SetCombineAndStoreAsync(operation, destination, keys));
}
