// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using static Valkey.Glide.ConnectionConfiguration;
using static Valkey.Glide.Errors;

using TimeoutException = Valkey.Glide.Errors.TimeoutException;

namespace Valkey.Glide.IntegrationTests;

public class ErrorHandlingTests
{
    [Fact]
    public async Task ErrorIfConnectionFailed() =>
        await Assert.ThrowsAsync<ConnectionException>(async () =>
            await GlideClient.CreateClient(new StandaloneClientConfigurationBuilder().WithAddress(null, 42).Build())
        );

    [Fact]
    public async Task ErrorIfTimedOut()
    {
        using GlideClient client = TestConfiguration.DefaultStandaloneClient();
        //TestContext.Current.TestOutputHelper?.WriteLine($"{client} {DateTime.Now:O}");
        //_ = await Assert.ThrowsAsync<TimeoutException>(() => client.CustomCommand(["debug", "sleep", "1"]));
        //*
        _ = await Assert.ThrowsAsync<TimeoutException>(async () =>
        {
            _ = await client.CustomCommand(["debug", "sleep", "1"]);
            //TestContext.Current.TestOutputHelper?.WriteLine($"CustomCommand = {res}");
            // TODO try KeepAlive here
            GC.KeepAlive(client);
        });
        //*/
        //TestContext.Current.TestOutputHelper?.WriteLine($"{client} {DateTime.Now:O}");
        //GC.KeepAlive(client);
    }

    [Fact]
    public async Task ErrorIfIncorrectArgs()
    {
        using GlideClient client = TestConfiguration.DefaultStandaloneClient();
        _ = await Assert.ThrowsAsync<RequestException>(()
            => client.CustomCommand(["ping", "pong", "pang"])
        );
    }
}
