// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.InterOp.Errors;

using TimeoutException = Valkey.Glide.InterOp.Errors.TimeoutException;

namespace Valkey.Glide.IntegrationTests;

public class ErrorHandlingTests
{
    [Fact]
    public async Task ErrorIfConnectionFailed() =>
        await Assert.ThrowsAsync<ConnectionException>(async () =>
        {
            using GlideClient glideClient = new GlideClient(new StandaloneClientConfigurationBuilder().WithAddress("localhost", 42).Build());
            glideClient.EnsureInitializedAsync();
        });

    [Fact(Skip = "Deactivated until #3395 merged")]
    public async Task ErrorIfTimedOut() =>
        await Assert.ThrowsAsync<TimeoutException>(async () =>
            await TestConfiguration.DefaultClusterClient().CustomCommand(["debug", "sleep", "1"])
        );

    [Fact]
    public async Task ErrorIfIncorrectArgs() =>
        await Assert.ThrowsAsync<RequestException>(async () =>
            await TestConfiguration.DefaultStandaloneClient().CustomCommand(["ping", "pong", "pang"])
        );
}
