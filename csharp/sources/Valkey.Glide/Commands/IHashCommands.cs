// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Valkey.Glide.Commands;

internal interface IHashCommands
{
    Task<long> HashDecrementAsync(ValkeyKey key, ValkeyValue hashField, long value = 1, CommandFlags ignored = CommandFlags.None);

    Task<double> HashDecrementAsync(ValkeyKey key, ValkeyValue hashField, double value, CommandFlags ignored = CommandFlags.None);

    Task<bool> HashDeleteAsync(ValkeyKey key, ValkeyValue hashField, CommandFlags ignored = CommandFlags.None);

    Task<long> HashDeleteAsync(ValkeyKey key, ValkeyValue[] hashFields, CommandFlags ignored = CommandFlags.None);

    Task<bool> HashExistsAsync(ValkeyKey key, ValkeyValue hashField, CommandFlags ignored = CommandFlags.None);

    Task<ExpireResult[]> HashFieldExpireAsync(ValkeyKey key, ValkeyValue[] hashFields, TimeSpan expiry, ExpireWhen when = ExpireWhen.Always, CommandFlags ignored = CommandFlags.None);

    Task<ExpireResult[]> HashFieldExpireAsync(ValkeyKey key, ValkeyValue[] hashFields, DateTime expiry, ExpireWhen when = ExpireWhen.Always, CommandFlags ignored = CommandFlags.None);

    Task<long[]> HashFieldGetExpireDateTimeAsync(ValkeyKey key, ValkeyValue[] hashFields, CommandFlags ignored = CommandFlags.None);

    Task<PersistResult[]> HashFieldPersistAsync(ValkeyKey key, ValkeyValue[] hashFields, CommandFlags ignored = CommandFlags.None);

    Task<long[]> HashFieldGetTimeToLiveAsync(ValkeyKey key, ValkeyValue[] hashFields, CommandFlags ignored = CommandFlags.None);

    Task<ValkeyValue> HashGetAsync(ValkeyKey key, ValkeyValue hashField, CommandFlags ignored = CommandFlags.None);

    //Task<Lease<byte>?> HashGetLeaseAsync(ValkeyKey key, ValkeyValue hashField, CommandFlags ignored = CommandFlags.None);

    Task<ValkeyValue[]> HashGetAsync(ValkeyKey key, ValkeyValue[] hashFields, CommandFlags ignored = CommandFlags.None);

    Task<HashEntry[]> HashGetAllAsync(ValkeyKey key, CommandFlags ignored = CommandFlags.None);

    Task<long> HashIncrementAsync(ValkeyKey key, ValkeyValue hashField, long value = 1, CommandFlags ignored = CommandFlags.None);

    Task<double> HashIncrementAsync(ValkeyKey key, ValkeyValue hashField, double value, CommandFlags ignored = CommandFlags.None);

    Task<ValkeyValue[]> HashKeysAsync(ValkeyKey key, CommandFlags ignored = CommandFlags.None);

    Task<long> HashLengthAsync(ValkeyKey key, CommandFlags ignored = CommandFlags.None);

    Task<ValkeyValue> HashRandomFieldAsync(ValkeyKey key, CommandFlags ignored = CommandFlags.None);

    Task<ValkeyValue[]> HashRandomFieldsAsync(ValkeyKey key, long count, CommandFlags ignored = CommandFlags.None);

    Task<HashEntry[]> HashRandomFieldsWithValuesAsync(ValkeyKey key, long count, CommandFlags ignored = CommandFlags.None);

    Task HashSetAsync(ValkeyKey key, HashEntry[] hashFields, CommandFlags ignored = CommandFlags.None);

    Task<bool> HashSetAsync(ValkeyKey key, ValkeyValue hashField, ValkeyValue value, When when = When.Always, CommandFlags ignored = CommandFlags.None);

    Task<long> HashStringLengthAsync(ValkeyKey key, ValkeyValue hashField, CommandFlags ignored = CommandFlags.None);

    Task<ValkeyValue[]> HashValuesAsync(ValkeyKey key, CommandFlags ignored = CommandFlags.None);

}
