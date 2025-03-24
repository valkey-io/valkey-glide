using System.Runtime.CompilerServices;
using Valkey.Glide.Commands.Abstraction;
using Valkey.Glide.InterOp.Native;
using Valkey.Glide.InterOp.Routing;
using Value = Valkey.Glide.InterOp.Value;

namespace Valkey.Glide.Commands;

public static class GetCommand
{
    /// <summary>
    /// Creates a new <see cref="GetCommand"/> instance with the specified key.
    /// </summary>
    /// <param name="key">The key to associate with the command. It must not be null or empty.</param>
    /// <return>A new instance of <see cref="GetCommand"/> with the specified key.</return>
    public static GetCommand<NoRouting> Create(string key) => new GetCommand<NoRouting>{RoutingInfo = new NoRouting()}.WithKey(key);

    /// <summary>
    /// Creates a new <see cref="GetCommand"/> instance with the specified routing information and key.
    /// </summary>
    /// <param name="routingInfo">The routing information to associate with the command. It cannot be null.</param>
    /// <param name="key">The key to associate with the command. It must not be null or empty.</param>
    /// <return>A new instance of <see cref="GetCommand"/> with the specified routing information and key.</return>
    public static GetCommand<TRoutingInfo> Create<TRoutingInfo>(TRoutingInfo routingInfo, string key)
        where TRoutingInfo : IRoutingInfo => new GetCommand<TRoutingInfo>{RoutingInfo = routingInfo}.WithKey(key);
}

/// <summary>
/// Represents a command for retrieving a key-value pair from a Glide client.
/// </summary>
/// <remarks>
/// This command retrieves the value associated with a specific key stored in the database.
/// It validates that the key is neither null nor empty before execution and throws
/// an appropriate exception if validation fails.
/// </remarks>
public readonly record struct GetCommand<TRoutingInfo>()
    : IGlideCommand where TRoutingInfo : IRoutingInfo
{
    private string Key { get; init; } = string.Empty;
    public required TRoutingInfo RoutingInfo { get; init; }

    [MethodImpl(MethodImplOptions.AggressiveInlining | MethodImplOptions.AggressiveOptimization)]
    public GetCommand<TRoutingInfo> WithKey(string key)
        => this with {Key = key};

    /// <inheritdoc />
    [MethodImpl(MethodImplOptions.AggressiveInlining | MethodImplOptions.AggressiveOptimization)]
    public Task<Value> ExecuteAsync(IGlideClient client, CancellationToken cancellationToken = default)
    {
        if (string.IsNullOrWhiteSpace(Key))
            throw new InvalidOperationException(Properties.Language.GetCommand_KeyNotSet);
        return client.CommandAsync(
            ERequestType.Get, RoutingInfo, Key.AsRedisCommandText());
    }
}
