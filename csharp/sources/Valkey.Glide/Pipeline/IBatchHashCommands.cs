// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Commands;

namespace Valkey.Glide.Pipeline;

internal interface IBatchHashCommands
{
    /// <inheritdoc cref="IHashCommands.HashGetAsync(ValkeyKey, ValkeyValue, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="IHashCommands.HashGetAsync(ValkeyKey, ValkeyValue, CommandFlags)" /></returns>
    IBatch HashGet(ValkeyKey key, ValkeyValue hashField);

    /// <inheritdoc cref="IHashCommands.HashGetAsync(ValkeyKey, ValkeyValue[], CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="IHashCommands.HashGetAsync(ValkeyKey, ValkeyValue[], CommandFlags)" /></returns>
    IBatch HashGet(ValkeyKey key, ValkeyValue[] hashFields);

    /// <inheritdoc cref="IHashCommands.HashGetAllAsync(ValkeyKey, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="IHashCommands.HashGetAllAsync(ValkeyKey, CommandFlags)" /></returns>
    IBatch HashGetAll(ValkeyKey key);

    /// <inheritdoc cref="IHashCommands.HashSetAsync(ValkeyKey, HashEntry[], CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="IHashCommands.HashSetAsync(ValkeyKey, HashEntry[], CommandFlags)" /></returns>
    IBatch HashSet(ValkeyKey key, HashEntry[] hashFields);

    /// <inheritdoc cref="IHashCommands.HashSetAsync(ValkeyKey, ValkeyValue, ValkeyValue, When, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="IHashCommands.HashSetAsync(ValkeyKey, ValkeyValue, ValkeyValue, When, CommandFlags)" /></returns>
    IBatch HashSet(ValkeyKey key, ValkeyValue hashField, ValkeyValue value, When when = When.Always);

    /// <inheritdoc cref="IHashCommands.HashDeleteAsync(ValkeyKey, ValkeyValue, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="IHashCommands.HashDeleteAsync(ValkeyKey, ValkeyValue, CommandFlags)" /></returns>
    IBatch HashDelete(ValkeyKey key, ValkeyValue hashField);

    /// <inheritdoc cref="IHashCommands.HashDeleteAsync(ValkeyKey, ValkeyValue[], CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="IHashCommands.HashDeleteAsync(ValkeyKey, ValkeyValue[], CommandFlags)" /></returns>
    IBatch HashDelete(ValkeyKey key, ValkeyValue[] hashFields);

    /// <inheritdoc cref="IHashCommands.HashExistsAsync(ValkeyKey, ValkeyValue, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="IHashCommands.HashExistsAsync(ValkeyKey, ValkeyValue, CommandFlags)" /></returns>
    IBatch HashExists(ValkeyKey key, ValkeyValue hashField);

    /// <inheritdoc cref="IHashCommands.HashLengthAsync(ValkeyKey, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="IHashCommands.HashLengthAsync(ValkeyKey, CommandFlags)" /></returns>
    IBatch HashLength(ValkeyKey key);

    /// <inheritdoc cref="IHashCommands.HashStringLengthAsync(ValkeyKey, ValkeyValue, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="IHashCommands.HashStringLengthAsync(ValkeyKey, ValkeyValue, CommandFlags)" /></returns>
    IBatch HashStringLength(ValkeyKey key, ValkeyValue hashField);

    /// <inheritdoc cref="IHashCommands.HashValuesAsync(ValkeyKey, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="IHashCommands.HashValuesAsync(ValkeyKey, CommandFlags)" /></returns>
    IBatch HashValues(ValkeyKey key);

    /// <inheritdoc cref="IHashCommands.HashRandomFieldAsync(ValkeyKey, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="IHashCommands.HashRandomFieldAsync(ValkeyKey, CommandFlags)" /></returns>
    IBatch HashRandomField(ValkeyKey key);

    /// <inheritdoc cref="IHashCommands.HashRandomFieldsAsync(ValkeyKey, long, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="IHashCommands.HashRandomFieldsAsync(ValkeyKey, long, CommandFlags)" /></returns>
    IBatch HashRandomFields(ValkeyKey key, long count);

    /// <inheritdoc cref="IHashCommands.HashRandomFieldsWithValuesAsync(ValkeyKey, long, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="IHashCommands.HashRandomFieldsWithValuesAsync(ValkeyKey, long, CommandFlags)" /></returns>
    IBatch HashRandomFieldsWithValues(ValkeyKey key, long count);
}
