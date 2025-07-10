// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Commands.Options;
using Valkey.Glide.Internals;

using static Valkey.Glide.ConnectionConfiguration;

namespace Valkey.Glide.UnitTests;

public class CommandTests
{
    [Fact]
    public void ValidateCommandArgs()
    {
        Assert.Multiple(
            () => Assert.Equal(new string[] {"get", "a"}, Request.CustomCommand(new gs[] {"get", "a"}).GetArgs()),
            () => Assert.Equal(new string[] {"ping", "pong", "pang"}, Request.CustomCommand(new gs[] {"ping", "pong", "pang"}).GetArgs()),
            () => Assert.Equal(new string[] {"get"}, Request.CustomCommand(new gs[] {"get"}).GetArgs()),
            () => Assert.Equal(new string[] {}, Request.CustomCommand(new gs[] {}).GetArgs()),

            () => Assert.Equal(new string[] {"SET", "a", "b"}, Request.Set("a", "b").GetArgs()),
            () => Assert.Equal(new string[] {"GET", "a"}, Request.Get("a").GetArgs()),
            () => Assert.Equal(new string[] {"INFO"}, Request.Info(new InfoOptions.Section[] {}).GetArgs()),
            () => Assert.Equal(new string[] {"INFO", "CLIENTS", "CPU"}, Request.Info(new InfoOptions.Section[] {InfoOptions.Section.CLIENTS, InfoOptions.Section.CPU}).GetArgs()),

            // Set Commands
            () => Assert.Equal(new string[] {"SADD", "key", "member"}, Request.SetAddAsync("key", "member").GetArgs()),
            () => Assert.Equal(new string[] {"SADD", "key", "member1", "member2"}, Request.SetAddAsync("key", new ValkeyValue[] {"member1", "member2"}).GetArgs()),
            () => Assert.Equal(new string[] {"SREM", "key", "member"}, Request.SetRemoveAsync("key", "member").GetArgs()),
            () => Assert.Equal(new string[] {"SREM", "key", "member1", "member2"}, Request.SetRemoveAsync("key", new ValkeyValue[] {"member1", "member2"}).GetArgs()),
            () => Assert.Equal(new string[] {"SMEMBERS", "key"}, Request.SetMembersAsync("key").GetArgs()),
            () => Assert.Equal(new string[] {"SCARD", "key"}, Request.SetLengthAsync("key").GetArgs()),
            () => Assert.Equal(new string[] {"SINTERCARD", "2", "key1", "key2"}, Request.SetIntersectionLengthAsync(new ValkeyKey[] {"key1", "key2"}).GetArgs()),
            () => Assert.Equal(new string[] {"SINTERCARD", "2", "key1", "key2", "LIMIT", "10"}, Request.SetIntersectionLengthAsync(new ValkeyKey[] {"key1", "key2"}, 10).GetArgs()),
            () => Assert.Equal(new string[] {"SPOP", "key"}, Request.SetPopAsync("key").GetArgs()),
            () => Assert.Equal(new string[] {"SPOP", "key", "3"}, Request.SetPopAsync("key", 3).GetArgs()),
            () => Assert.Equal(new string[] {"SUNION", "key1", "key2"}, Request.SetUnionAsync(new ValkeyKey[] {"key1", "key2"}).GetArgs()),
            () => Assert.Equal(new string[] {"SINTER", "key1", "key2"}, Request.SetIntersectAsync(new ValkeyKey[] {"key1", "key2"}).GetArgs()),
            () => Assert.Equal(new string[] {"SDIFF", "key1", "key2"}, Request.SetDifferenceAsync(new ValkeyKey[] {"key1", "key2"}).GetArgs()),
            () => Assert.Equal(new string[] {"SUNIONSTORE", "dest", "key1", "key2"}, Request.SetUnionStoreAsync("dest", new ValkeyKey[] {"key1", "key2"}).GetArgs()),
            () => Assert.Equal(new string[] {"SINTERSTORE", "dest", "key1", "key2"}, Request.SetIntersectStoreAsync("dest", new ValkeyKey[] {"key1", "key2"}).GetArgs()),
            () => Assert.Equal(new string[] {"SDIFFSTORE", "dest", "key1", "key2"}, Request.SetDifferenceStoreAsync("dest", new ValkeyKey[] {"key1", "key2"}).GetArgs()),

            // Hash Commands
            () => Assert.Equal(new string[] {"HGET", "key", "field"}, Request.HashGetAsync("key", "field").GetArgs()),
            () => Assert.Equal(new string[] {"HMGET", "key", "field1", "field2"}, Request.HashGetAsync("key", new ValkeyValue[] {"field1", "field2"}).GetArgs()),
            () => Assert.Equal(new string[] {"HGETALL", "key"}, Request.HashGetAllAsync("key").GetArgs()),
            () => Assert.Equal(new string[] {"HMSET", "key", "field1", "value1", "field2", "value2"}, Request.HashSetAsync("key", new HashEntry[] {new HashEntry("field1", "value1"), new HashEntry("field2", "value2")}).GetArgs()),
            () => Assert.Equal(new string[] {"HMSET", "key", "field", "value"}, Request.HashSetAsync("key", new HashEntry[] { new HashEntry("field", "value") }).GetArgs()),
            () => Assert.Equal(new string[] {"HDEL", "key", "field"}, Request.HashDeleteAsync("key", "field").GetArgs()),
            () => Assert.Equal(new string[] {"HDEL", "key", "field1", "field2"}, Request.HashDeleteAsync("key", new ValkeyValue[] {"field1", "field2"}).GetArgs()),
            () => Assert.Equal(new string[] {"HEXISTS", "key", "field"}, Request.HashExistsAsync("key", "field").GetArgs()),
            () => Assert.Equal(new string[] {"HLEN", "key"}, Request.HashLengthAsync("key").GetArgs()),
            () => Assert.Equal(new string[] {"HSTRLEN", "key", "field"}, Request.HashStringLengthAsync("key", "field").GetArgs()),
            () => Assert.Equal(new string[] {"HVALS", "key"}, Request.HashValuesAsync("key").GetArgs()),
            () => Assert.Equal(new string[] {"HRANDFIELD", "key"}, Request.HashRandomFieldAsync("key").GetArgs()),
            () => Assert.Equal(new string[] {"HRANDFIELD", "key", "3"}, Request.HashRandomFieldsAsync("key", 3).GetArgs()),
            () => Assert.Equal(new string[] {"HRANDFIELD", "key", "3", "WITHVALUES"}, Request.HashRandomFieldsWithValuesAsync("key", 3).GetArgs())
        );
    }

    [Fact]
    public void ValidateCommandConverters()
    {
        Assert.Multiple(
            () => Assert.Equal(1, Request.CustomCommand([]).Converter(1)),
            () => Assert.Equal(.1, Request.CustomCommand([]).Converter(.1)),
            () => Assert.Null(Request.CustomCommand([]).Converter(null)),

            () => Assert.Equal("OK", Request.Set("a", "b").Converter("OK")),
            () => Assert.Equal<GlideString>("OK", Request.Get("a").Converter("OK")),
            () => Assert.Null(Request.Get("a").Converter(null)),
            () => Assert.Equal("info", Request.Info([]).Converter("info")),

            () => Assert.True(Request.SetAddAsync("key", "member").Converter(1L)),
            () => Assert.False(Request.SetAddAsync("key", "member").Converter(0L)),
            () => Assert.True(Request.SetRemoveAsync("key", "member").Converter(1L)),
            () => Assert.False(Request.SetRemoveAsync("key", "member").Converter(0L)),

            () => Assert.Equal(2L, Request.SetAddAsync("key", ["member1", "member2"]).Converter(2L)),
            () => Assert.Equal(1L, Request.SetRemoveAsync("key", ["member1", "member2"]).Converter(1L)),
            () => Assert.Equal(5L, Request.SetLengthAsync("key").Converter(5L)),
            () => Assert.Equal(3L, Request.SetIntersectionLengthAsync(["key1", "key2"]).Converter(3L)),
            () => Assert.Equal(4L, Request.SetUnionStoreAsync("dest", ["key1", "key2"]).Converter(4L)),
            () => Assert.Equal(2L, Request.SetIntersectStoreAsync("dest", ["key1", "key2"]).Converter(2L)),
            () => Assert.Equal(1L, Request.SetDifferenceStoreAsync("dest", ["key1", "key2"]).Converter(1L)),

            () => Assert.Equal<GlideString>("member", Request.SetPopAsync("key").Converter("member")),
            () => Assert.Null(Request.SetPopAsync("key").Converter(null)),

            // Hash Commands
            () => Assert.Equal<GlideString>("value", Request.HashGetAsync("key", "field").Converter("value")),
            () => Assert.Equal(ValkeyValue.Null, Request.HashGetAsync("key", "field").Converter(null)),
            () => Assert.Equal(1L, Request.HashSetAsync("key", new HashEntry[] { new HashEntry("field", "value") }).Converter(1L)),
            () => Assert.True(Request.HashDeleteAsync("key", "field").Converter(1L)),
            () => Assert.False(Request.HashDeleteAsync("key", "field").Converter(0L)),
            () => Assert.Equal(2L, Request.HashDeleteAsync("key", ["field1", "field2"]).Converter(2L)),
            () => Assert.True(Request.HashExistsAsync("key", "field").Converter(1L)),
            () => Assert.False(Request.HashExistsAsync("key", "field").Converter(0L)),
            () => Assert.Equal(5L, Request.HashLengthAsync("key").Converter(5L)),
            () => Assert.Equal(10L, Request.HashStringLengthAsync("key", "field").Converter(10L))
        );

    }

    [Fact]
    public void ValidateSetCommandHashSetConverters()
    {
        HashSet<object> testHashSet = new HashSet<object> {
            (gs)"member1",
            (gs)"member2",
            (gs)"member3"
        };

        Assert.Multiple(
            () => {
                var result = Request.SetMembersAsync("key").Converter(testHashSet);
                Assert.Equal(3, result.Length);
                foreach (var item in result) Assert.IsType<ValkeyValue>(item);
            },

            () => {
                var result = Request.SetPopAsync("key", 2).Converter(testHashSet);
                Assert.Equal(3, result.Length);
                foreach (var item in result) Assert.IsType<ValkeyValue>(item);
            },

            () => {
                var result = Request.SetUnionAsync(["key1", "key2"]).Converter(testHashSet);
                Assert.Equal(3, result.Length);
                foreach (var item in result) Assert.IsType<ValkeyValue>(item);
            },

            () => {
                var result = Request.SetIntersectAsync(["key1", "key2"]).Converter(testHashSet);
                Assert.Equal(3, result.Length);
                foreach (var item in result) Assert.IsType<ValkeyValue>(item);
            },

            () => {
                var result = Request.SetDifferenceAsync(["key1", "key2"]).Converter(testHashSet);
                Assert.Equal(3, result.Length);
                foreach (var item in result) Assert.IsType<ValkeyValue>(item);
            }
        );
    }

    [Fact]
    public void ValidateHashCommandConverters()
    {
        // Test for HashGetAsync with multiple fields
        List<object> testList = new List<object> {
            (gs)"value1",
            (gs)"value2",
            null
        };

        // Test for HashGetAllAsync and HashRandomFieldsWithValuesAsync
        Dictionary<GlideString, GlideString> testKvpList = new Dictionary<GlideString, GlideString> {
            {"field1", "value1" },
            {"field2", "value2" },
            {"field3", "value3" },
        };

        // Test for HashValuesAsync and HashRandomFieldsAsync
        HashSet<object> testHashSet = new HashSet<object> {
            (gs)"value1",
            (gs)"value2",
            (gs)"value3"
        };

        Assert.Multiple(
            // Test HashGetAsync with multiple fields
            () => {
                var result = Request.HashGetAsync("key", new ValkeyValue[] {"field1", "field2", "field3"}).Converter(testList.ToArray());
                Assert.Equal(3, result.Length);
                Assert.Equal("value1", result[0]);
                Assert.Equal("value2", result[1]);
                Assert.Equal(ValkeyValue.Null, result[2]);
            },

            // Test HashGetAllAsync
            () => {
                var result = Request.HashGetAllAsync("key").Converter(testKvpList);
                Assert.Equal(3, result.Length);
                foreach (var entry in result) {
                    Assert.IsType<HashEntry>(entry);
                    Assert.IsType<ValkeyValue>(entry.Name);
                    Assert.IsType<ValkeyValue>(entry.Value);
                }
                Assert.Equal("field1", result[0].Name);
                Assert.Equal("value1", result[0].Value);
            },

            // Test HashValuesAsync
            () => {
                var result = Request.HashValuesAsync("key").Converter(testHashSet);
                Assert.Equal(3, result.Length);
                foreach (var item in result) Assert.IsType<ValkeyValue>(item);
            },

            // Test HashRandomFieldAsync
            () => {
                var result = Request.HashRandomFieldAsync("key").Converter("field1");
                Assert.Equal("field1", result);
            },

            // Test HashRandomFieldsAsync
            () => {
                var result = Request.HashRandomFieldsAsync("key", 3).Converter(testHashSet);
                Assert.Equal(3, result.Length);
                foreach (var item in result) Assert.IsType<ValkeyValue>(item);
            },

            // Test HashRandomFieldsWithValuesAsync
            () => {
                var result = Request.HashRandomFieldsWithValuesAsync("key", 3).Converter(testKvpList);
                Assert.Equal(3, result.Length);
                foreach (var entry in result) {
                    Assert.IsType<HashEntry>(entry);
                    Assert.IsType<ValkeyValue>(entry.Name);
                    Assert.IsType<ValkeyValue>(entry.Value);
                }
            }
        );
    }
}
