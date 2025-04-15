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
        using GlideClusterClient client = TestConfiguration.DefaultClusterClient();
        TestContext.Current.TestOutputHelper?.WriteLine($"{client} {DateTime.Now:O}");
        _ = await Assert.ThrowsAsync<TimeoutException>(async () =>
            await client.CustomCommand(["debug", "sleep", "1"])
        );
        TestContext.Current.TestOutputHelper?.WriteLine($"{client} {DateTime.Now:O}");
        GC.KeepAlive(client);
    }

    [Fact]
    public async Task ErrorIfIncorrectArgs() =>
        await Assert.ThrowsAsync<RequestException>(async () =>
            await TestConfiguration.DefaultStandaloneClient().CustomCommand(["ping", "pong", "pang"])
        );
}
