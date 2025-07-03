// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Valkey.Glide.Commands;
internal interface IHashCommands
{
    Task<long> HashDecrementAsync(ValkeyKey key, ValkeyValue hashField, long value = 1, CommandFlags flags = CommandFlags.None);

    Task<double> HashDecrementAsync(ValkeyKey key, ValkeyValue hashField, double value, CommandFlags flags = CommandFlags.None);

    Task<bool> HashDeleteAsync(ValkeyKey key, ValkeyValue hashField, CommandFlags flags = CommandFlags.None);

    Task<long> HashDeleteAsync(ValkeyKey key, ValkeyValue[] hashFields, CommandFlags flags = CommandFlags.None);

    Task<bool> HashExistsAsync(ValkeyKey key, ValkeyValue hashField, CommandFlags flags = CommandFlags.None);

    Task<ExpireResult[]> HashFieldExpireAsync(ValkeyKey key, ValkeyValue[] hashFields, TimeSpan expiry, ExpireWhen when = ExpireWhen.Always, CommandFlags flags = CommandFlags.None);

    Task<ExpireResult[]> HashFieldExpireAsync(ValkeyKey key, ValkeyValue[] hashFields, DateTime expiry, ExpireWhen when = ExpireWhen.Always, CommandFlags flags = CommandFlags.None);

    Task<long[]> HashFieldGetExpireDateTimeAsync(ValkeyKey key, ValkeyValue[] hashFields, CommandFlags flags = CommandFlags.None);

    Task<PersistResult[]> HashFieldPersistAsync(ValkeyKey key, ValkeyValue[] hashFields, CommandFlags flags = CommandFlags.None);

    Task<long[]> HashFieldGetTimeToLiveAsync(ValkeyKey key, ValkeyValue[] hashFields, CommandFlags flags = CommandFlags.None);

    Task<ValkeyValue> HashGetAsync(ValkeyKey key, ValkeyValue hashField, CommandFlags flags = CommandFlags.None);

    //Task<Lease<byte>?> HashGetLeaseAsync(ValkeyKey key, ValkeyValue hashField, CommandFlags flags = CommandFlags.None);

    Task<ValkeyValue[]> HashGetAsync(ValkeyKey key, ValkeyValue[] hashFields, CommandFlags flags = CommandFlags.None);

    Task<HashEntry[]> HashGetAllAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None);

    Task<long> HashIncrementAsync(ValkeyKey key, ValkeyValue hashField, long value = 1, CommandFlags flags = CommandFlags.None);

    Task<double> HashIncrementAsync(ValkeyKey key, ValkeyValue hashField, double value, CommandFlags flags = CommandFlags.None);

    Task<ValkeyValue[]> HashKeysAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None);

    Task<long> HashLengthAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None);

    Task<ValkeyValue> HashRandomFieldAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None);

    Task<ValkeyValue[]> HashRandomFieldsAsync(ValkeyKey key, long count, CommandFlags flags = CommandFlags.None);

    Task<HashEntry[]> HashRandomFieldsWithValuesAsync(ValkeyKey key, long count, CommandFlags flags = CommandFlags.None);

    Task HashSetAsync(ValkeyKey key, HashEntry[] hashFields, CommandFlags flags = CommandFlags.None);

    Task<bool> HashSetAsync(ValkeyKey key, ValkeyValue hashField, ValkeyValue value, When when = When.Always, CommandFlags flags = CommandFlags.None);

    Task<long> HashStringLengthAsync(ValkeyKey key, ValkeyValue hashField, CommandFlags flags = CommandFlags.None);

    Task<ValkeyValue[]> HashValuesAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None);

}
