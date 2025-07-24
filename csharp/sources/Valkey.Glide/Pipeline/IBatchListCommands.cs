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

    /// <inheritdoc cref="IListCommands.ListLeftPushAsync(ValkeyKey, ValkeyValue, When, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="IListCommands.ListLeftPushAsync(ValkeyKey, ValkeyValue, When, CommandFlags)" /></returns>
    IBatch ListLeftPush(ValkeyKey key, ValkeyValue value);

    /// <inheritdoc cref="IListCommands.ListLeftPushAsync(ValkeyKey, ValkeyValue[], When, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="IListCommands.ListLeftPushAsync(ValkeyKey, ValkeyValue[], When, CommandFlags)" /></returns>
    IBatch ListLeftPush(ValkeyKey key, ValkeyValue[] values);

    /// <inheritdoc cref="IListCommands.ListLeftPushAsync(ValkeyKey, ValkeyValue[], CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="IListCommands.ListLeftPushAsync(ValkeyKey, ValkeyValue[], CommandFlags)" /></returns>
    IBatch ListLeftPush(ValkeyKey key, ValkeyValue[] values, CommandFlags flags);

    /// <inheritdoc cref="IListCommands.ListRightPopAsync(ValkeyKey, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="IListCommands.ListRightPopAsync(ValkeyKey, CommandFlags)" /></returns>
    IBatch ListRightPop(ValkeyKey key);

    /// <inheritdoc cref="IListCommands.ListRightPopAsync(ValkeyKey, long, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="IListCommands.ListRightPopAsync(ValkeyKey, long, CommandFlags)" /></returns>
    IBatch ListRightPop(ValkeyKey key, long count);

    /// <inheritdoc cref="IListCommands.ListRightPushAsync(ValkeyKey, ValkeyValue, When, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="IListCommands.ListRightPushAsync(ValkeyKey, ValkeyValue, When, CommandFlags)" /></returns>
    IBatch ListRightPush(ValkeyKey key, ValkeyValue value);

    /// <inheritdoc cref="IListCommands.ListRightPushAsync(ValkeyKey, ValkeyValue[], When, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="IListCommands.ListRightPushAsync(ValkeyKey, ValkeyValue[], When, CommandFlags)" /></returns>
    IBatch ListRightPush(ValkeyKey key, ValkeyValue[] values);

    /// <inheritdoc cref="IListCommands.ListRightPushAsync(ValkeyKey, ValkeyValue[], CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="IListCommands.ListRightPushAsync(ValkeyKey, ValkeyValue[], CommandFlags)" /></returns>
    IBatch ListRightPush(ValkeyKey key, ValkeyValue[] values, CommandFlags flags);

    /// <inheritdoc cref="IListCommands.ListLengthAsync(ValkeyKey, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="IListCommands.ListLengthAsync(ValkeyKey, CommandFlags)" /></returns>
    IBatch ListLength(ValkeyKey key);

    /// <inheritdoc cref="IListCommands.ListRemoveAsync(ValkeyKey, ValkeyValue, long, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="IListCommands.ListRemoveAsync(ValkeyKey, ValkeyValue, long, CommandFlags)" /></returns>
    IBatch ListRemove(ValkeyKey key, ValkeyValue value, long count = 0);

    /// <inheritdoc cref="IListCommands.ListTrimAsync(ValkeyKey, long, long, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="IListCommands.ListTrimAsync(ValkeyKey, long, long, CommandFlags)" /></returns>
    IBatch ListTrim(ValkeyKey key, long start, long stop);

    /// <inheritdoc cref="IListCommands.ListRangeAsync(ValkeyKey, long, long, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="IListCommands.ListRangeAsync(ValkeyKey, long, long, CommandFlags)" /></returns>
    IBatch ListRange(ValkeyKey key, long start = 0, long stop = -1);
}
