// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace Valkey.Glide.Commands;
internal interface IHashCommands
{
    /// <inheritdoc cref="IDatabase.HashDecrement(ValkeyKey, ValkeyValue, long, CommandFlags)"/>
    Task<long> HashDecrementAsync(ValkeyKey key, ValkeyValue hashField, long value = 1, CommandFlags flags = CommandFlags.None);

    /// <inheritdoc cref="IDatabase.HashDecrement(ValkeyKey, ValkeyValue, double, CommandFlags)"/>
    Task<double> HashDecrementAsync(ValkeyKey key, ValkeyValue hashField, double value, CommandFlags flags = CommandFlags.None);

    /// <inheritdoc cref="IDatabase.HashDelete(ValkeyKey, ValkeyValue, CommandFlags)"/>
    Task<bool> HashDeleteAsync(ValkeyKey key, ValkeyValue hashField, CommandFlags flags = CommandFlags.None);

    /// <inheritdoc cref="IDatabase.HashDelete(ValkeyKey, ValkeyValue[], CommandFlags)"/>
    Task<long> HashDeleteAsync(ValkeyKey key, ValkeyValue[] hashFields, CommandFlags flags = CommandFlags.None);

    /// <inheritdoc cref="IDatabase.HashExists(ValkeyKey, ValkeyValue, CommandFlags)"/>
    Task<bool> HashExistsAsync(ValkeyKey key, ValkeyValue hashField, CommandFlags flags = CommandFlags.None);

    /// <inheritdoc cref="IDatabase.HashFieldExpire(ValkeyKey, ValkeyValue[], TimeSpan, ExpireWhen, CommandFlags)"/>
    Task<ExpireResult[]> HashFieldExpireAsync(ValkeyKey key, ValkeyValue[] hashFields, TimeSpan expiry, ExpireWhen when = ExpireWhen.Always, CommandFlags flags = CommandFlags.None);

    /// <inheritdoc cref="IDatabase.HashFieldExpire(ValkeyKey, ValkeyValue[], DateTime, ExpireWhen, CommandFlags)"/>
    Task<ExpireResult[]> HashFieldExpireAsync(ValkeyKey key, ValkeyValue[] hashFields, DateTime expiry, ExpireWhen when = ExpireWhen.Always, CommandFlags flags = CommandFlags.None);

    /// <inheritdoc cref="IDatabase.HashFieldGetExpireDateTime(ValkeyKey, ValkeyValue[], CommandFlags)"/>
    Task<long[]> HashFieldGetExpireDateTimeAsync(ValkeyKey key, ValkeyValue[] hashFields, CommandFlags flags = CommandFlags.None);

    /// <inheritdoc cref="HashFieldPersistAsync(ValkeyKey, ValkeyValue[], CommandFlags)"/>
    Task<PersistResult[]> HashFieldPersistAsync(ValkeyKey key, ValkeyValue[] hashFields, CommandFlags flags = CommandFlags.None);

    /// <inheritdoc cref="IDatabase.HashFieldGetTimeToLive(ValkeyKey, ValkeyValue[], CommandFlags)"/>
    Task<long[]> HashFieldGetTimeToLiveAsync(ValkeyKey key, ValkeyValue[] hashFields, CommandFlags flags = CommandFlags.None);

    /// <inheritdoc cref="IDatabase.HashGet(ValkeyKey, ValkeyValue, CommandFlags)"/>
    Task<ValkeyValue> HashGetAsync(ValkeyKey key, ValkeyValue hashField, CommandFlags flags = CommandFlags.None);

    /// <inheritdoc cref="IDatabase.HashGetLease(ValkeyKey, ValkeyValue, CommandFlags)"/>
    //Task<Lease<byte>?> HashGetLeaseAsync(ValkeyKey key, ValkeyValue hashField, CommandFlags flags = CommandFlags.None);

    /// <inheritdoc cref="IDatabase.HashGet(ValkeyKey, ValkeyValue[], CommandFlags)"/>
    Task<ValkeyValue[]> HashGetAsync(ValkeyKey key, ValkeyValue[] hashFields, CommandFlags flags = CommandFlags.None);

    /// <inheritdoc cref="IDatabase.HashGetAll(ValkeyKey, CommandFlags)"/>
    Task<HashEntry[]> HashGetAllAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None);

    /// <inheritdoc cref="IDatabase.HashIncrement(ValkeyKey, ValkeyValue, long, CommandFlags)"/>
    Task<long> HashIncrementAsync(ValkeyKey key, ValkeyValue hashField, long value = 1, CommandFlags flags = CommandFlags.None);

    /// <inheritdoc cref="IDatabase.HashIncrement(ValkeyKey, ValkeyValue, double, CommandFlags)"/>
    Task<double> HashIncrementAsync(ValkeyKey key, ValkeyValue hashField, double value, CommandFlags flags = CommandFlags.None);

    /// <inheritdoc cref="IDatabase.HashKeys(ValkeyKey, CommandFlags)"/>
    Task<ValkeyValue[]> HashKeysAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None);

    /// <inheritdoc cref="IDatabase.HashLength(ValkeyKey, CommandFlags)"/>
    Task<long> HashLengthAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None);

    /// <inheritdoc cref="IDatabase.HashRandomField(ValkeyKey, CommandFlags)"/>
    Task<ValkeyValue> HashRandomFieldAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None);

    /// <inheritdoc cref="IDatabase.HashRandomFields(ValkeyKey, long, CommandFlags)"/>
    Task<ValkeyValue[]> HashRandomFieldsAsync(ValkeyKey key, long count, CommandFlags flags = CommandFlags.None);

    /// <inheritdoc cref="IDatabase.HashRandomFieldsWithValues(ValkeyKey, long, CommandFlags)"/>
    Task<HashEntry[]> HashRandomFieldsWithValuesAsync(ValkeyKey key, long count, CommandFlags flags = CommandFlags.None);

    /// <inheritdoc cref="IDatabase.HashScan(ValkeyKey, ValkeyValue, int, long, int, CommandFlags)"/>
    //IAsyncEnumerable<HashEntry> HashScanAsync(ValkeyKey key, ValkeyValue pattern = default, int pageSize = ValkeyBase.CursorUtils.DefaultLibraryPageSize, long cursor = ValkeyBase.CursorUtils.Origin, int pageOffset = 0, CommandFlags flags = CommandFlags.None);

    /// <inheritdoc cref="IDatabase.HashScanNoValues(ValkeyKey, ValkeyValue, int, long, int, CommandFlags)"/>
    //IAsyncEnumerable<ValkeyValue> HashScanNoValuesAsync(ValkeyKey key, ValkeyValue pattern = default, int pageSize = ValkeyBase.CursorUtils.DefaultLibraryPageSize, long cursor = ValkeyBase.CursorUtils.Origin, int pageOffset = 0, CommandFlags flags = CommandFlags.None);

    /// <inheritdoc cref="IDatabase.HashSet(ValkeyKey, HashEntry[], CommandFlags)"/>
    Task HashSetAsync(ValkeyKey key, HashEntry[] hashFields, CommandFlags flags = CommandFlags.None);

    /// <inheritdoc cref="IDatabase.HashSet(ValkeyKey, ValkeyValue, ValkeyValue, When, CommandFlags)"/>
    Task<bool> HashSetAsync(ValkeyKey key, ValkeyValue hashField, ValkeyValue value, When when = When.Always, CommandFlags flags = CommandFlags.None);

    /// <inheritdoc cref="IDatabase.HashStringLength(ValkeyKey, ValkeyValue, CommandFlags)"/>
    Task<long> HashStringLengthAsync(ValkeyKey key, ValkeyValue hashField, CommandFlags flags = CommandFlags.None);

    /// <inheritdoc cref="IDatabase.HashValues(ValkeyKey, CommandFlags)"/>
    Task<ValkeyValue[]> HashValuesAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None);

}
