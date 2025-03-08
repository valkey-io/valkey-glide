// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Tests.Integration;

public class StandaloneClientTests
{
    [Fact]
    public void CustomCommand()
    {
        GlideClient client = TestConfiguration.DefaultStandaloneClient();
        // Assert.Multiple doesn't work with async tasks https://github.com/xunit/xunit/issues/3209
        Assert.Multiple(
            () => Assert.Equal("PONG", client.CustomCommand(["ping"]).Result),
            () => Assert.Equal("piping", client.CustomCommand(["ping", "piping"]).Result),
            () => Assert.Contains("# Server", client.CustomCommand(["INFO"]).Result as string)
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

        Assert.Equal("OK", await client.Set(key3, dump!));
        Assert.Equal(dump, await client.Get(key3));
    }
}
