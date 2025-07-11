// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Commands;
using Valkey.Glide.Internals;

namespace Valkey.Glide;

public abstract partial class BaseClient : ISortedSetCommands
{
    public async Task<bool> SortedSetAddAsync(ValkeyKey key, ValkeyValue member, double score, CommandFlags ignored = CommandFlags.None)
        => await Command(Request.SortedSetAddAsync(key, member, score));

    public async Task<long> SortedSetAddAsync(ValkeyKey key, SortedSetEntry[] entries, CommandFlags ignored = CommandFlags.None)
        => await Command(Request.SortedSetAddAsync(key, entries));
}
