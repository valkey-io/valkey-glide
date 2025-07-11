// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Commands;
using Valkey.Glide.Internals;

namespace Valkey.Glide;

public abstract partial class BaseClient : ISortedSetCommands
{
    /// <inheritdoc/>
    public async Task<bool> SortedSetAddAsync(ValkeyKey key, ValkeyValue member, double score, CommandFlags flags)
        => await SortedSetAddAsync(key, member, score, SortedSetWhen.Always, flags);

    /// <inheritdoc/>
    public async Task<bool> SortedSetAddAsync(ValkeyKey key, ValkeyValue member, double score, When when = When.Always, CommandFlags flags = CommandFlags.None)
        => await SortedSetAddAsync(key, member, score, SortedSetWhenExtensions.Parse(when), flags);

    /// <inheritdoc/>
    public async Task<bool> SortedSetAddAsync(ValkeyKey key, ValkeyValue member, double score, SortedSetWhen when = SortedSetWhen.Always, CommandFlags flags = CommandFlags.None)
        => await Command(Request.SortedSetAddAsync(key, member, score, when));

    /// <inheritdoc/>
    public async Task<long> SortedSetAddAsync(ValkeyKey key, SortedSetEntry[] values, CommandFlags flags)
        => await SortedSetAddAsync(key, values, SortedSetWhen.Always, flags);

    /// <inheritdoc/>
    public async Task<long> SortedSetAddAsync(ValkeyKey key, SortedSetEntry[] values, When when = When.Always, CommandFlags flags = CommandFlags.None)
        => await SortedSetAddAsync(key, values, SortedSetWhenExtensions.Parse(when), flags);

    /// <inheritdoc/>
    public async Task<long> SortedSetAddAsync(ValkeyKey key, SortedSetEntry[] values, SortedSetWhen when = SortedSetWhen.Always, CommandFlags flags = CommandFlags.None)
        => await Command(Request.SortedSetAddAsync(key, values, when));
}
