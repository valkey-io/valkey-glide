// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Commands;

using static Valkey.Glide.Commands.Options.InfoOptions;

namespace Valkey.Glide.Pipeline;

// BaseBatch was split into two types, one for docs, another for the impl. This also ease the testing.
internal interface IBatch
{
    // inherit all docs except `remarks` section which stores en example (not relevant for batch)
    // and returns section, because we customize it.

    /// <inheritdoc cref="IGenericCommands.CustomCommand(GlideString[])" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="IGenericCommands.CustomCommand(GlideString[])" /></returns>
    IBatch CustomCommand(GlideString[] args);

    /// <inheritdoc cref="IStringBaseCommands.Get(GlideString)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="IStringBaseCommands.Get(GlideString)" /></returns>
    IBatch Get(GlideString key);

    /// <inheritdoc cref="IStringBaseCommands.Set(GlideString, GlideString)" path="/summary" />
    /// <inheritdoc cref="IStringBaseCommands.Set(GlideString, GlideString)" path="/param" />
    /// <returns>Command Response - <inheritdoc cref="IStringBaseCommands.Set(GlideString, GlideString)" /></returns>
    IBatch Set(GlideString key, GlideString value);

    /// <inheritdoc cref="IServerManagementCommands.Info()" path="/summary" />
    /// <returns>Command Response - <inheritdoc cref="IServerManagementCommands.Info()" /></returns>
    IBatch Info();

    /// <inheritdoc cref="IServerManagementCommands.Info(Section[])" path="/summary" />
    /// <inheritdoc cref="IServerManagementCommands.Info(Section[])" path="/param" />
    /// <returns>Command Response - <inheritdoc cref="IServerManagementCommands.Info(Section[])" /></returns>
    IBatch Info(Section[] sections);

    /// <inheritdoc cref="ISetCommands.SetAddAsync(ValkeyKey, ValkeyValue, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="ISetCommands.SetAddAsync(ValkeyKey, ValkeyValue, CommandFlags)" /></returns>
    IBatch SetAdd(ValkeyKey key, ValkeyValue value);

    /// <inheritdoc cref="ISetCommands.SetAddAsync(ValkeyKey, ValkeyValue[], CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="ISetCommands.SetAddAsync(ValkeyKey, ValkeyValue[], CommandFlags)" /></returns>
    IBatch SetAdd(ValkeyKey key, ValkeyValue[] values);

    /// <inheritdoc cref="ISetCommands.SetRemoveAsync(ValkeyKey, ValkeyValue, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="ISetCommands.SetRemoveAsync(ValkeyKey, ValkeyValue, CommandFlags)" /></returns>
    IBatch SetRemove(ValkeyKey key, ValkeyValue value);

    /// <inheritdoc cref="ISetCommands.SetRemoveAsync(ValkeyKey, ValkeyValue[], CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="ISetCommands.SetRemoveAsync(ValkeyKey, ValkeyValue[], CommandFlags)" /></returns>
    IBatch SetRemove(ValkeyKey key, ValkeyValue[] values);

    /// <inheritdoc cref="ISetCommands.SetMembersAsync(ValkeyKey, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="ISetCommands.SetMembersAsync(ValkeyKey, CommandFlags)" /></returns>
    IBatch SetMembers(ValkeyKey key);

    /// <inheritdoc cref="ISetCommands.SetLengthAsync(ValkeyKey, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="ISetCommands.SetLengthAsync(ValkeyKey, CommandFlags)" /></returns>
    IBatch SetLength(ValkeyKey key);

    /// <inheritdoc cref="ISetCommands.SetIntersectionLengthAsync(ValkeyKey[], long, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="ISetCommands.SetIntersectionLengthAsync(ValkeyKey[], long, CommandFlags)" /></returns>
    IBatch SetIntersectionLength(ValkeyKey[] keys, long limit = 0);

    /// <inheritdoc cref="ISetCommands.SetPopAsync(ValkeyKey, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="ISetCommands.SetPopAsync(ValkeyKey, CommandFlags)" /></returns>
    IBatch SetPop(ValkeyKey key);

    /// <inheritdoc cref="ISetCommands.SetPopAsync(ValkeyKey, long, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="ISetCommands.SetPopAsync(ValkeyKey, long, CommandFlags)" /></returns>
    IBatch SetPop(ValkeyKey key, long count);

    /// <inheritdoc cref="ISetCommands.SetUnionAsync(ValkeyKey, ValkeyKey, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="ISetCommands.SetUnionAsync(ValkeyKey, ValkeyKey, CommandFlags)" /></returns>
    IBatch SetUnion(ValkeyKey first, ValkeyKey second);

    /// <inheritdoc cref="ISetCommands.SetUnionAsync(ValkeyKey[], CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="ISetCommands.SetUnionAsync(ValkeyKey[], CommandFlags)" /></returns>
    IBatch SetUnion(ValkeyKey[] keys);

    /// <inheritdoc cref="ISetCommands.SetIntersectAsync(ValkeyKey, ValkeyKey, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="ISetCommands.SetIntersectAsync(ValkeyKey, ValkeyKey, CommandFlags)" /></returns>
    IBatch SetIntersect(ValkeyKey first, ValkeyKey second);

    /// <inheritdoc cref="ISetCommands.SetIntersectAsync(ValkeyKey[], CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="ISetCommands.SetIntersectAsync(ValkeyKey[], CommandFlags)" /></returns>
    IBatch SetIntersect(ValkeyKey[] keys);

    /// <inheritdoc cref="ISetCommands.SetDifferenceAsync(ValkeyKey, ValkeyKey, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="ISetCommands.SetDifferenceAsync(ValkeyKey, ValkeyKey, CommandFlags)" /></returns>
    IBatch SetDifference(ValkeyKey first, ValkeyKey second);

    /// <inheritdoc cref="ISetCommands.SetDifferenceAsync(ValkeyKey[], CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="ISetCommands.SetDifferenceAsync(ValkeyKey[], CommandFlags)" /></returns>
    IBatch SetDifference(ValkeyKey[] keys);

    /// <inheritdoc cref="ISetCommands.SetUnionStoreAsync(ValkeyKey, ValkeyKey, ValkeyKey, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="ISetCommands.SetUnionStoreAsync(ValkeyKey, ValkeyKey, ValkeyKey, CommandFlags)" /></returns>
    IBatch SetUnionStore(ValkeyKey destination, ValkeyKey first, ValkeyKey second);

    /// <inheritdoc cref="ISetCommands.SetUnionStoreAsync(ValkeyKey, ValkeyKey[], CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="ISetCommands.SetUnionStoreAsync(ValkeyKey, ValkeyKey[], CommandFlags)" /></returns>
    IBatch SetUnionStore(ValkeyKey destination, ValkeyKey[] keys);

    /// <inheritdoc cref="ISetCommands.SetIntersectStoreAsync(ValkeyKey, ValkeyKey, ValkeyKey, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="ISetCommands.SetIntersectStoreAsync(ValkeyKey, ValkeyKey, ValkeyKey, CommandFlags)" /></returns>
    IBatch SetIntersectStore(ValkeyKey destination, ValkeyKey first, ValkeyKey second);

    /// <inheritdoc cref="ISetCommands.SetIntersectStoreAsync(ValkeyKey, ValkeyKey[], CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="ISetCommands.SetIntersectStoreAsync(ValkeyKey, ValkeyKey[], CommandFlags)" /></returns>
    IBatch SetIntersectStore(ValkeyKey destination, ValkeyKey[] keys);

    /// <inheritdoc cref="ISetCommands.SetDifferenceStoreAsync(ValkeyKey, ValkeyKey, ValkeyKey, CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="ISetCommands.SetDifferenceStoreAsync(ValkeyKey, ValkeyKey, ValkeyKey, CommandFlags)" /></returns>
    IBatch SetDifferenceStore(ValkeyKey destination, ValkeyKey first, ValkeyKey second);

    /// <inheritdoc cref="ISetCommands.SetDifferenceStoreAsync(ValkeyKey, ValkeyKey[], CommandFlags)" path="/*[not(self::remarks) and not(self::returns)]" />
    /// <returns>Command Response - <inheritdoc cref="ISetCommands.SetDifferenceStoreAsync(ValkeyKey, ValkeyKey[], CommandFlags)" /></returns>
    IBatch SetDifferenceStore(ValkeyKey destination, ValkeyKey[] keys);
}
