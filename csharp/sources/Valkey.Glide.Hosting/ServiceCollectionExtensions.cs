using JetBrains.Annotations;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Valkey.Glide.InterOp;
using Valkey.Glide.InterOp.Native;

namespace Valkey.Glide.Hosting;

/// <summary>
/// Provides extension methods for configuring and registering Valkey Glide services
/// in an <see cref="IServiceCollection"/>.
/// </summary>
[PublicAPI]
public static class ServiceCollectionExtensions
{
    /// <summary>
    /// Configures and registers the necessary services for Valkey Glide within the service collection using a connection string.
    /// </summary>
    /// <param name="services">The service collection to which the Valkey Glide services will be added.</param>
    /// <param name="resourceName">The name of the resource whose connection string is used to configure the connection.</param>
    /// <param name="nativeLoggerLevel">The log-level of the native component</param>
    /// <param name="nativeLogFilePath">The file-path to log to for the native component</param>
    /// <returns>The modified service collection with Valkey Glide services registered.</returns>
    /// <exception cref="InvalidOperationException">
    /// Thrown if the connection string for the specified resource name is not found or if the connection string is invalid.
    /// </exception>
    public static IServiceCollection AddValkeyGlide(
        this IServiceCollection services,
        string resourceName,
        ELoggerLevel nativeLoggerLevel = ELoggerLevel.Off,
        string? nativeLogFilePath = null
    )
    {
        NativeClient.Initialize(nativeLoggerLevel, nativeLogFilePath);
        services.AddSingleton<InterOp.ConnectionRequest>(
            serviceProvider =>
            {
                var configuration = serviceProvider.GetRequiredService<IConfiguration>();

                // Read ValKey connection string
                var connectionString = configuration[string.Concat("ConnectionStrings:", resourceName)];
                if (string.IsNullOrWhiteSpace(connectionString))
                    throw new InvalidOperationException(
                        $"Connection string '{resourceName}' not found in IConfiguration[\"ConnectionStrings:{resourceName}\"]."
                    );
                return ConnectionStringParser.Parse(connectionString);
            }
        );

        services.AddScoped<IGlideClient, GlideClient>(
            serviceProvider => new GlideClient(serviceProvider.GetRequiredService<InterOp.ConnectionRequest>())
        );
        return services;
    }


    /// <summary>
    /// Configures and registers the Valkey Glide services within the service collection using a custom configuration.
    /// </summary>
    /// <param name="services">The service collection where the Valkey Glide services will be added.</param>
    /// <param name="configure">An action to configure the connection settings through the <see cref="ConnectionConfigBuilder"/>.</param>
    /// <param name="nativeLoggerLevel">The log-level of the native component</param>
    /// <param name="nativeLogFilePath">The file-path to log to for the native component</param>
    /// <returns>The modified service collection with Valkey Glide services registered.</returns>
    /// <exception cref="ArgumentNullException">
    /// Thrown if the <paramref name="configure"/> parameter is null.
    /// </exception>
    public static IServiceCollection AddValkeyGlide(
        this IServiceCollection services,
        Action<ConnectionConfigBuilder> configure,
        ELoggerLevel nativeLoggerLevel = ELoggerLevel.Off,
        string? nativeLogFilePath = null
    )
    {
        NativeClient.Initialize(nativeLoggerLevel, nativeLogFilePath);
        var builder = new ConnectionConfigBuilder();
        configure(builder);
        var config = builder.Build();
        return AddValkeyGlide(services, config);
    }

    /// <summary>
    /// Registers and configures the Valkey Glide services using the specified connection request.
    /// </summary>
    /// <param name="services">The service collection to which the Valkey Glide services will be added.</param>
    /// <param name="connectionRequest">The connection request containing configuration details for establishing the connection.</param>
    /// <param name="nativeLoggerLevel">The log-level of the native component</param>
    /// <param name="nativeLogFilePath">The file-path to log to for the native component</param>
    /// <returns>The modified service collection with Valkey Glide services registered.</returns>
    /// <exception cref="ArgumentNullException">
    /// Thrown if the <paramref name="services"/> or <paramref name="connectionRequest"/> is null.
    /// </exception>
    public static IServiceCollection AddValkeyGlide(
        this IServiceCollection services,
        InterOp.ConnectionRequest connectionRequest,
        ELoggerLevel nativeLoggerLevel = ELoggerLevel.Off,
        string? nativeLogFilePath = null
    )
    {
        NativeClient.Initialize(nativeLoggerLevel, nativeLogFilePath);
        services.AddSingleton(connectionRequest);
        services.AddScoped<IGlideClient, GlideClient>(
            serviceProvider => new GlideClient(serviceProvider.GetRequiredService<InterOp.ConnectionRequest>())
        );
        return services;
    }
}
