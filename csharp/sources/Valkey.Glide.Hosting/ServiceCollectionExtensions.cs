using JetBrains.Annotations;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Valkey.Glide.InterOp;
using Valkey.Glide.InterOp.Native;
using ConnectionRequest = Valkey.Glide.InterOp.ConnectionRequest;

namespace Valkey.Glide.Hosting;

/// <summary>
/// Provides extension methods for configuring and registering Valkey Glide services
/// in an <see cref="IServiceCollection"/>.
/// </summary>
[PublicAPI]
public static class ServiceCollectionExtensions
{
    /// <summary>
    /// Configures and registers custom <see cref="IGlideSerializer{T}"/>'s for Valkey Glide within the service collection.
    /// </summary>
    /// <remarks>
    /// <list type="bullet">
    /// <item>
    /// This is safe to be called multiple times and by library authors.
    /// Each registered configuration action will be applied
    /// sequentially.
    /// </item>
    /// </list>
    /// </remarks>
    /// <param name="services">The service collection to which the custom Glide transformers will be added.</param>
    /// <param name="configure">An action to configure the GlideSerializerCollectionBuilder, allowing the registration of custom transformers.</param>
    /// <returns>The modified service collection with the custom Glide transformers registered.</returns>
    public static IServiceCollection ConfigureValkeyGlideTransformers(this IServiceCollection services,
        Action<GlideSerializerCollectionBuilder> configure)
    {
        services.AddTransient<GlideSerializerCollectionBuilder>(_ =>
        {
            GlideSerializerCollectionBuilder builder = new();
            configure(builder);
            return builder;
        });
        return services;
    }

    /// <summary>
    /// Configures and registers the necessary services for Valkey Glide within the service collection using a connection string.
    /// </summary>
    /// <param name="services">The service collection to which the Valkey Glide services will be added.</param>
    /// <param name="resourceName">The name of the resource whose connection string is used to configure the connection.</param>
    /// <param name="loggingHarness">
    /// The logging harness implementation to use.
    /// If no logging harness is passed, the default <see cref="StdOutLoggingHarness"/> will be created.
    /// </param>
    /// <returns>The modified service collection with Valkey Glide services registered.</returns>
    /// <exception cref="InvalidOperationException">
    /// Thrown if the connection string for the specified resource name is not found or if the connection string is invalid.
    /// </exception>
    public static IServiceCollection AddValkeyGlide(
        this IServiceCollection services,
        string resourceName,
        NativeLoggingHarness? loggingHarness = null
    )
    {
        // Ensure at least one logging harness implementation exists.
        if (loggingHarness is null)
        {
            _ = new StdOutLoggingHarness();
        }
        services.AddSingleton<ConnectionRequest>(
            serviceProvider =>
            {
                IConfiguration configuration = serviceProvider.GetRequiredService<IConfiguration>();

                // Read ValKey connection string
                string? connectionString = configuration[string.Concat("ConnectionStrings:", resourceName)];
                if (string.IsNullOrWhiteSpace(connectionString))
                    throw new InvalidOperationException(
                        $"Connection string '{resourceName}' not found in IConfiguration[\"ConnectionStrings:{resourceName}\"]."
                    );
                return ConnectionStringParser.Parse(connectionString);
            }
        );

        AddGlideClient(services);
        return services;
    }


    /// <summary>
    /// Configures and registers the Valkey Glide services within the service collection using a custom configuration.
    /// </summary>
    /// <param name="services">The service collection where the Valkey Glide services will be added.</param>
    /// <param name="configure">An action to configure the connection settings through the <see cref="ConnectionConfigBuilder"/>.</param>
    /// <param name="loggingHarness">
    /// The logging harness implementation to use.
    /// If no logging harness is passed, the default <see cref="StdOutLoggingHarness"/> will be created.
    /// </param>
    /// <returns>The modified service collection with Valkey Glide services registered.</returns>
    /// <exception cref="ArgumentNullException">
    /// Thrown if the <paramref name="configure"/> parameter is null.
    /// </exception>
    public static IServiceCollection AddValkeyGlide(
        this IServiceCollection services,
        Action<ConnectionConfigBuilder> configure,
        NativeLoggingHarness? loggingHarness = null
    )
    {
        ConnectionConfigBuilder builder = new();
        configure(builder);
        ConnectionRequest config = builder.Build();
        return AddValkeyGlide(services, config, loggingHarness);
    }

    /// <summary>
    /// Registers and configures the Valkey Glide services using the specified connection request.
    /// </summary>
    /// <param name="services">The service collection to which the Valkey Glide services will be added.</param>
    /// <param name="connectionRequest">The connection request containing configuration details for establishing the connection.</param>
    /// <param name="loggingHarness">
    /// The logging harness implementation to use.
    /// If no logging harness is passed, the default <see cref="StdOutLoggingHarness"/> will be created.
    /// </param>
    /// <returns>The modified service collection with Valkey Glide services registered.</returns>
    /// <exception cref="ArgumentNullException">
    /// Thrown if the <paramref name="services"/> or <paramref name="connectionRequest"/> is null.
    /// </exception>
    public static IServiceCollection AddValkeyGlide(
        this IServiceCollection services,
        ConnectionRequest connectionRequest,
        NativeLoggingHarness? loggingHarness = null
    )
    {
        // Ensure at least one logging harness implementation exists.
        if (loggingHarness is null)
        {
            _ = new StdOutLoggingHarness();
        }
        services.AddSingleton(connectionRequest);
        AddGlideClient(services);
        return services;
    }

    private static void AddGlideClient(this IServiceCollection services)
        // ToDo: Settle on which lifetime would be the best for use
        => services.AddTransient<IGlideClient, GlideClient>(
            serviceProvider =>
            {
                IEnumerable<GlideSerializerCollectionBuilder> glideTransformerBuilders =
                    serviceProvider.GetServices<GlideSerializerCollectionBuilder>();
                GlideSerializerCollection glideSerializerCollection = new();
                glideSerializerCollection.RegisterDefaultSerializers();
                foreach (GlideSerializerCollectionBuilder glideTransformerBuilder in glideTransformerBuilders)
                {
                    foreach (Action<GlideSerializerCollection> action in glideTransformerBuilder.GetSerializers())
                    {
                        action(glideSerializerCollection);
                    }
                }
                glideSerializerCollection.Seal();
                ConnectionRequest connectionRequest = serviceProvider.GetRequiredService<ConnectionRequest>();
                return new GlideClient(connectionRequest, glideSerializerCollection);
            });
}
