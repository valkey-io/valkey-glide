// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using static Valkey.Glide.ConnectionConfiguration;
using static Valkey.Glide.Errors;

using TimeoutException = Valkey.Glide.Errors.TimeoutException;

namespace Valkey.Glide.IntegrationTests;

[Collection(typeof(ErrorHandlingTests))]
[CollectionDefinition(DisableParallelization = true)]
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
        _ = await Assert.ThrowsAsync<TimeoutException>(async () =>
            _ = await client.CustomCommand(["debug", "sleep", "0.5"])
        );
        client.Dispose();
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
