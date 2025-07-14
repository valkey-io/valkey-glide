// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Commands;
using Valkey.Glide.Internals;

namespace Valkey.Glide;

public abstract partial class BaseClient : ISortedSetCommands
{
    public async Task<bool> SortedSetAddAsync(ValkeyKey key, ValkeyValue member, double score, SortedSetWhen when = SortedSetWhen.Always, CommandFlags flags = CommandFlags.None)
        => await Command(Request.SortedSetAddAsync(key, member, score, when, flags));

    public async Task<long> SortedSetAddAsync(ValkeyKey key, SortedSetEntry[] values, SortedSetWhen when = SortedSetWhen.Always, CommandFlags flags = CommandFlags.None)
        => await Command(Request.SortedSetAddAsync(key, values, when, flags));

    public async Task<double?> SortedSetIncrementAsync(ValkeyKey key, ValkeyValue member, double increment, SortedSetWhen when = SortedSetWhen.Always, CommandFlags flags = CommandFlags.None)
        => await Command(Request.SortedSetIncrementAsync(key, member, increment, when, flags));
}
