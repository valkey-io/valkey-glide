// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace Valkey.Glide.Commands;
internal interface IHashCommands
{
    /// <inheritdoc cref="IDatabase.HashGet(ValkeyKey, ValkeyValue, CommandFlags)"/>
    Task<ValkeyValue> HashGetAsync(ValkeyKey key, ValkeyValue hashField, CommandFlags flags = CommandFlags.None);

    /// <inheritdoc cref="IDatabase.HashGet(ValkeyKey, ValkeyValue[], CommandFlags)"/>
    Task<ValkeyValue[]> HashGetAsync(ValkeyKey key, ValkeyValue[] hashFields, CommandFlags flags = CommandFlags.None);

    /// <inheritdoc cref="IDatabase.HashGetAll(ValkeyKey, CommandFlags)"/>
    Task<HashEntry[]> HashGetAllAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None);

    /// <inheritdoc cref="IDatabase.HashSet(ValkeyKey, HashEntry[], CommandFlags)"/>
    Task HashSetAsync(ValkeyKey key, HashEntry[] hashFields, CommandFlags flags = CommandFlags.None);

    /// <inheritdoc cref="IDatabase.HashSet(ValkeyKey, ValkeyValue, ValkeyValue, When, CommandFlags)"/>
    Task<bool> HashSetAsync(ValkeyKey key, ValkeyValue hashField, ValkeyValue value, When when = When.Always, CommandFlags flags = CommandFlags.None);

    /// <inheritdoc cref="IDatabase.HashDelete(ValkeyKey, ValkeyValue, CommandFlags)"/>
    Task<bool> HashDeleteAsync(ValkeyKey key, ValkeyValue hashField, CommandFlags flags = CommandFlags.None);

    /// <inheritdoc cref="IDatabase.HashDelete(ValkeyKey, ValkeyValue[], CommandFlags)"/>
    Task<long> HashDeleteAsync(ValkeyKey key, ValkeyValue[] hashFields, CommandFlags flags = CommandFlags.None);

    /// <inheritdoc cref="IDatabase.HashExists(ValkeyKey, ValkeyValue, CommandFlags)"/>
    Task<bool> HashExistsAsync(ValkeyKey key, ValkeyValue hashField, CommandFlags flags = CommandFlags.None);

    /// <inheritdoc cref="IDatabase.HashLength(ValkeyKey, CommandFlags)"/>
    Task<long> HashLengthAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None);

    /// <inheritdoc cref="IDatabase.HashStringLength(ValkeyKey, ValkeyValue, CommandFlags)"/>
    Task<long> HashStringLengthAsync(ValkeyKey key, ValkeyValue hashField, CommandFlags flags = CommandFlags.None);

    /// <inheritdoc cref="IDatabase.HashValues(ValkeyKey, CommandFlags)"/>
    Task<ValkeyValue[]> HashValuesAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None);

    /// <inheritdoc cref="IDatabase.HashRandomField(ValkeyKey, CommandFlags)"/>
    Task<ValkeyValue> HashRandomFieldAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None);

    /// <inheritdoc cref="IDatabase.HashRandomFields(ValkeyKey, long, CommandFlags)"/>
    Task<ValkeyValue[]> HashRandomFieldsAsync(ValkeyKey key, long count, CommandFlags flags = CommandFlags.None);

    /// <inheritdoc cref="IDatabase.HashRandomFieldsWithValues(ValkeyKey, long, CommandFlags)"/>
    Task<HashEntry[]> HashRandomFieldsWithValuesAsync(ValkeyKey key, long count, CommandFlags flags = CommandFlags.None);
}
