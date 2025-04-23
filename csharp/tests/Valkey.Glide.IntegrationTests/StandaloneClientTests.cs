// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Pipeline;

using static Valkey.Glide.Pipeline.Options;

using gs = Valkey.Glide.GlideString;
namespace Valkey.Glide.IntegrationTests;

public class StandaloneClientTests
{
    [Fact]
    public void CustomCommand()
    {
        GlideClient client = TestConfiguration.DefaultStandaloneClient();
        // Assert.Multiple doesn't work with async tasks https://github.com/xunit/xunit/issues/3209
        Assert.Multiple(
            () => Assert.Equal("PONG", client.CustomCommand(["ping"]).Result!.ToString()),
            () => Assert.Equal("piping", client.CustomCommand(["ping", "piping"]).Result!.ToString()),
            () => Assert.Contains("# Server", client.CustomCommand(["INFO"]).Result!.ToString())
        );
    }

    [Fact]
    public async Task CustomCommandWithBinary()
    {
        GlideClient client = TestConfiguration.DefaultStandaloneClient();
        string key1 = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();
        string key3 = Guid.NewGuid().ToString();
        string value = Guid.NewGuid().ToString();
        Assert.Equal("OK", await client.Set(key1, value));

        gs dump = (await client.CustomCommand(["DUMP", key1]) as gs)!;

        Assert.Equal("OK".ToGlideString(), await client.CustomCommand(["RESTORE", key2, "0", dump!]));
        Assert.Equal(value, (await client.Get(key2))!);

        // Set and get a binary value
        Assert.Equal("OK", await client.Set(key3, dump!));
        Assert.Equal(dump, await client.Get(key3));
    }

    [Fact]
    public void CanConnectWithDifferentParameters()
    {
        _ = GlideClient.CreateClient(TestConfiguration.DefaultClientConfig()
            .WithClientName("GLIDE").Build());

        _ = GlideClient.CreateClient(TestConfiguration.DefaultClientConfig()
            .WithTls(false).Build());

        _ = GlideClient.CreateClient(TestConfiguration.DefaultClientConfig()
            .WithConnectionTimeout(2000).Build());

        _ = GlideClient.CreateClient(TestConfiguration.DefaultClientConfig()
            .WithRequestTimeout(2000).Build());

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

    [Fact]
    // Verify that client can handle complex return types, not just strings
    // TODO: remove this test once we add tests with these commands
    public async Task CustomCommandWithDifferentReturnTypes()
    {
        GlideClient client = TestConfiguration.DefaultStandaloneClient();

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
    public async Task Transaction()
    {
        GlideClient client = TestConfiguration.DefaultStandaloneClient();

        Batch transaction = new Batch(true).Set("abc", "pewpew").Get("abc").CustomCommand(["ping", "ping"]);
        var res = await client.Exec(transaction).WaitAsync(TimeSpan.FromSeconds(1));
        Assert.True(res.Length == 3);
        Assert.Equal(new object?[] { new gs("OK"), new gs("pewpew"), new gs("ping") }, res);

        transaction = new Batch(true).Get("abc").CustomCommand(["ping", "pong", "pang"]).CustomCommand(["llen", "abc"]);
        res = await client.Exec(transaction, new BatchOptions(raiseOnError: false));
        Assert.True(res.Length == 3);
    }

    [Theory]
    [InlineData(true)][InlineData(false)]
    public async Task BatchTimeout(bool isAtomic)
    {
        using GlideClient client = TestConfiguration.DefaultStandaloneClient();

        Batch batch = new Batch(isAtomic).CustomCommand(["DEBUG", "sleep", "0.5"]);
        BatchOptions options = new(timeout: 100);

        // Expect a timeout exception on short timeout
        _ = await Assert.ThrowsAsync<Exception>(() => client.Exec(batch, options));
        // TODO TimeoutException from #3411 https://github.com/valkey-io/valkey-glide/pull/3411

        // Retry with a longer timeout and expect [null]
        options = new(timeout: 1000);
        object[] res = (await client.Exec(batch, options))!;
        Assert.Equal([new gs("OK")], res); // TODO changed to "OK" in #3589 https://github.com/valkey-io/valkey-glide/pull/3589
    }

    [Theory]
    [InlineData(true)][InlineData(false)]
    public async Task BatchRaiseOnError(bool isAtomic)
    {
        using GlideClient client = TestConfiguration.DefaultStandaloneClient();
        string key1 = "{BatchRaiseOnError}-1-" + Guid.NewGuid();
        string key2 = "{BatchRaiseOnError}-2-" + Guid.NewGuid();

        Batch batch = new Batch(isAtomic).Set(key1, "hello").CustomCommand(["lpop", key1]).CustomCommand(["del", key1]).CustomCommand(["rename", key1, key2]);
        BatchOptions options = new(raiseOnError: false);

        object[] res = (await client.Exec(batch, options))!;
        // Exceptions aren't raised, but stored in the result set
        Assert.Multiple(
            () => Assert.Equal(4, res.Length),
            () => Assert.Equal(new gs("OK"), res[0]), // TODO changed to "OK" in #3589 https://github.com/valkey-io/valkey-glide/pull/3589
            () => Assert.Equal(1L, (long)res[2]),
            () => Assert.IsType<Exception>(res[1]), // TODO RequestException from #3411 https://github.com/valkey-io/valkey-glide/pull/3411
            () => Assert.IsType<Exception>(res[3]),
            () => Assert.Contains("wrong kind of value", ((Exception)res[1]).Message),
            () => Assert.Contains("no such key", ((Exception)res[3]).Message)
        );

        // First exception is raised, all data lost
        options = new(raiseOnError: true);
        Exception err = await Assert.ThrowsAsync<Exception>(async () =>
        {
            // TODO RequestException from #3411 https://github.com/valkey-io/valkey-glide/pull/3411
            await client.Exec(batch, options);
            GC.KeepAlive(client); // TODO?
        });
        Assert.Contains("wrong kind of value", err.Message);
        GC.KeepAlive(client);
    }
}
