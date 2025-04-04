// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.InterOp;

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

#pragma warning disable CS8600 // Converting null literal or possible null value to non-nullable type.
        GlideString dump = await client.CustomCommand(["DUMP", key1]) as GlideString;
#pragma warning restore CS8600 // Converting null literal or possible null value to non-nullable type.

        Assert.Equal("OK".ToGlideString(), await client.CustomCommand(["RESTORE", key2, "0", dump!]));
        Assert.Equal(value, (await client.Get(key2))!);

        // Set and get a binary value
        Assert.Equal("OK", await client.Set(key3, dump!));
        Assert.Equal(dump, await client.Get(key3));
    }

    [Fact]
    public void CanConnectWithDifferentParameters()
    {
        _ = new GlideClient(TestConfiguration.DefaultClientConfig()
            .WithClientName("GLIDE"));

        _ = new GlideClient(TestConfiguration.DefaultClientConfig()
            .WithTlsMode(ETlsMode.NoTls));

        _ = new GlideClient(TestConfiguration.DefaultClientConfig()
            .WithConnectionTimeout(TimeSpan.FromMilliseconds(2000)));

        _ = new GlideClient(TestConfiguration.DefaultClientConfig()
            .WithRequestTimeout(TimeSpan.FromMilliseconds(2000)));

        _ = new GlideClient(TestConfiguration.DefaultClientConfig()
            .WithDatabaseId(4));

        _ = new GlideClient(TestConfiguration.DefaultClientConfig()
            .WithConnectionRetryStrategy(1, 2, 3));

        _ = new GlideClient(TestConfiguration.DefaultClientConfig()
            .WithAuthentication("default", ""));

        _ = new GlideClient(TestConfiguration.DefaultClientConfig()
            .WithProtocol(EProtocolVersion.Resp2));

        _ = new GlideClient(TestConfiguration.DefaultClientConfig()
            .WithReplicationStrategy(ReplicationStrategy.Primary()));
    }
}
