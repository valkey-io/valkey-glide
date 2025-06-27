// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace Valkey.Glide.Commands;
internal interface IHashCommands
{
    /// <inheritdoc cref="IDatabase.HashDecrement(RedisKey, RedisValue, long, CommandFlags)"/>
    Task<long> HashDecrementAsync(RedisKey key, RedisValue hashField, long value = 1, CommandFlags flags = CommandFlags.None);

    /// <inheritdoc cref="IDatabase.HashDecrement(RedisKey, RedisValue, double, CommandFlags)"/>
    Task<double> HashDecrementAsync(RedisKey key, RedisValue hashField, double value, CommandFlags flags = CommandFlags.None);

    /// <inheritdoc cref="IDatabase.HashDelete(RedisKey, RedisValue, CommandFlags)"/>
    Task<bool> HashDeleteAsync(RedisKey key, RedisValue hashField, CommandFlags flags = CommandFlags.None);

    /// <inheritdoc cref="IDatabase.HashDelete(RedisKey, RedisValue[], CommandFlags)"/>
    Task<long> HashDeleteAsync(RedisKey key, RedisValue[] hashFields, CommandFlags flags = CommandFlags.None);

    /// <inheritdoc cref="IDatabase.HashExists(RedisKey, RedisValue, CommandFlags)"/>
    Task<bool> HashExistsAsync(RedisKey key, RedisValue hashField, CommandFlags flags = CommandFlags.None);

    /// <inheritdoc cref="IDatabase.HashFieldExpire(RedisKey, RedisValue[], TimeSpan, ExpireWhen, CommandFlags)"/>
    Task<ExpireResult[]> HashFieldExpireAsync(RedisKey key, RedisValue[] hashFields, TimeSpan expiry, ExpireWhen when = ExpireWhen.Always, CommandFlags flags = CommandFlags.None);

    /// <inheritdoc cref="IDatabase.HashFieldExpire(RedisKey, RedisValue[], DateTime, ExpireWhen, CommandFlags)"/>
    Task<ExpireResult[]> HashFieldExpireAsync(RedisKey key, RedisValue[] hashFields, DateTime expiry, ExpireWhen when = ExpireWhen.Always, CommandFlags flags = CommandFlags.None);

    /// <inheritdoc cref="IDatabase.HashFieldGetExpireDateTime(RedisKey, RedisValue[], CommandFlags)"/>
    Task<long[]> HashFieldGetExpireDateTimeAsync(RedisKey key, RedisValue[] hashFields, CommandFlags flags = CommandFlags.None);

    /// <inheritdoc cref="HashFieldPersistAsync(RedisKey, RedisValue[], CommandFlags)"/>
    Task<PersistResult[]> HashFieldPersistAsync(RedisKey key, RedisValue[] hashFields, CommandFlags flags = CommandFlags.None);

    /// <inheritdoc cref="IDatabase.HashFieldGetTimeToLive(RedisKey, RedisValue[], CommandFlags)"/>
    Task<long[]> HashFieldGetTimeToLiveAsync(RedisKey key, RedisValue[] hashFields, CommandFlags flags = CommandFlags.None);

    /// <inheritdoc cref="IDatabase.HashGet(RedisKey, RedisValue, CommandFlags)"/>
    Task<RedisValue> HashGetAsync(RedisKey key, RedisValue hashField, CommandFlags flags = CommandFlags.None);

    /// <inheritdoc cref="IDatabase.HashGetLease(RedisKey, RedisValue, CommandFlags)"/>
    //Task<Lease<byte>?> HashGetLeaseAsync(RedisKey key, RedisValue hashField, CommandFlags flags = CommandFlags.None);

    /// <inheritdoc cref="IDatabase.HashGet(RedisKey, RedisValue[], CommandFlags)"/>
    Task<RedisValue[]> HashGetAsync(RedisKey key, RedisValue[] hashFields, CommandFlags flags = CommandFlags.None);

    /// <inheritdoc cref="IDatabase.HashGetAll(RedisKey, CommandFlags)"/>
    Task<HashEntry[]> HashGetAllAsync(RedisKey key, CommandFlags flags = CommandFlags.None);

    /// <inheritdoc cref="IDatabase.HashIncrement(RedisKey, RedisValue, long, CommandFlags)"/>
    Task<long> HashIncrementAsync(RedisKey key, RedisValue hashField, long value = 1, CommandFlags flags = CommandFlags.None);

    /// <inheritdoc cref="IDatabase.HashIncrement(RedisKey, RedisValue, double, CommandFlags)"/>
    Task<double> HashIncrementAsync(RedisKey key, RedisValue hashField, double value, CommandFlags flags = CommandFlags.None);

    /// <inheritdoc cref="IDatabase.HashKeys(RedisKey, CommandFlags)"/>
    Task<RedisValue[]> HashKeysAsync(RedisKey key, CommandFlags flags = CommandFlags.None);

    /// <inheritdoc cref="IDatabase.HashLength(RedisKey, CommandFlags)"/>
    Task<long> HashLengthAsync(RedisKey key, CommandFlags flags = CommandFlags.None);

    /// <inheritdoc cref="IDatabase.HashRandomField(RedisKey, CommandFlags)"/>
    Task<RedisValue> HashRandomFieldAsync(RedisKey key, CommandFlags flags = CommandFlags.None);

    /// <inheritdoc cref="IDatabase.HashRandomFields(RedisKey, long, CommandFlags)"/>
    Task<RedisValue[]> HashRandomFieldsAsync(RedisKey key, long count, CommandFlags flags = CommandFlags.None);

    /// <inheritdoc cref="IDatabase.HashRandomFieldsWithValues(RedisKey, long, CommandFlags)"/>
    Task<HashEntry[]> HashRandomFieldsWithValuesAsync(RedisKey key, long count, CommandFlags flags = CommandFlags.None);

    /// <inheritdoc cref="IDatabase.HashScan(RedisKey, RedisValue, int, long, int, CommandFlags)"/>
    //IAsyncEnumerable<HashEntry> HashScanAsync(RedisKey key, RedisValue pattern = default, int pageSize = RedisBase.CursorUtils.DefaultLibraryPageSize, long cursor = RedisBase.CursorUtils.Origin, int pageOffset = 0, CommandFlags flags = CommandFlags.None);

    /// <inheritdoc cref="IDatabase.HashScanNoValues(RedisKey, RedisValue, int, long, int, CommandFlags)"/>
    //IAsyncEnumerable<RedisValue> HashScanNoValuesAsync(RedisKey key, RedisValue pattern = default, int pageSize = RedisBase.CursorUtils.DefaultLibraryPageSize, long cursor = RedisBase.CursorUtils.Origin, int pageOffset = 0, CommandFlags flags = CommandFlags.None);

    /// <inheritdoc cref="IDatabase.HashSet(RedisKey, HashEntry[], CommandFlags)"/>
    Task HashSetAsync(RedisKey key, HashEntry[] hashFields, CommandFlags flags = CommandFlags.None);

    /// <inheritdoc cref="IDatabase.HashSet(RedisKey, RedisValue, RedisValue, When, CommandFlags)"/>
    Task<bool> HashSetAsync(RedisKey key, RedisValue hashField, RedisValue value, When when = When.Always, CommandFlags flags = CommandFlags.None);

    /// <inheritdoc cref="IDatabase.HashStringLength(RedisKey, RedisValue, CommandFlags)"/>
    Task<long> HashStringLengthAsync(RedisKey key, RedisValue hashField, CommandFlags flags = CommandFlags.None);

    /// <inheritdoc cref="IDatabase.HashValues(RedisKey, CommandFlags)"/>
    Task<RedisValue[]> HashValuesAsync(RedisKey key, CommandFlags flags = CommandFlags.None);

}
