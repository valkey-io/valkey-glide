// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Tests.Integration;

public class StadaloneClientTests
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

    [Fact(Skip = "Binary strings are not supported yet")]
    public async Task CustomCommandWithBinary()
    {
        GlideClient client = TestConfiguration.DefaultStandaloneClient();
        string key1 = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();
        string value = Guid.NewGuid().ToString();
        Assert.Equal("OK", await client.Set(key1, value));
        // TODO avoid suppressing if possible
        // Suppressing because we're sure that `DUMP` returns a string.
        // Converting null literal or possible null value to non-nullable type.
        // Possible null reference assignment.
#pragma warning disable CS8600, CS8601
        string dump = await client.CustomCommand(["DUMP", key1]) as string;
        Assert.Equal("OK", await client.CustomCommand(["RESTORE", key2, "0", dump]));
#pragma warning restore CS8600, CS8601
        Assert.Equal(value, await client.Get(key2));
    }
}
