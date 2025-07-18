// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Commands;

namespace Valkey.Glide.Pipeline;

internal interface IBatchListCommands
{
    /// <inheritdoc cref="IListCommands.ListLeftPopAsync(ValkeyKey, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="IListCommands.ListLeftPopAsync(ValkeyKey, CommandFlags)" /></returns>
    IBatch ListLeftPop(ValkeyKey key);

    /// <inheritdoc cref="IListCommands.ListLeftPopAsync(ValkeyKey, long, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="IListCommands.ListLeftPopAsync(ValkeyKey, long, CommandFlags)" /></returns>
    IBatch ListLeftPop(ValkeyKey key, long count);

    /// <inheritdoc cref="IListCommands.ListLeftPushAsync(ValkeyKey, ValkeyValue[], When, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="IListCommands.ListLeftPushAsync(ValkeyKey, ValkeyValue[], When, CommandFlags)" /></returns>
    IBatch ListLeftPush(ValkeyKey key, ValkeyValue[] values);
}
