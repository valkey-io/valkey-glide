// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using static Valkey.Glide.Internals.Request;

namespace Valkey.Glide.Pipeline;

/// <summary>
/// Hash commands for BaseBatch.
/// </summary>
public abstract partial class BaseBatch<T>
{
    /// <inheritdoc cref="IBatchHashCommands.HashGet(ValkeyKey, ValkeyValue)" />
    public T HashGet(ValkeyKey key, ValkeyValue hashField) => AddCmd(HashGetAsync(key, hashField));

    /// <inheritdoc cref="IBatchHashCommands.HashGet(ValkeyKey, ValkeyValue[])" />
    public T HashGet(ValkeyKey key, ValkeyValue[] hashFields) => AddCmd(HashGetAsync(key, hashFields));

    /// <inheritdoc cref="IBatchHashCommands.HashGetAll(ValkeyKey)" />
    public T HashGetAll(ValkeyKey key) => AddCmd(HashGetAllAsync(key));

    /// <inheritdoc cref="IBatchHashCommands.HashSet(ValkeyKey, HashEntry[])" />
    public T HashSet(ValkeyKey key, HashEntry[] hashFields) => AddCmd(HashSetAsync(key, hashFields));

    /// <inheritdoc cref="IBatchHashCommands.HashSet(ValkeyKey, ValkeyValue, ValkeyValue, When)" />
    public T HashSet(ValkeyKey key, ValkeyValue hashField, ValkeyValue value, When when = When.Always) => AddCmd(HashSetAsync(key, hashField, value, when));

    /// <inheritdoc cref="IBatchHashCommands.HashDelete(ValkeyKey, ValkeyValue)" />
    public T HashDelete(ValkeyKey key, ValkeyValue hashField) => AddCmd(HashDeleteAsync(key, hashField));

    /// <inheritdoc cref="IBatchHashCommands.HashDelete(ValkeyKey, ValkeyValue[])" />
    public T HashDelete(ValkeyKey key, ValkeyValue[] hashFields) => AddCmd(HashDeleteAsync(key, hashFields));

    /// <inheritdoc cref="IBatchHashCommands.HashExists(ValkeyKey, ValkeyValue)" />
    public T HashExists(ValkeyKey key, ValkeyValue hashField) => AddCmd(HashExistsAsync(key, hashField));

    /// <inheritdoc cref="IBatchHashCommands.HashLength(ValkeyKey)" />
    public T HashLength(ValkeyKey key) => AddCmd(HashLengthAsync(key));

    /// <inheritdoc cref="IBatchHashCommands.HashStringLength(ValkeyKey, ValkeyValue)" />
    public T HashStringLength(ValkeyKey key, ValkeyValue hashField) => AddCmd(HashStringLengthAsync(key, hashField));

    /// <inheritdoc cref="IBatchHashCommands.HashValues(ValkeyKey)" />
    public T HashValues(ValkeyKey key) => AddCmd(HashValuesAsync(key));

    /// <inheritdoc cref="IBatchHashCommands.HashRandomField(ValkeyKey)" />
    public T HashRandomField(ValkeyKey key) => AddCmd(HashRandomFieldAsync(key));

    /// <inheritdoc cref="IBatchHashCommands.HashRandomFields(ValkeyKey, long)" />
    public T HashRandomFields(ValkeyKey key, long count) => AddCmd(HashRandomFieldsAsync(key, count));

    /// <inheritdoc cref="IBatchHashCommands.HashRandomFieldsWithValues(ValkeyKey, long)" />
    public T HashRandomFieldsWithValues(ValkeyKey key, long count) => AddCmd(HashRandomFieldsWithValuesAsync(key, count));

    // Explicit interface implementations for IBatchHashCommands
    IBatch IBatchHashCommands.HashGet(ValkeyKey key, ValkeyValue hashField) => HashGet(key, hashField);
    IBatch IBatchHashCommands.HashGet(ValkeyKey key, ValkeyValue[] hashFields) => HashGet(key, hashFields);
    IBatch IBatchHashCommands.HashGetAll(ValkeyKey key) => HashGetAll(key);
    IBatch IBatchHashCommands.HashSet(ValkeyKey key, HashEntry[] hashFields) => HashSet(key, hashFields);
    IBatch IBatchHashCommands.HashSet(ValkeyKey key, ValkeyValue hashField, ValkeyValue value, When when) => HashSet(key, hashField, value, when);
    IBatch IBatchHashCommands.HashDelete(ValkeyKey key, ValkeyValue hashField) => HashDelete(key, hashField);
    IBatch IBatchHashCommands.HashDelete(ValkeyKey key, ValkeyValue[] hashFields) => HashDelete(key, hashFields);
    IBatch IBatchHashCommands.HashExists(ValkeyKey key, ValkeyValue hashField) => HashExists(key, hashField);
    IBatch IBatchHashCommands.HashLength(ValkeyKey key) => HashLength(key);
    IBatch IBatchHashCommands.HashStringLength(ValkeyKey key, ValkeyValue hashField) => HashStringLength(key, hashField);
    IBatch IBatchHashCommands.HashValues(ValkeyKey key) => HashValues(key);
    IBatch IBatchHashCommands.HashRandomField(ValkeyKey key) => HashRandomField(key);
    IBatch IBatchHashCommands.HashRandomFields(ValkeyKey key, long count) => HashRandomFields(key, count);
    IBatch IBatchHashCommands.HashRandomFieldsWithValues(ValkeyKey key, long count) => HashRandomFieldsWithValues(key, count);
}
