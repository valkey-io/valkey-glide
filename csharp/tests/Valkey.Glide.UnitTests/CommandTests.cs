﻿// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Commands.Options;
using Valkey.Glide.Internals;

namespace Valkey.Glide.UnitTests;

public class CommandTests
{
    [Fact]
    public void ValidateCommandArgs()
    {
        Assert.Multiple(
            () => Assert.Equal(["get", "a"], Request.CustomCommand(["get", "a"]).GetArgs()),
            () => Assert.Equal(["ping", "pong", "pang"], Request.CustomCommand(["ping", "pong", "pang"]).GetArgs()),
            () => Assert.Equal(["get"], Request.CustomCommand(["get"]).GetArgs()),
            () => Assert.Equal([], Request.CustomCommand([]).GetArgs()),

            // String Commands
            () => Assert.Equal(["SET", "key", "value"], Request.StringSet("key", "value").GetArgs()),
            () => Assert.Equal(["GET", "key"], Request.StringGet("key").GetArgs()),
            () => Assert.Equal(["MGET", "key1", "key2", "key3"], Request.StringGetMultiple(["key1", "key2", "key3"]).GetArgs()),
            () => Assert.Equal(["MSET", "key1", "value1", "key2", "value2"], Request.StringSetMultiple([
                new KeyValuePair<ValkeyKey, ValkeyValue>("key1", "value1"),
                new KeyValuePair<ValkeyKey, ValkeyValue>("key2", "value2")
            ]).GetArgs()),
            () => Assert.Equal(["STRLEN", "key"], Request.StringLength("key").GetArgs()),
            () => Assert.Equal(["GETRANGE", "key", "0", "5"], Request.StringGetRange("key", 0, 5).GetArgs()),
            () => Assert.Equal(["SETRANGE", "key", "10", "value"], Request.StringSetRange("key", 10, "value").GetArgs()),

            () => Assert.Equal(["INFO"], Request.Info([]).GetArgs()),
            () => Assert.Equal(["INFO", "CLIENTS", "CPU"], Request.Info([InfoOptions.Section.CLIENTS, InfoOptions.Section.CPU]).GetArgs()),

            // Connection Management Commands
            () => Assert.Equal(["PING"], Request.Ping().GetArgs()),
            () => Assert.Equal(["PING", "Hello"], Request.Ping("Hello").GetArgs()),
            () => Assert.Equal(["ECHO", "message"], Request.Echo("message").GetArgs()),

            // Set Commands
            () => Assert.Equal(["SADD", "key", "member"], Request.SetAddAsync("key", "member").GetArgs()),
            () => Assert.Equal(["SADD", "key", "member1", "member2"], Request.SetAddAsync("key", ["member1", "member2"]).GetArgs()),
            () => Assert.Equal(["SREM", "key", "member"], Request.SetRemoveAsync("key", "member").GetArgs()),
            () => Assert.Equal(["SREM", "key", "member1", "member2"], Request.SetRemoveAsync("key", ["member1", "member2"]).GetArgs()),
            () => Assert.Equal(["SMEMBERS", "key"], Request.SetMembersAsync("key").GetArgs()),
            () => Assert.Equal(["SCARD", "key"], Request.SetLengthAsync("key").GetArgs()),
            () => Assert.Equal(["SINTERCARD", "2", "key1", "key2"], Request.SetIntersectionLengthAsync(["key1", "key2"]).GetArgs()),
            () => Assert.Equal(["SINTERCARD", "2", "key1", "key2", "LIMIT", "10"], Request.SetIntersectionLengthAsync(["key1", "key2"], 10).GetArgs()),
            () => Assert.Equal(["SPOP", "key"], Request.SetPopAsync("key").GetArgs()),
            () => Assert.Equal(["SPOP", "key", "3"], Request.SetPopAsync("key", 3).GetArgs()),
            () => Assert.Equal(["SUNION", "key1", "key2"], Request.SetUnionAsync(["key1", "key2"]).GetArgs()),
            () => Assert.Equal(["SINTER", "key1", "key2"], Request.SetIntersectAsync(["key1", "key2"]).GetArgs()),
            () => Assert.Equal(["SDIFF", "key1", "key2"], Request.SetDifferenceAsync(["key1", "key2"]).GetArgs()),
            () => Assert.Equal(["SUNIONSTORE", "dest", "key1", "key2"], Request.SetUnionStoreAsync("dest", ["key1", "key2"]).GetArgs()),
            () => Assert.Equal(["SINTERSTORE", "dest", "key1", "key2"], Request.SetIntersectStoreAsync("dest", ["key1", "key2"]).GetArgs()),
            () => Assert.Equal(["SDIFFSTORE", "dest", "key1", "key2"], Request.SetDifferenceStoreAsync("dest", ["key1", "key2"]).GetArgs()),

            // Generic Commands
            () => Assert.Equal(["DEL", "key"], Request.KeyDeleteAsync("key").GetArgs()),
            () => Assert.Equal(["DEL", "key1", "key2"], Request.KeyDeleteAsync(["key1", "key2"]).GetArgs()),
            () => Assert.Equal(["UNLINK", "key"], Request.KeyUnlinkAsync("key").GetArgs()),
            () => Assert.Equal(["UNLINK", "key1", "key2"], Request.KeyUnlinkAsync(["key1", "key2"]).GetArgs()),
            () => Assert.Equal(["EXISTS", "key"], Request.KeyExistsAsync("key").GetArgs()),
            () => Assert.Equal(["EXISTS", "key1", "key2"], Request.KeyExistsAsync(["key1", "key2"]).GetArgs()),
            () => Assert.Equal(["EXPIRE", "key", "60"], Request.KeyExpireAsync("key", TimeSpan.FromSeconds(60)).GetArgs()),
            () => Assert.Equal(["EXPIRE", "key", "60", "NX"], Request.KeyExpireAsync("key", TimeSpan.FromSeconds(60), ExpireWhen.HasNoExpiry).GetArgs()),
            () => Assert.Equal(["EXPIREAT", "key", "1609459200"], Request.KeyExpireAsync("key", new DateTime(2021, 1, 1, 0, 0, 0, DateTimeKind.Utc)).GetArgs()),
            () => Assert.Equal(["TTL", "key"], Request.KeyTimeToLiveAsync("key").GetArgs()),
            () => Assert.Equal(["TYPE", "key"], Request.KeyTypeAsync("key").GetArgs()),
            () => Assert.Equal(["RENAME", "oldkey", "newkey"], Request.KeyRenameAsync("oldkey", "newkey").GetArgs()),
            () => Assert.Equal(["RENAMENX", "oldkey", "newkey"], Request.KeyRenameNXAsync("oldkey", "newkey").GetArgs()),
            () => Assert.Equal(["PERSIST", "key"], Request.KeyPersistAsync("key").GetArgs()),
            () => Assert.Equal(["DUMP", "key"], Request.KeyDumpAsync("key").GetArgs()),
            () => Assert.Equal(["RESTORE", "key", "0", "data"], Request.KeyRestoreAsync("key", "data"u8.ToArray()).GetArgs()),
            () => Assert.Equal(["RESTORE", "key", "0", "data", "ABSTTL"], Request.KeyRestoreDateTimeAsync("key", "data"u8.ToArray()).GetArgs()),
            () => Assert.Equal(["RESTORE", "key", "5000", "data"], Request.KeyRestoreAsync("key", "data"u8.ToArray(), TimeSpan.FromSeconds(5)).GetArgs()),
            () => Assert.Equal(["RESTORE", "key", "2303596800000", "data", "ABSTTL"], Request.KeyRestoreDateTimeAsync("key", "data"u8.ToArray(), new DateTime(2042, 12, 31, 0, 0, 0, DateTimeKind.Utc)).GetArgs()),
            () => Assert.Equal(["RESTORE", "key", "0", "data", "REPLACE"], Request.KeyRestoreAsync("key", "data"u8.ToArray(), restoreOptions: new RestoreOptions().Replace()).GetArgs()),
            () => Assert.Equal(["RESTORE", "key", "0", "data", "IDLETIME", "1000"], Request.KeyRestoreAsync("key", "data"u8.ToArray(), restoreOptions: new RestoreOptions().SetIdletime(1000)).GetArgs()),
            () => Assert.Equal(["RESTORE", "key", "0", "data", "FREQ", "5"], Request.KeyRestoreAsync("key", "data"u8.ToArray(), restoreOptions: new RestoreOptions().SetFrequency(5)).GetArgs()),
            () => Assert.Equal(["RESTORE", "key", "0", "data", "REPLACE", "IDLETIME", "2000"], Request.KeyRestoreAsync("key", "data"u8.ToArray(), restoreOptions: new RestoreOptions().Replace().SetIdletime(2000)).GetArgs()),
            () => Assert.Equal(["RESTORE", "key", "0", "data", "REPLACE", "FREQ", "10"], Request.KeyRestoreAsync("key", "data"u8.ToArray(), restoreOptions: new RestoreOptions().Replace().SetFrequency(10)).GetArgs()),
            () => Assert.Equal(["RESTORE", "key", "0", "data", "ABSTTL"], Request.KeyRestoreDateTimeAsync("key", "data"u8.ToArray(), restoreOptions: new RestoreOptions()).GetArgs()),
            () => Assert.Equal(["RESTORE", "key", "0", "data", "ABSTTL", "IDLETIME", "2000"], Request.KeyRestoreDateTimeAsync("key", "data"u8.ToArray(), restoreOptions: new RestoreOptions().SetIdletime(2000)).GetArgs()),
            () => Assert.Equal(["RESTORE", "key", "0", "data", "ABSTTL", "FREQ", "10"], Request.KeyRestoreDateTimeAsync("key", "data"u8.ToArray(), restoreOptions: new RestoreOptions().SetFrequency(10)).GetArgs()),
            () => Assert.Equal(["RESTORE", "key", "0", "data", "ABSTTL", "REPLACE", "IDLETIME", "3000"], Request.KeyRestoreDateTimeAsync("key", "data"u8.ToArray(), restoreOptions: new RestoreOptions().Replace().SetIdletime(3000)).GetArgs()),
            () => Assert.Equal(["RESTORE", "key", "0", "data", "ABSTTL", "REPLACE", "FREQ", "20"], Request.KeyRestoreDateTimeAsync("key", "data"u8.ToArray(), restoreOptions: new RestoreOptions().Replace().SetFrequency(20)).GetArgs()),
            () => Assert.Equal(["RESTORE", "key", "2303596800000", "data", "ABSTTL", "REPLACE"], Request.KeyRestoreDateTimeAsync("key", "data"u8.ToArray(), new DateTime(2042, 12, 31, 0, 0, 0, DateTimeKind.Utc), new RestoreOptions().Replace()).GetArgs()),
            () => Assert.Throws<ArgumentException>(() => Request.KeyRestoreAsync("key", "data"u8.ToArray(), restoreOptions: new RestoreOptions().SetIdletime(1000).SetFrequency(5)).GetArgs()),
            () => Assert.Equal(["TOUCH", "key"], Request.KeyTouchAsync("key").GetArgs()),
            () => Assert.Equal(["TOUCH", "key1", "key2"], Request.KeyTouchAsync(["key1", "key2"]).GetArgs()),
            () => Assert.Equal(["COPY", "src", "dest"], Request.KeyCopyAsync("src", "dest").GetArgs()),
            () => Assert.Equal(["COPY", "src", "dest", "DB", "1", "REPLACE"], Request.KeyCopyAsync("src", "dest", 1, true).GetArgs()),
            () => Assert.Equal(["MOVE", "key", "1"], Request.KeyMoveAsync("key", 1).GetArgs()),

            // Sorted Set Commands
            () => Assert.Equal(["ZADD", "key", "10.5", "member"], Request.SortedSetAddAsync("key", "member", 10.5).GetArgs()),
            () => Assert.Equal(["ZADD", "key", "NX", "10.5", "member"], Request.SortedSetAddAsync("key", "member", 10.5, SortedSetWhen.NotExists).GetArgs()),
            () => Assert.Equal(["ZADD", "key", "XX", "10.5", "member"], Request.SortedSetAddAsync("key", "member", 10.5, SortedSetWhen.Exists).GetArgs()),
            () => Assert.Equal(["ZADD", "key", "GT", "10.5", "member"], Request.SortedSetAddAsync("key", "member", 10.5, SortedSetWhen.GreaterThan).GetArgs()),
            () => Assert.Equal(["ZADD", "key", "LT", "10.5", "member"], Request.SortedSetAddAsync("key", "member", 10.5, SortedSetWhen.LessThan).GetArgs()),
            () => Assert.Equal(["ZADD", "key", "10.5", "member1", "8.25", "member2"], Request.SortedSetAddAsync("key", [new SortedSetEntry("member1", 10.5), new SortedSetEntry("member2", 8.25)]).GetArgs()),
            () => Assert.Equal(["ZADD", "key", "NX", "10.5", "member1", "8.25", "member2"], Request.SortedSetAddAsync("key", [new SortedSetEntry("member1", 10.5), new SortedSetEntry("member2", 8.25)], SortedSetWhen.NotExists).GetArgs()),

            // List Commands
            () => Assert.Equal(["LPOP", "a"], Request.ListLeftPopAsync("a").GetArgs()),
            () => Assert.Equal(["LPOP", "a", "3"], Request.ListLeftPopAsync("a", 3).GetArgs()),
            () => Assert.Equal(["LPUSH", "a", "one", "two"], Request.ListLeftPushAsync("a", ["one", "two"]).GetArgs())
        );
    }

    [Fact]
    public void ValidateCommandConverters()
    {
        Assert.Multiple(
            () => Assert.Equal(1, Request.CustomCommand([]).Converter(1)),
            () => Assert.Equal(.1, Request.CustomCommand([]).Converter(.1)),
            () => Assert.Null(Request.CustomCommand([]).Converter(null)),

            // String Commands
            () => Assert.True(Request.StringSet("key", "value").Converter("OK")),
            () => Assert.Equal<GlideString>("value", Request.StringGet("key").Converter("value")),
            () => Assert.Null(Request.StringGet("key").Converter(null)),
            () => Assert.Equal(5L, Request.StringLength("key").Converter(5L)),
            () => Assert.Equal(0L, Request.StringLength("key").Converter(0L)),
            () => Assert.Equal<GlideString>("hello", Request.StringGetRange("key", 0, 4).Converter("hello")),
            () => Assert.Equal<GlideString>("", Request.StringGetRange("key", 0, 4).Converter("")),
            () => Assert.Equal(10L, Request.StringSetRange("key", 5, "world").Converter(10L)),
            () => Assert.True(Request.StringSetMultiple([
                new KeyValuePair<ValkeyKey, ValkeyValue>("key1", "value1"),
                new KeyValuePair<ValkeyKey, ValkeyValue>("key2", "value2")
            ]).Converter("OK")),
            () => Assert.False(Request.StringSetMultiple([
                new KeyValuePair<ValkeyKey, ValkeyValue>("key1", "value1"),
                new KeyValuePair<ValkeyKey, ValkeyValue>("key2", "value2")
            ]).Converter("ERROR")),

            () => Assert.Equal("info", Request.Info([]).Converter("info")),

            () => Assert.IsType<TimeSpan>(Request.Ping().Converter("PONG")),
            () => Assert.IsType<TimeSpan>(Request.Ping("Hello").Converter("Hello")),
            () => Assert.Equal<ValkeyValue>("message", Request.Echo("message").Converter("message")),

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

            () => Assert.Equal<ValkeyValue>("member", Request.SetPopAsync("key").Converter("member")),
            () => Assert.Null(Request.SetPopAsync("key").Converter(null)),

            // Generic Commands Converters
            () => Assert.True(Request.KeyDeleteAsync("key").Converter(1L)),
            () => Assert.False(Request.KeyDeleteAsync("key").Converter(0L)),
            () => Assert.Equal(2L, Request.KeyDeleteAsync(["key1", "key2"]).Converter(2L)),
            () => Assert.True(Request.KeyUnlinkAsync("key").Converter(1L)),
            () => Assert.False(Request.KeyUnlinkAsync("key").Converter(0L)),
            () => Assert.Equal(3L, Request.KeyUnlinkAsync(["key1", "key2", "key3"]).Converter(3L)),
            () => Assert.True(Request.KeyExistsAsync("key").Converter(1L)),
            () => Assert.False(Request.KeyExistsAsync("key").Converter(0L)),
            () => Assert.Equal(2L, Request.KeyExistsAsync(["key1", "key2"]).Converter(2L)),
            () => Assert.True(Request.KeyExpireAsync("key", TimeSpan.FromSeconds(60)).Converter(true)),
            () => Assert.False(Request.KeyExpireAsync("key", TimeSpan.FromSeconds(60)).Converter(false)),
            () => Assert.Equal(TimeSpan.FromSeconds(30), Request.KeyTimeToLiveAsync("key").Converter(30L)),
            () => Assert.Null(Request.KeyTimeToLiveAsync("key").Converter(-1L)),
            () => Assert.Null(Request.KeyTimeToLiveAsync("key").Converter(-2L)),
            () => Assert.Equal(ValkeyType.String, Request.KeyTypeAsync("key").Converter("string")),
            () => Assert.Equal(ValkeyType.List, Request.KeyTypeAsync("key").Converter("list")),
            () => Assert.Equal(ValkeyType.Set, Request.KeyTypeAsync("key").Converter("set")),
            () => Assert.Equal(ValkeyType.SortedSet, Request.KeyTypeAsync("key").Converter("zset")),
            () => Assert.Equal(ValkeyType.Hash, Request.KeyTypeAsync("key").Converter("hash")),
            () => Assert.Equal(ValkeyType.Stream, Request.KeyTypeAsync("key").Converter("stream")),
            () => Assert.Equal(ValkeyType.None, Request.KeyTypeAsync("key").Converter("none")),
            () => Assert.True(Request.KeyRenameAsync("oldkey", "newkey").Converter("OK")),
            () => Assert.True(Request.KeyRenameNXAsync("oldkey", "newkey").Converter(true)),
            () => Assert.False(Request.KeyRenameNXAsync("oldkey", "newkey").Converter(false)),
            () => Assert.True(Request.KeyPersistAsync("key").Converter(true)),
            () => Assert.False(Request.KeyPersistAsync("key").Converter(false)),
            () => Assert.NotNull(Request.KeyDumpAsync("key").Converter("dumpdata")),
            () => Assert.Null(Request.KeyDumpAsync("key").Converter(null)),
            () => Assert.Equal("OK", Request.KeyRestoreAsync("key", new byte[0]).Converter("OK")),
            () => Assert.Equal("OK", Request.KeyRestoreDateTimeAsync("key", new byte[0]).Converter("OK")),
            () => Assert.True(Request.KeyTouchAsync("key").Converter(1L)),
            () => Assert.False(Request.KeyTouchAsync("key").Converter(0L)),
            () => Assert.Equal(2L, Request.KeyTouchAsync(["key1", "key2"]).Converter(2L)),
            () => Assert.True(Request.KeyCopyAsync("src", "dest").Converter(true)),
            () => Assert.False(Request.KeyCopyAsync("src", "dest").Converter(false)),
            () => Assert.True(Request.KeyMoveAsync("key", 1).Converter(true)),
            () => Assert.False(Request.KeyMoveAsync("key", 1).Converter(false)),

            () => Assert.Equal("one", Request.ListLeftPopAsync("a").Converter("one")),
            () => Assert.Equal(["one", "two"], Request.ListLeftPopAsync("a", 2).Converter([(gs)"one", (gs)"two"])),
            () => Assert.Equal(2L, Request.ListLeftPushAsync("a", ["one", "two"]).Converter(2L))
        );
    }

    [Fact]
    public void ValidateStringCommandArrayConverters()
    {
        Assert.Multiple(
            () =>
            {
                // Test MGET with GlideString objects (what the server actually returns)
                object[] mgetResponse = [new GlideString("value1"), null, new GlideString("value3")];
                var result = Request.StringGetMultiple(["key1", "key2", "key3"]).Converter(mgetResponse);
                Assert.Equal(3, result.Length);
                Assert.Equal(new ValkeyValue("value1"), result[0]);
                Assert.Equal(ValkeyValue.Null, result[1]);
                Assert.Equal(new ValkeyValue("value3"), result[2]);
            },

            () =>
            {
                // Test empty MGET response
                var emptyResult = Request.StringGetMultiple([]).Converter([]);
                Assert.Empty(emptyResult);
            },

            () =>
            {
                // Test MGET with all null values
                object[] allNullResponse = [null, null];
                var result = Request.StringGetMultiple(["key1", "key2"]).Converter(allNullResponse);
                Assert.Equal(2, result.Length);
                Assert.Equal(ValkeyValue.Null, result[0]);
                Assert.Equal(ValkeyValue.Null, result[1]);
            }
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

        Assert.Multiple([
            () => {
                ValkeyValue[] result = Request.SetMembersAsync("key").Converter(testHashSet);
                Assert.Equal(3, result.Length);
                Assert.All(result, item => Assert.IsType<ValkeyValue>(item));
            },

            () => {
                ValkeyValue[] result = Request.SetPopAsync("key", 2).Converter(testHashSet);
                Assert.Equal(3, result.Length);
                Assert.All(result, item => Assert.IsType<ValkeyValue>(item));
            },

            () => {
                ValkeyValue[] result = Request.SetUnionAsync(["key1", "key2"]).Converter(testHashSet);
                Assert.Equal(3, result.Length);
                Assert.All(result, item => Assert.IsType<ValkeyValue>(item));
            },

            () => {
                ValkeyValue[] result = Request.SetIntersectAsync(["key1", "key2"]).Converter(testHashSet);
                Assert.Equal(3, result.Length);
                Assert.All(result, item => Assert.IsType<ValkeyValue>(item));
            },

            () => {
                ValkeyValue[] result = Request.SetDifferenceAsync(["key1", "key2"]).Converter(testHashSet);
                Assert.Equal(3, result.Length);
                Assert.All(result, item => Assert.IsType<ValkeyValue>(item));
            },
        ]);
    }
}
