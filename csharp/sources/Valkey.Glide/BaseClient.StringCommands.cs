// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Commands;
using Valkey.Glide.Internals;

namespace Valkey.Glide;

public abstract partial class BaseClient : IStringBaseCommands
{
    public async Task<bool> StringSetAsync(ValkeyKey key, ValkeyValue value, CommandFlags flags = CommandFlags.None)
        => await Command(Request.StringSet(key, value));

    public async Task<ValkeyValue> StringGetAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None)
        => await Command(Request.StringGet(key));

    public async Task<ValkeyValue[]> StringGetAsync(ValkeyKey[] keys, CommandFlags flags = CommandFlags.None)
        => await Command(Request.StringGetMultiple(keys));

    public async Task<bool> StringSetAsync(KeyValuePair<ValkeyKey, ValkeyValue>[] values, CommandFlags flags = CommandFlags.None)
        => await Command(Request.StringSetMultiple(values));

    public async Task<ValkeyValue> StringGetRangeAsync(ValkeyKey key, long start, long end, CommandFlags flags = CommandFlags.None)
        => await Command(Request.StringGetRange(key, start, end));

    public async Task<ValkeyValue> StringSetRangeAsync(ValkeyKey key, long offset, ValkeyValue value, CommandFlags flags = CommandFlags.None)
        => await Command(Request.StringSetRange(key, offset, value));

    public async Task<long> StringLengthAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None)
        => await Command(Request.StringLength(key));
}
