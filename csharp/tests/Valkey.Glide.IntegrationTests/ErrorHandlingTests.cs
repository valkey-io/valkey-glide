﻿// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

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
