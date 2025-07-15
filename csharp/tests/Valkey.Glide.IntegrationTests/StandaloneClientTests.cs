// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Pipeline;

using static Valkey.Glide.Commands.Options.InfoOptions;

namespace Valkey.Glide.IntegrationTests;

public class StandaloneClientTests(TestConfiguration config)
{
    public static TheoryData<bool> GetAtomic => [true, false];

    public TestConfiguration Config { get; } = config;

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestStandaloneClients), MemberType = typeof(TestConfiguration))]
    public void CustomCommand(GlideClient client) =>
        // Assert.Multiple doesn't work with async tasks https://github.com/xunit/xunit/issues/3209
        Assert.Multiple(
            () => Assert.Equal("PONG", client.CustomCommand(["ping"]).Result!.ToString()),
            () => Assert.Equal("piping", client.CustomCommand(["ping", "piping"]).Result!.ToString()),
            () => Assert.Contains("# Server", client.CustomCommand(["INFO"]).Result!.ToString())
        );

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestStandaloneClients), MemberType = typeof(TestConfiguration))]
    public async Task CustomCommandWithBinary(GlideClient client)
    {
        string key1 = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();
        string key3 = Guid.NewGuid().ToString();
        string value = Guid.NewGuid().ToString();
        Assert.True(await client.StringSetAsync(key1, value));

        gs dump = (await client.CustomCommand(["DUMP", key1]) as gs)!;

        Assert.Equal("OK", await client.CustomCommand(["RESTORE", key2, "0", dump!]));
        ValkeyValue retrievedValue = await client.StringGetAsync(key2);
        Assert.Equal(value, retrievedValue.ToString());

        // Set and get a binary value
        Assert.True(await client.StringSetAsync(key3, dump!));
        ValkeyValue binaryValue = await client.StringGetAsync(key3);
        Assert.Equal(dump, (GlideString)binaryValue);
    }

    [Fact]
    public void CanConnectWithDifferentParameters()
    {
        _ = GlideClient.CreateClient(TestConfiguration.DefaultClientConfig()
            .WithClientName("GLIDE").Build());

        _ = GlideClient.CreateClient(TestConfiguration.DefaultClientConfig()
            .WithTls(false).Build());

        _ = GlideClient.CreateClient(TestConfiguration.DefaultClientConfig()
            .WithConnectionTimeout(TimeSpan.FromSeconds(2)).Build());

        _ = GlideClient.CreateClient(TestConfiguration.DefaultClientConfig()
            .WithRequestTimeout(TimeSpan.FromSeconds(2)).Build());

        _ = GlideClient.CreateClient(TestConfiguration.DefaultClientConfig()
            .WithDataBaseId(4).Build());

        _ = GlideClient.CreateClient(TestConfiguration.DefaultClientConfig()
            .WithConnectionRetryStrategy(1, 2, 3).Build());

        _ = GlideClient.CreateClient(TestConfiguration.DefaultClientConfig()
            .WithAuthentication("default", "").Build());

        _ = GlideClient.CreateClient(TestConfiguration.DefaultClientConfig()
            .WithProtocolVersion(ConnectionConfiguration.Protocol.RESP2).Build());

        _ = GlideClient.CreateClient(TestConfiguration.DefaultClientConfig()
            .WithReadFrom(new ConnectionConfiguration.ReadFrom(ConnectionConfiguration.ReadFromStrategy.Primary)).Build());
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestStandaloneClients), MemberType = typeof(TestConfiguration))]
    // Verify that client can handle complex return types, not just strings
    // TODO: remove this test once we add tests with these commands
    public async Task CustomCommandWithDifferentReturnTypes(GlideClient client)
    {
        string key1 = Guid.NewGuid().ToString();
        Assert.Equal(2, (long)(await client.CustomCommand(["hset", key1, "f1", "v1", "f2", "v2"]))!);
        Assert.Equal(
            new Dictionary<gs, gs> { { "f1", "v1" }, { "f2", "v2" } },
            await client.CustomCommand(["hgetall", key1])
        );
        Assert.Equal(
            new gs?[] { "v1", "v2", null },
            await client.CustomCommand(["hmget", key1, "f1", "f2", "f3"])
        );

        string key2 = Guid.NewGuid().ToString();
        Assert.Equal(3, (long)(await client.CustomCommand(["sadd", key2, "a", "b", "c"]))!);
        Assert.Equal(
            [new gs("a"), new gs("b"), new gs("c")],
            (await client.CustomCommand(["smembers", key2]) as HashSet<object>)!
        );
        Assert.Equal(
            new bool[] { true, true, false },
            await client.CustomCommand(["smismember", key2, "a", "b", "d"])
        );

        string key3 = Guid.NewGuid().ToString();
        _ = await client.CustomCommand(["xadd", key3, "0-1", "str-1-id-1-field-1", "str-1-id-1-value-1", "str-1-id-1-field-2", "str-1-id-1-value-2"]);
        _ = await client.CustomCommand(["xadd", key3, "0-2", "str-1-id-2-field-1", "str-1-id-2-value-1", "str-1-id-2-field-2", "str-1-id-2-value-2"]);
        _ = Assert.IsType<Dictionary<gs, object?>>((await client.CustomCommand(["xread", "streams", key3, "stream", "0-1", "0-2"]))!);
        _ = Assert.IsType<Dictionary<gs, object?>>((await client.CustomCommand(["xinfo", "stream", key3, "full"]))!);
    }

    [Fact]
    public async Task Info()
    {
        GlideClient client = TestConfiguration.DefaultStandaloneClient();

        string info = await client.Info();
        Assert.Multiple([
            () => Assert.Contains("# Server", info),
            () => Assert.Contains("# Replication", info),
            () => Assert.DoesNotContain("# Latencystats", info),
        ]);

        info = await client.Info([Section.REPLICATION]);
        Assert.Multiple([
            () => Assert.DoesNotContain("# Server", info),
            () => Assert.Contains("# Replication", info),
            () => Assert.DoesNotContain("# Latencystats", info),
        ]);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestStandaloneClients), MemberType = typeof(TestConfiguration))]
    public async Task KeyCopy_Move(GlideClient client)
    {
        string key = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();

        await client.StringSetAsync(key, "val");
        Assert.True(await client.KeyCopyAsync(key, key2, 1));
        Assert.True(await client.KeyMoveAsync(key, 2));
    }

    [Theory]
    [InlineData(true)]
    [InlineData(false)]
    public async Task BatchKeyCopyAndKeyMove(bool isAtomic)
    {
        GlideClient client = TestConfiguration.DefaultStandaloneClient();
        string sourceKey = Guid.NewGuid().ToString();
        string destKey = Guid.NewGuid().ToString();
        string moveKey = Guid.NewGuid().ToString();
        string value = "test-value";

        IBatch batch = new Batch(isAtomic);

        // Set up keys
        _ = batch.StringSet(sourceKey, value);
        _ = batch.StringSet(moveKey, value);

        IBatchStandalone batch2 = new Batch(isAtomic);

        // Test KeyCopy with database parameter
        _ = batch2.KeyCopy(sourceKey, destKey, 1, false);

        // Test KeyMove
        _ = batch2.KeyMove(moveKey, 2);

        object?[] results = (await client.Exec((Batch)batch, false))!;
        object?[] results2 = (await client.Exec((Batch)batch2, false))!;

        Assert.Multiple(
            () => Assert.True((bool)results[0]!), // Set sourceKey
            () => Assert.True((bool)results[1]!), // Set moveKey
            () => Assert.True((bool)results2[0]!), // KeyCopy result
            () => Assert.True((bool)results2[1]!)  // KeyMove result
        );
    }
}
