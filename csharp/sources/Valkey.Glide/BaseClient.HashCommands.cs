// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

using static Valkey.Glide.Internals.FFI;

using Valkey.Glide.Commands;

namespace Valkey.Glide;
public abstract partial class BaseClient : IHashCommands
{
    public Task<bool> HashDeleteAsync(ValkeyKey key, ValkeyValue hashField, CommandFlags flags = CommandFlags.None) => throw new NotImplementedException();
    public Task<long> HashDeleteAsync(ValkeyKey key, ValkeyValue[] hashFields, CommandFlags flags = CommandFlags.None) => throw new NotImplementedException();
    public Task<bool> HashExistsAsync(ValkeyKey key, ValkeyValue hashField, CommandFlags flags = CommandFlags.None) => throw new NotImplementedException();
    public Task<HashEntry[]> HashGetAllAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None) => throw new NotImplementedException();
    public Task<ValkeyValue> HashGetAsync(ValkeyKey key, ValkeyValue hashField, CommandFlags flags = CommandFlags.None)
    {
        this.Command(RequestType.HGet, key, hashField, flags);
    }

    public Task<ValkeyValue[]> HashGetAsync(ValkeyKey key, ValkeyValue[] hashFields, CommandFlags flags = CommandFlags.None) => throw new NotImplementedException();
    public Task<long> HashLengthAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None) => throw new NotImplementedException();
    public Task<ValkeyValue> HashRandomFieldAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None) => throw new NotImplementedException();
    public Task<ValkeyValue[]> HashRandomFieldsAsync(ValkeyKey key, long count, CommandFlags flags = CommandFlags.None) => throw new NotImplementedException();
    public Task<HashEntry[]> HashRandomFieldsWithValuesAsync(ValkeyKey key, long count, CommandFlags flags = CommandFlags.None) => throw new NotImplementedException();
    public Task HashSetAsync(ValkeyKey key, HashEntry[] hashFields, CommandFlags flags = CommandFlags.None) => throw new NotImplementedException();
    public Task<bool> HashSetAsync(ValkeyKey key, ValkeyValue hashField, ValkeyValue value, When when = When.Always, CommandFlags flags = CommandFlags.None) => throw new NotImplementedException();
    public Task<long> HashStringLengthAsync(ValkeyKey key, ValkeyValue hashField, CommandFlags flags = CommandFlags.None) => throw new NotImplementedException();
    public Task<ValkeyValue[]> HashValuesAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None) => throw new NotImplementedException();
}
