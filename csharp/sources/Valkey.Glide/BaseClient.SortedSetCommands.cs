// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Commands;
using Valkey.Glide.Internals;

namespace Valkey.Glide;

public abstract partial class BaseClient : ISortedSetCommands
{
    public async Task<bool> SortedSetAddAsync(ValkeyKey key, ValkeyValue member, double score, CommandFlags flags)
        => await SortedSetAddAsync(key, member, score, SortedSetWhen.Always, flags);

    public async Task<bool> SortedSetAddAsync(ValkeyKey key, ValkeyValue member, double score, When when, CommandFlags flags = CommandFlags.None)
        => await SortedSetAddAsync(key, member, score, SortedSetWhenExtensions.Parse(when), flags);

    public async Task<bool> SortedSetAddAsync(ValkeyKey key, ValkeyValue member, double score, SortedSetWhen when = SortedSetWhen.Always, CommandFlags flags = CommandFlags.None)
        => await Command(Request.SortedSetAddAsync(key, member, score, when, flags));

    public async Task<long> SortedSetAddAsync(ValkeyKey key, SortedSetEntry[] values, CommandFlags flags)
        => await SortedSetAddAsync(key, values, SortedSetWhen.Always, flags);

    public async Task<long> SortedSetAddAsync(ValkeyKey key, SortedSetEntry[] values, When when, CommandFlags flags = CommandFlags.None)
        => await SortedSetAddAsync(key, values, SortedSetWhenExtensions.Parse(when), flags);

    public async Task<long> SortedSetAddAsync(ValkeyKey key, SortedSetEntry[] values, SortedSetWhen when = SortedSetWhen.Always, CommandFlags flags = CommandFlags.None)
        => values.Length == 0 ? 0 : await Command(Request.SortedSetAddAsync(key, values, when, flags));
}
