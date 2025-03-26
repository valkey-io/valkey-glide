// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging;

namespace Valkey.Glide.Hosting;

/// <summary>
/// Provides extension methods for configuring the application <see cref="IHost"/>.
/// </summary>
public static class HostExtensions
{
    /// <summary>
    /// Enables Glide Core Logging to the dotnet logging framework.
    /// </summary>
    /// <param name="host">The <see cref="IHost"/> instance to use for creating an <see cref="ILogger"/>, which Glide Core Logging will use.</param>
    /// <returns>
    /// The same <see cref="IHost"/> instance passed in with <paramref name="host"/> to allow method chaining.
    /// </returns>
    public static IHost AddGlideCoreLogging(this IHost host)
    {
        var logger = host.Services.GetRequiredService<ILogger<GlideCoreLogger>>();
        _ = new GlideCoreLogger(logger);
        return host;
    }
}
