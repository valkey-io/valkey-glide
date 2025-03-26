using Valkey.Glide.Commands.Abstraction;
using Valkey.Glide.InterOp.Native;
using Valkey.Glide.InterOp.Routing;
using Value = Valkey.Glide.InterOp.Value;

namespace Valkey.Glide.Commands;


/// <summary>
/// Provides factory methods for creating instances of the CustomCommand structures.
/// </summary>
/// <remarks>
/// This static class enables the creation of CustomCommand instances with varying numbers of generic arguments,
/// facilitating a fluent and type-safe approach to defining and executing commands within the Glide framework.
/// </remarks
public static partial class CustomCommand
{
    /// <summary>
    /// Creates a new instance of the <see cref="CustomCommand{T1}"/> structure with the specified argument.
    /// </summary>
    /// <typeparam name="T1">The type of the first argument for the command.</typeparam>
    /// <param name="Arg1">The value of the first argument to be passed to the command.</param>
    /// <returns>An instance of <see cref="CustomCommand{T1}"/> initialized with the provided argument.</returns>
    public static CustomCommand<TRoutingInfo, T1> Create<TRoutingInfo, T1>(TRoutingInfo routingInfo, T1 arg1)
         where TRoutingInfo : IRoutingInfo
         => new CustomCommand<TRoutingInfo, T1>{RoutingInfo = routingInfo}
                .WithArg1(arg1);
}

/// <summary>
/// Represents a customizable command that can accept a specified number
/// of generic arguments and implements the IGlideCommand interface.
/// </summary>
/// <typeparam name="T1">The type of the first argument for the command.</typeparam>
/// <remarks>
/// <list type="bullet">
/// <item>
/// Each instance of the CustomCommand struct is immutable, and the usage of `WithArg` methods ensures
/// a fluent API for adding argument values while preserving immutability. It integrates with the IGlideCommand
/// interface for executing tasks asynchronously within the Glide framework.
/// </item>
/// <item>All values are handled as "values". If you want to issue command text, use the <see cref="Data.CommandText"/> struct</item>
/// </list>
/// </remarks>
public readonly struct CustomCommand<TRoutingInfo, T1> : IGlideCommand where TRoutingInfo : IRoutingInfo
{
    public required TRoutingInfo RoutingInfo { get; init; }

    private T1? Arg1 { get; init; }
    private bool Arg1Set { get; init; }
    public CustomCommand<TRoutingInfo, T1> WithArg1(T1 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg1 = arg, Arg1Set = true };
    }

    Task<Value> IGlideCommand.ExecuteAsync(IGlideClient client, CancellationToken cancellationToken = default)
    {
        if (!Arg1Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 0));
        return client.SendCommandAsync(ERequestType.CustomCommand, RoutingInfo, client.ToParameter(Arg1));
    }
}

/// <summary>
/// Provides factory methods for creating instances of the CustomCommand structures.
/// </summary>
/// <remarks>
/// This static class enables the creation of CustomCommand instances with varying numbers of generic arguments,
/// facilitating a fluent and type-safe approach to defining and executing commands within the Glide framework.
/// </remarks
public static partial class CustomCommand
{
    /// <summary>
    /// Creates a new instance of the <see cref="CustomCommand{T1, T2}"/> structure with the specified argument.
    /// </summary>
    /// <typeparam name="T1">The type of the first argument for the command.</typeparam>
    /// <param name="Arg1">The value of the first argument to be passed to the command.</param>
    /// <typeparam name="T2">The type of the second argument for the command.</typeparam>
    /// <param name="Arg2">The value of the second argument to be passed to the command.</param>
    /// <returns>An instance of <see cref="CustomCommand{T1, T2}"/> initialized with the provided argument.</returns>
    public static CustomCommand<TRoutingInfo, T1, T2> Create<TRoutingInfo, T1, T2>(TRoutingInfo routingInfo, T1 arg1, T2 arg2)
         where TRoutingInfo : IRoutingInfo
         => new CustomCommand<TRoutingInfo, T1, T2>{RoutingInfo = routingInfo}
                .WithArg1(arg1)
                .WithArg2(arg2);
}

/// <summary>
/// Represents a customizable command that can accept a specified number
/// of generic arguments and implements the IGlideCommand interface.
/// </summary>
/// <typeparam name="T1">The type of the first argument for the command.</typeparam>
/// <typeparam name="T2">The type of the second argument for the command.</typeparam>
/// <remarks>
/// <list type="bullet">
/// <item>
/// Each instance of the CustomCommand struct is immutable, and the usage of `WithArg` methods ensures
/// a fluent API for adding argument values while preserving immutability. It integrates with the IGlideCommand
/// interface for executing tasks asynchronously within the Glide framework.
/// </item>
/// <item>All values are handled as "values". If you want to issue command text, use the <see cref="Data.CommandText"/> struct</item>
/// </list>
/// </remarks>
public readonly struct CustomCommand<TRoutingInfo, T1, T2> : IGlideCommand where TRoutingInfo : IRoutingInfo
{
    public required TRoutingInfo RoutingInfo { get; init; }

    private T1? Arg1 { get; init; }
    private bool Arg1Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2> WithArg1(T1 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg1 = arg, Arg1Set = true };
    }

    private T2? Arg2 { get; init; }
    private bool Arg2Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2> WithArg2(T2 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg2 = arg, Arg2Set = true };
    }

    Task<Value> IGlideCommand.ExecuteAsync(IGlideClient client, CancellationToken cancellationToken = default)
    {
        if (!Arg1Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 0));
        if (!Arg2Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 1));
        return client.SendCommandAsync(ERequestType.CustomCommand, RoutingInfo, client.ToParameter(Arg1), client.ToParameter(Arg2));
    }
}

/// <summary>
/// Provides factory methods for creating instances of the CustomCommand structures.
/// </summary>
/// <remarks>
/// This static class enables the creation of CustomCommand instances with varying numbers of generic arguments,
/// facilitating a fluent and type-safe approach to defining and executing commands within the Glide framework.
/// </remarks
public static partial class CustomCommand
{
    /// <summary>
    /// Creates a new instance of the <see cref="CustomCommand{T1, T2, T3}"/> structure with the specified argument.
    /// </summary>
    /// <typeparam name="T1">The type of the first argument for the command.</typeparam>
    /// <param name="Arg1">The value of the first argument to be passed to the command.</param>
    /// <typeparam name="T2">The type of the second argument for the command.</typeparam>
    /// <param name="Arg2">The value of the second argument to be passed to the command.</param>
    /// <typeparam name="T3">The type of the third argument for the command.</typeparam>
    /// <param name="Arg3">The value of the third argument to be passed to the command.</param>
    /// <returns>An instance of <see cref="CustomCommand{T1, T2, T3}"/> initialized with the provided argument.</returns>
    public static CustomCommand<TRoutingInfo, T1, T2, T3> Create<TRoutingInfo, T1, T2, T3>(TRoutingInfo routingInfo, T1 arg1, T2 arg2, T3 arg3)
         where TRoutingInfo : IRoutingInfo
         => new CustomCommand<TRoutingInfo, T1, T2, T3>{RoutingInfo = routingInfo}
                .WithArg1(arg1)
                .WithArg2(arg2)
                .WithArg3(arg3);
}

/// <summary>
/// Represents a customizable command that can accept a specified number
/// of generic arguments and implements the IGlideCommand interface.
/// </summary>
/// <typeparam name="T1">The type of the first argument for the command.</typeparam>
/// <typeparam name="T2">The type of the second argument for the command.</typeparam>
/// <typeparam name="T3">The type of the third argument for the command.</typeparam>
/// <remarks>
/// <list type="bullet">
/// <item>
/// Each instance of the CustomCommand struct is immutable, and the usage of `WithArg` methods ensures
/// a fluent API for adding argument values while preserving immutability. It integrates with the IGlideCommand
/// interface for executing tasks asynchronously within the Glide framework.
/// </item>
/// <item>All values are handled as "values". If you want to issue command text, use the <see cref="Data.CommandText"/> struct</item>
/// </list>
/// </remarks>
public readonly struct CustomCommand<TRoutingInfo, T1, T2, T3> : IGlideCommand where TRoutingInfo : IRoutingInfo
{
    public required TRoutingInfo RoutingInfo { get; init; }

    private T1? Arg1 { get; init; }
    private bool Arg1Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3> WithArg1(T1 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg1 = arg, Arg1Set = true };
    }

    private T2? Arg2 { get; init; }
    private bool Arg2Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3> WithArg2(T2 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg2 = arg, Arg2Set = true };
    }

    private T3? Arg3 { get; init; }
    private bool Arg3Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3> WithArg3(T3 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg3 = arg, Arg3Set = true };
    }

    Task<Value> IGlideCommand.ExecuteAsync(IGlideClient client, CancellationToken cancellationToken = default)
    {
        if (!Arg1Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 0));
        if (!Arg2Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 1));
        if (!Arg3Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 2));
        return client.SendCommandAsync(ERequestType.CustomCommand, RoutingInfo, client.ToParameter(Arg1), client.ToParameter(Arg2), client.ToParameter(Arg3));
    }
}

/// <summary>
/// Provides factory methods for creating instances of the CustomCommand structures.
/// </summary>
/// <remarks>
/// This static class enables the creation of CustomCommand instances with varying numbers of generic arguments,
/// facilitating a fluent and type-safe approach to defining and executing commands within the Glide framework.
/// </remarks
public static partial class CustomCommand
{
    /// <summary>
    /// Creates a new instance of the <see cref="CustomCommand{T1, T2, T3, T4}"/> structure with the specified argument.
    /// </summary>
    /// <typeparam name="T1">The type of the first argument for the command.</typeparam>
    /// <param name="Arg1">The value of the first argument to be passed to the command.</param>
    /// <typeparam name="T2">The type of the second argument for the command.</typeparam>
    /// <param name="Arg2">The value of the second argument to be passed to the command.</param>
    /// <typeparam name="T3">The type of the third argument for the command.</typeparam>
    /// <param name="Arg3">The value of the third argument to be passed to the command.</param>
    /// <typeparam name="T4">The type of the fourth argument for the command.</typeparam>
    /// <param name="Arg4">The value of the fourth argument to be passed to the command.</param>
    /// <returns>An instance of <see cref="CustomCommand{T1, T2, T3, T4}"/> initialized with the provided argument.</returns>
    public static CustomCommand<TRoutingInfo, T1, T2, T3, T4> Create<TRoutingInfo, T1, T2, T3, T4>(TRoutingInfo routingInfo, T1 arg1, T2 arg2, T3 arg3, T4 arg4)
         where TRoutingInfo : IRoutingInfo
         => new CustomCommand<TRoutingInfo, T1, T2, T3, T4>{RoutingInfo = routingInfo}
                .WithArg1(arg1)
                .WithArg2(arg2)
                .WithArg3(arg3)
                .WithArg4(arg4);
}

/// <summary>
/// Represents a customizable command that can accept a specified number
/// of generic arguments and implements the IGlideCommand interface.
/// </summary>
/// <typeparam name="T1">The type of the first argument for the command.</typeparam>
/// <typeparam name="T2">The type of the second argument for the command.</typeparam>
/// <typeparam name="T3">The type of the third argument for the command.</typeparam>
/// <typeparam name="T4">The type of the fourth argument for the command.</typeparam>
/// <remarks>
/// <list type="bullet">
/// <item>
/// Each instance of the CustomCommand struct is immutable, and the usage of `WithArg` methods ensures
/// a fluent API for adding argument values while preserving immutability. It integrates with the IGlideCommand
/// interface for executing tasks asynchronously within the Glide framework.
/// </item>
/// <item>All values are handled as "values". If you want to issue command text, use the <see cref="Data.CommandText"/> struct</item>
/// </list>
/// </remarks>
public readonly struct CustomCommand<TRoutingInfo, T1, T2, T3, T4> : IGlideCommand where TRoutingInfo : IRoutingInfo
{
    public required TRoutingInfo RoutingInfo { get; init; }

    private T1? Arg1 { get; init; }
    private bool Arg1Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4> WithArg1(T1 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg1 = arg, Arg1Set = true };
    }

    private T2? Arg2 { get; init; }
    private bool Arg2Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4> WithArg2(T2 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg2 = arg, Arg2Set = true };
    }

    private T3? Arg3 { get; init; }
    private bool Arg3Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4> WithArg3(T3 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg3 = arg, Arg3Set = true };
    }

    private T4? Arg4 { get; init; }
    private bool Arg4Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4> WithArg4(T4 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg4 = arg, Arg4Set = true };
    }

    Task<Value> IGlideCommand.ExecuteAsync(IGlideClient client, CancellationToken cancellationToken = default)
    {
        if (!Arg1Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 0));
        if (!Arg2Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 1));
        if (!Arg3Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 2));
        if (!Arg4Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 3));
        return client.SendCommandAsync(ERequestType.CustomCommand, RoutingInfo, client.ToParameter(Arg1), client.ToParameter(Arg2), client.ToParameter(Arg3), client.ToParameter(Arg4));
    }
}

/// <summary>
/// Provides factory methods for creating instances of the CustomCommand structures.
/// </summary>
/// <remarks>
/// This static class enables the creation of CustomCommand instances with varying numbers of generic arguments,
/// facilitating a fluent and type-safe approach to defining and executing commands within the Glide framework.
/// </remarks
public static partial class CustomCommand
{
    /// <summary>
    /// Creates a new instance of the <see cref="CustomCommand{T1, T2, T3, T4, T5}"/> structure with the specified argument.
    /// </summary>
    /// <typeparam name="T1">The type of the first argument for the command.</typeparam>
    /// <param name="Arg1">The value of the first argument to be passed to the command.</param>
    /// <typeparam name="T2">The type of the second argument for the command.</typeparam>
    /// <param name="Arg2">The value of the second argument to be passed to the command.</param>
    /// <typeparam name="T3">The type of the third argument for the command.</typeparam>
    /// <param name="Arg3">The value of the third argument to be passed to the command.</param>
    /// <typeparam name="T4">The type of the fourth argument for the command.</typeparam>
    /// <param name="Arg4">The value of the fourth argument to be passed to the command.</param>
    /// <typeparam name="T5">The type of the fifth argument for the command.</typeparam>
    /// <param name="Arg5">The value of the fifth argument to be passed to the command.</param>
    /// <returns>An instance of <see cref="CustomCommand{T1, T2, T3, T4, T5}"/> initialized with the provided argument.</returns>
    public static CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5> Create<TRoutingInfo, T1, T2, T3, T4, T5>(TRoutingInfo routingInfo, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5)
         where TRoutingInfo : IRoutingInfo
         => new CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5>{RoutingInfo = routingInfo}
                .WithArg1(arg1)
                .WithArg2(arg2)
                .WithArg3(arg3)
                .WithArg4(arg4)
                .WithArg5(arg5);
}

/// <summary>
/// Represents a customizable command that can accept a specified number
/// of generic arguments and implements the IGlideCommand interface.
/// </summary>
/// <typeparam name="T1">The type of the first argument for the command.</typeparam>
/// <typeparam name="T2">The type of the second argument for the command.</typeparam>
/// <typeparam name="T3">The type of the third argument for the command.</typeparam>
/// <typeparam name="T4">The type of the fourth argument for the command.</typeparam>
/// <typeparam name="T5">The type of the fifth argument for the command.</typeparam>
/// <remarks>
/// <list type="bullet">
/// <item>
/// Each instance of the CustomCommand struct is immutable, and the usage of `WithArg` methods ensures
/// a fluent API for adding argument values while preserving immutability. It integrates with the IGlideCommand
/// interface for executing tasks asynchronously within the Glide framework.
/// </item>
/// <item>All values are handled as "values". If you want to issue command text, use the <see cref="Data.CommandText"/> struct</item>
/// </list>
/// </remarks>
public readonly struct CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5> : IGlideCommand where TRoutingInfo : IRoutingInfo
{
    public required TRoutingInfo RoutingInfo { get; init; }

    private T1? Arg1 { get; init; }
    private bool Arg1Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5> WithArg1(T1 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg1 = arg, Arg1Set = true };
    }

    private T2? Arg2 { get; init; }
    private bool Arg2Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5> WithArg2(T2 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg2 = arg, Arg2Set = true };
    }

    private T3? Arg3 { get; init; }
    private bool Arg3Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5> WithArg3(T3 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg3 = arg, Arg3Set = true };
    }

    private T4? Arg4 { get; init; }
    private bool Arg4Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5> WithArg4(T4 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg4 = arg, Arg4Set = true };
    }

    private T5? Arg5 { get; init; }
    private bool Arg5Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5> WithArg5(T5 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg5 = arg, Arg5Set = true };
    }

    Task<Value> IGlideCommand.ExecuteAsync(IGlideClient client, CancellationToken cancellationToken = default)
    {
        if (!Arg1Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 0));
        if (!Arg2Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 1));
        if (!Arg3Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 2));
        if (!Arg4Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 3));
        if (!Arg5Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 4));
        return client.SendCommandAsync(ERequestType.CustomCommand, RoutingInfo, client.ToParameter(Arg1), client.ToParameter(Arg2), client.ToParameter(Arg3), client.ToParameter(Arg4), client.ToParameter(Arg5));
    }
}

/// <summary>
/// Provides factory methods for creating instances of the CustomCommand structures.
/// </summary>
/// <remarks>
/// This static class enables the creation of CustomCommand instances with varying numbers of generic arguments,
/// facilitating a fluent and type-safe approach to defining and executing commands within the Glide framework.
/// </remarks
public static partial class CustomCommand
{
    /// <summary>
    /// Creates a new instance of the <see cref="CustomCommand{T1, T2, T3, T4, T5, T6}"/> structure with the specified argument.
    /// </summary>
    /// <typeparam name="T1">The type of the first argument for the command.</typeparam>
    /// <param name="Arg1">The value of the first argument to be passed to the command.</param>
    /// <typeparam name="T2">The type of the second argument for the command.</typeparam>
    /// <param name="Arg2">The value of the second argument to be passed to the command.</param>
    /// <typeparam name="T3">The type of the third argument for the command.</typeparam>
    /// <param name="Arg3">The value of the third argument to be passed to the command.</param>
    /// <typeparam name="T4">The type of the fourth argument for the command.</typeparam>
    /// <param name="Arg4">The value of the fourth argument to be passed to the command.</param>
    /// <typeparam name="T5">The type of the fifth argument for the command.</typeparam>
    /// <param name="Arg5">The value of the fifth argument to be passed to the command.</param>
    /// <typeparam name="T6">The type of the sixth argument for the command.</typeparam>
    /// <param name="Arg6">The value of the sixth argument to be passed to the command.</param>
    /// <returns>An instance of <see cref="CustomCommand{T1, T2, T3, T4, T5, T6}"/> initialized with the provided argument.</returns>
    public static CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6> Create<TRoutingInfo, T1, T2, T3, T4, T5, T6>(TRoutingInfo routingInfo, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6)
         where TRoutingInfo : IRoutingInfo
         => new CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6>{RoutingInfo = routingInfo}
                .WithArg1(arg1)
                .WithArg2(arg2)
                .WithArg3(arg3)
                .WithArg4(arg4)
                .WithArg5(arg5)
                .WithArg6(arg6);
}

/// <summary>
/// Represents a customizable command that can accept a specified number
/// of generic arguments and implements the IGlideCommand interface.
/// </summary>
/// <typeparam name="T1">The type of the first argument for the command.</typeparam>
/// <typeparam name="T2">The type of the second argument for the command.</typeparam>
/// <typeparam name="T3">The type of the third argument for the command.</typeparam>
/// <typeparam name="T4">The type of the fourth argument for the command.</typeparam>
/// <typeparam name="T5">The type of the fifth argument for the command.</typeparam>
/// <typeparam name="T6">The type of the sixth argument for the command.</typeparam>
/// <remarks>
/// <list type="bullet">
/// <item>
/// Each instance of the CustomCommand struct is immutable, and the usage of `WithArg` methods ensures
/// a fluent API for adding argument values while preserving immutability. It integrates with the IGlideCommand
/// interface for executing tasks asynchronously within the Glide framework.
/// </item>
/// <item>All values are handled as "values". If you want to issue command text, use the <see cref="Data.CommandText"/> struct</item>
/// </list>
/// </remarks>
public readonly struct CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6> : IGlideCommand where TRoutingInfo : IRoutingInfo
{
    public required TRoutingInfo RoutingInfo { get; init; }

    private T1? Arg1 { get; init; }
    private bool Arg1Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6> WithArg1(T1 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg1 = arg, Arg1Set = true };
    }

    private T2? Arg2 { get; init; }
    private bool Arg2Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6> WithArg2(T2 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg2 = arg, Arg2Set = true };
    }

    private T3? Arg3 { get; init; }
    private bool Arg3Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6> WithArg3(T3 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg3 = arg, Arg3Set = true };
    }

    private T4? Arg4 { get; init; }
    private bool Arg4Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6> WithArg4(T4 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg4 = arg, Arg4Set = true };
    }

    private T5? Arg5 { get; init; }
    private bool Arg5Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6> WithArg5(T5 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg5 = arg, Arg5Set = true };
    }

    private T6? Arg6 { get; init; }
    private bool Arg6Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6> WithArg6(T6 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg6 = arg, Arg6Set = true };
    }

    Task<Value> IGlideCommand.ExecuteAsync(IGlideClient client, CancellationToken cancellationToken = default)
    {
        if (!Arg1Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 0));
        if (!Arg2Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 1));
        if (!Arg3Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 2));
        if (!Arg4Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 3));
        if (!Arg5Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 4));
        if (!Arg6Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 5));
        return client.SendCommandAsync(ERequestType.CustomCommand, RoutingInfo, client.ToParameter(Arg1), client.ToParameter(Arg2), client.ToParameter(Arg3), client.ToParameter(Arg4), client.ToParameter(Arg5), client.ToParameter(Arg6));
    }
}

/// <summary>
/// Provides factory methods for creating instances of the CustomCommand structures.
/// </summary>
/// <remarks>
/// This static class enables the creation of CustomCommand instances with varying numbers of generic arguments,
/// facilitating a fluent and type-safe approach to defining and executing commands within the Glide framework.
/// </remarks
public static partial class CustomCommand
{
    /// <summary>
    /// Creates a new instance of the <see cref="CustomCommand{T1, T2, T3, T4, T5, T6, T7}"/> structure with the specified argument.
    /// </summary>
    /// <typeparam name="T1">The type of the first argument for the command.</typeparam>
    /// <param name="Arg1">The value of the first argument to be passed to the command.</param>
    /// <typeparam name="T2">The type of the second argument for the command.</typeparam>
    /// <param name="Arg2">The value of the second argument to be passed to the command.</param>
    /// <typeparam name="T3">The type of the third argument for the command.</typeparam>
    /// <param name="Arg3">The value of the third argument to be passed to the command.</param>
    /// <typeparam name="T4">The type of the fourth argument for the command.</typeparam>
    /// <param name="Arg4">The value of the fourth argument to be passed to the command.</param>
    /// <typeparam name="T5">The type of the fifth argument for the command.</typeparam>
    /// <param name="Arg5">The value of the fifth argument to be passed to the command.</param>
    /// <typeparam name="T6">The type of the sixth argument for the command.</typeparam>
    /// <param name="Arg6">The value of the sixth argument to be passed to the command.</param>
    /// <typeparam name="T7">The type of the seventh argument for the command.</typeparam>
    /// <param name="Arg7">The value of the seventh argument to be passed to the command.</param>
    /// <returns>An instance of <see cref="CustomCommand{T1, T2, T3, T4, T5, T6, T7}"/> initialized with the provided argument.</returns>
    public static CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7> Create<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7>(TRoutingInfo routingInfo, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7)
         where TRoutingInfo : IRoutingInfo
         => new CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7>{RoutingInfo = routingInfo}
                .WithArg1(arg1)
                .WithArg2(arg2)
                .WithArg3(arg3)
                .WithArg4(arg4)
                .WithArg5(arg5)
                .WithArg6(arg6)
                .WithArg7(arg7);
}

/// <summary>
/// Represents a customizable command that can accept a specified number
/// of generic arguments and implements the IGlideCommand interface.
/// </summary>
/// <typeparam name="T1">The type of the first argument for the command.</typeparam>
/// <typeparam name="T2">The type of the second argument for the command.</typeparam>
/// <typeparam name="T3">The type of the third argument for the command.</typeparam>
/// <typeparam name="T4">The type of the fourth argument for the command.</typeparam>
/// <typeparam name="T5">The type of the fifth argument for the command.</typeparam>
/// <typeparam name="T6">The type of the sixth argument for the command.</typeparam>
/// <typeparam name="T7">The type of the seventh argument for the command.</typeparam>
/// <remarks>
/// <list type="bullet">
/// <item>
/// Each instance of the CustomCommand struct is immutable, and the usage of `WithArg` methods ensures
/// a fluent API for adding argument values while preserving immutability. It integrates with the IGlideCommand
/// interface for executing tasks asynchronously within the Glide framework.
/// </item>
/// <item>All values are handled as "values". If you want to issue command text, use the <see cref="Data.CommandText"/> struct</item>
/// </list>
/// </remarks>
public readonly struct CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7> : IGlideCommand where TRoutingInfo : IRoutingInfo
{
    public required TRoutingInfo RoutingInfo { get; init; }

    private T1? Arg1 { get; init; }
    private bool Arg1Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7> WithArg1(T1 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg1 = arg, Arg1Set = true };
    }

    private T2? Arg2 { get; init; }
    private bool Arg2Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7> WithArg2(T2 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg2 = arg, Arg2Set = true };
    }

    private T3? Arg3 { get; init; }
    private bool Arg3Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7> WithArg3(T3 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg3 = arg, Arg3Set = true };
    }

    private T4? Arg4 { get; init; }
    private bool Arg4Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7> WithArg4(T4 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg4 = arg, Arg4Set = true };
    }

    private T5? Arg5 { get; init; }
    private bool Arg5Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7> WithArg5(T5 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg5 = arg, Arg5Set = true };
    }

    private T6? Arg6 { get; init; }
    private bool Arg6Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7> WithArg6(T6 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg6 = arg, Arg6Set = true };
    }

    private T7? Arg7 { get; init; }
    private bool Arg7Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7> WithArg7(T7 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg7 = arg, Arg7Set = true };
    }

    Task<Value> IGlideCommand.ExecuteAsync(IGlideClient client, CancellationToken cancellationToken = default)
    {
        if (!Arg1Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 0));
        if (!Arg2Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 1));
        if (!Arg3Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 2));
        if (!Arg4Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 3));
        if (!Arg5Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 4));
        if (!Arg6Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 5));
        if (!Arg7Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 6));
        return client.SendCommandAsync(ERequestType.CustomCommand, RoutingInfo, client.ToParameter(Arg1), client.ToParameter(Arg2), client.ToParameter(Arg3), client.ToParameter(Arg4), client.ToParameter(Arg5), client.ToParameter(Arg6), client.ToParameter(Arg7));
    }
}

/// <summary>
/// Provides factory methods for creating instances of the CustomCommand structures.
/// </summary>
/// <remarks>
/// This static class enables the creation of CustomCommand instances with varying numbers of generic arguments,
/// facilitating a fluent and type-safe approach to defining and executing commands within the Glide framework.
/// </remarks
public static partial class CustomCommand
{
    /// <summary>
    /// Creates a new instance of the <see cref="CustomCommand{T1, T2, T3, T4, T5, T6, T7, T8}"/> structure with the specified argument.
    /// </summary>
    /// <typeparam name="T1">The type of the first argument for the command.</typeparam>
    /// <param name="Arg1">The value of the first argument to be passed to the command.</param>
    /// <typeparam name="T2">The type of the second argument for the command.</typeparam>
    /// <param name="Arg2">The value of the second argument to be passed to the command.</param>
    /// <typeparam name="T3">The type of the third argument for the command.</typeparam>
    /// <param name="Arg3">The value of the third argument to be passed to the command.</param>
    /// <typeparam name="T4">The type of the fourth argument for the command.</typeparam>
    /// <param name="Arg4">The value of the fourth argument to be passed to the command.</param>
    /// <typeparam name="T5">The type of the fifth argument for the command.</typeparam>
    /// <param name="Arg5">The value of the fifth argument to be passed to the command.</param>
    /// <typeparam name="T6">The type of the sixth argument for the command.</typeparam>
    /// <param name="Arg6">The value of the sixth argument to be passed to the command.</param>
    /// <typeparam name="T7">The type of the seventh argument for the command.</typeparam>
    /// <param name="Arg7">The value of the seventh argument to be passed to the command.</param>
    /// <typeparam name="T8">The type of the eighth argument for the command.</typeparam>
    /// <param name="Arg8">The value of the eighth argument to be passed to the command.</param>
    /// <returns>An instance of <see cref="CustomCommand{T1, T2, T3, T4, T5, T6, T7, T8}"/> initialized with the provided argument.</returns>
    public static CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8> Create<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8>(TRoutingInfo routingInfo, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8)
         where TRoutingInfo : IRoutingInfo
         => new CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8>{RoutingInfo = routingInfo}
                .WithArg1(arg1)
                .WithArg2(arg2)
                .WithArg3(arg3)
                .WithArg4(arg4)
                .WithArg5(arg5)
                .WithArg6(arg6)
                .WithArg7(arg7)
                .WithArg8(arg8);
}

/// <summary>
/// Represents a customizable command that can accept a specified number
/// of generic arguments and implements the IGlideCommand interface.
/// </summary>
/// <typeparam name="T1">The type of the first argument for the command.</typeparam>
/// <typeparam name="T2">The type of the second argument for the command.</typeparam>
/// <typeparam name="T3">The type of the third argument for the command.</typeparam>
/// <typeparam name="T4">The type of the fourth argument for the command.</typeparam>
/// <typeparam name="T5">The type of the fifth argument for the command.</typeparam>
/// <typeparam name="T6">The type of the sixth argument for the command.</typeparam>
/// <typeparam name="T7">The type of the seventh argument for the command.</typeparam>
/// <typeparam name="T8">The type of the eighth argument for the command.</typeparam>
/// <remarks>
/// <list type="bullet">
/// <item>
/// Each instance of the CustomCommand struct is immutable, and the usage of `WithArg` methods ensures
/// a fluent API for adding argument values while preserving immutability. It integrates with the IGlideCommand
/// interface for executing tasks asynchronously within the Glide framework.
/// </item>
/// <item>All values are handled as "values". If you want to issue command text, use the <see cref="Data.CommandText"/> struct</item>
/// </list>
/// </remarks>
public readonly struct CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8> : IGlideCommand where TRoutingInfo : IRoutingInfo
{
    public required TRoutingInfo RoutingInfo { get; init; }

    private T1? Arg1 { get; init; }
    private bool Arg1Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8> WithArg1(T1 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg1 = arg, Arg1Set = true };
    }

    private T2? Arg2 { get; init; }
    private bool Arg2Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8> WithArg2(T2 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg2 = arg, Arg2Set = true };
    }

    private T3? Arg3 { get; init; }
    private bool Arg3Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8> WithArg3(T3 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg3 = arg, Arg3Set = true };
    }

    private T4? Arg4 { get; init; }
    private bool Arg4Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8> WithArg4(T4 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg4 = arg, Arg4Set = true };
    }

    private T5? Arg5 { get; init; }
    private bool Arg5Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8> WithArg5(T5 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg5 = arg, Arg5Set = true };
    }

    private T6? Arg6 { get; init; }
    private bool Arg6Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8> WithArg6(T6 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg6 = arg, Arg6Set = true };
    }

    private T7? Arg7 { get; init; }
    private bool Arg7Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8> WithArg7(T7 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg7 = arg, Arg7Set = true };
    }

    private T8? Arg8 { get; init; }
    private bool Arg8Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8> WithArg8(T8 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg8 = arg, Arg8Set = true };
    }

    Task<Value> IGlideCommand.ExecuteAsync(IGlideClient client, CancellationToken cancellationToken = default)
    {
        if (!Arg1Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 0));
        if (!Arg2Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 1));
        if (!Arg3Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 2));
        if (!Arg4Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 3));
        if (!Arg5Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 4));
        if (!Arg6Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 5));
        if (!Arg7Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 6));
        if (!Arg8Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 7));
        return client.SendCommandAsync(ERequestType.CustomCommand, RoutingInfo, client.ToParameter(Arg1), client.ToParameter(Arg2), client.ToParameter(Arg3), client.ToParameter(Arg4), client.ToParameter(Arg5), client.ToParameter(Arg6), client.ToParameter(Arg7), client.ToParameter(Arg8));
    }
}

/// <summary>
/// Provides factory methods for creating instances of the CustomCommand structures.
/// </summary>
/// <remarks>
/// This static class enables the creation of CustomCommand instances with varying numbers of generic arguments,
/// facilitating a fluent and type-safe approach to defining and executing commands within the Glide framework.
/// </remarks
public static partial class CustomCommand
{
    /// <summary>
    /// Creates a new instance of the <see cref="CustomCommand{T1, T2, T3, T4, T5, T6, T7, T8, T9}"/> structure with the specified argument.
    /// </summary>
    /// <typeparam name="T1">The type of the first argument for the command.</typeparam>
    /// <param name="Arg1">The value of the first argument to be passed to the command.</param>
    /// <typeparam name="T2">The type of the second argument for the command.</typeparam>
    /// <param name="Arg2">The value of the second argument to be passed to the command.</param>
    /// <typeparam name="T3">The type of the third argument for the command.</typeparam>
    /// <param name="Arg3">The value of the third argument to be passed to the command.</param>
    /// <typeparam name="T4">The type of the fourth argument for the command.</typeparam>
    /// <param name="Arg4">The value of the fourth argument to be passed to the command.</param>
    /// <typeparam name="T5">The type of the fifth argument for the command.</typeparam>
    /// <param name="Arg5">The value of the fifth argument to be passed to the command.</param>
    /// <typeparam name="T6">The type of the sixth argument for the command.</typeparam>
    /// <param name="Arg6">The value of the sixth argument to be passed to the command.</param>
    /// <typeparam name="T7">The type of the seventh argument for the command.</typeparam>
    /// <param name="Arg7">The value of the seventh argument to be passed to the command.</param>
    /// <typeparam name="T8">The type of the eighth argument for the command.</typeparam>
    /// <param name="Arg8">The value of the eighth argument to be passed to the command.</param>
    /// <typeparam name="T9">The type of the ninth argument for the command.</typeparam>
    /// <param name="Arg9">The value of the ninth argument to be passed to the command.</param>
    /// <returns>An instance of <see cref="CustomCommand{T1, T2, T3, T4, T5, T6, T7, T8, T9}"/> initialized with the provided argument.</returns>
    public static CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9> Create<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9>(TRoutingInfo routingInfo, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9)
         where TRoutingInfo : IRoutingInfo
         => new CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9>{RoutingInfo = routingInfo}
                .WithArg1(arg1)
                .WithArg2(arg2)
                .WithArg3(arg3)
                .WithArg4(arg4)
                .WithArg5(arg5)
                .WithArg6(arg6)
                .WithArg7(arg7)
                .WithArg8(arg8)
                .WithArg9(arg9);
}

/// <summary>
/// Represents a customizable command that can accept a specified number
/// of generic arguments and implements the IGlideCommand interface.
/// </summary>
/// <typeparam name="T1">The type of the first argument for the command.</typeparam>
/// <typeparam name="T2">The type of the second argument for the command.</typeparam>
/// <typeparam name="T3">The type of the third argument for the command.</typeparam>
/// <typeparam name="T4">The type of the fourth argument for the command.</typeparam>
/// <typeparam name="T5">The type of the fifth argument for the command.</typeparam>
/// <typeparam name="T6">The type of the sixth argument for the command.</typeparam>
/// <typeparam name="T7">The type of the seventh argument for the command.</typeparam>
/// <typeparam name="T8">The type of the eighth argument for the command.</typeparam>
/// <typeparam name="T9">The type of the ninth argument for the command.</typeparam>
/// <remarks>
/// <list type="bullet">
/// <item>
/// Each instance of the CustomCommand struct is immutable, and the usage of `WithArg` methods ensures
/// a fluent API for adding argument values while preserving immutability. It integrates with the IGlideCommand
/// interface for executing tasks asynchronously within the Glide framework.
/// </item>
/// <item>All values are handled as "values". If you want to issue command text, use the <see cref="Data.CommandText"/> struct</item>
/// </list>
/// </remarks>
public readonly struct CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9> : IGlideCommand where TRoutingInfo : IRoutingInfo
{
    public required TRoutingInfo RoutingInfo { get; init; }

    private T1? Arg1 { get; init; }
    private bool Arg1Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9> WithArg1(T1 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg1 = arg, Arg1Set = true };
    }

    private T2? Arg2 { get; init; }
    private bool Arg2Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9> WithArg2(T2 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg2 = arg, Arg2Set = true };
    }

    private T3? Arg3 { get; init; }
    private bool Arg3Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9> WithArg3(T3 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg3 = arg, Arg3Set = true };
    }

    private T4? Arg4 { get; init; }
    private bool Arg4Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9> WithArg4(T4 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg4 = arg, Arg4Set = true };
    }

    private T5? Arg5 { get; init; }
    private bool Arg5Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9> WithArg5(T5 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg5 = arg, Arg5Set = true };
    }

    private T6? Arg6 { get; init; }
    private bool Arg6Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9> WithArg6(T6 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg6 = arg, Arg6Set = true };
    }

    private T7? Arg7 { get; init; }
    private bool Arg7Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9> WithArg7(T7 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg7 = arg, Arg7Set = true };
    }

    private T8? Arg8 { get; init; }
    private bool Arg8Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9> WithArg8(T8 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg8 = arg, Arg8Set = true };
    }

    private T9? Arg9 { get; init; }
    private bool Arg9Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9> WithArg9(T9 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg9 = arg, Arg9Set = true };
    }

    Task<Value> IGlideCommand.ExecuteAsync(IGlideClient client, CancellationToken cancellationToken = default)
    {
        if (!Arg1Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 0));
        if (!Arg2Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 1));
        if (!Arg3Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 2));
        if (!Arg4Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 3));
        if (!Arg5Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 4));
        if (!Arg6Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 5));
        if (!Arg7Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 6));
        if (!Arg8Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 7));
        if (!Arg9Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 8));
        return client.SendCommandAsync(ERequestType.CustomCommand, RoutingInfo, client.ToParameter(Arg1), client.ToParameter(Arg2), client.ToParameter(Arg3), client.ToParameter(Arg4), client.ToParameter(Arg5), client.ToParameter(Arg6), client.ToParameter(Arg7), client.ToParameter(Arg8), client.ToParameter(Arg9));
    }
}

/// <summary>
/// Provides factory methods for creating instances of the CustomCommand structures.
/// </summary>
/// <remarks>
/// This static class enables the creation of CustomCommand instances with varying numbers of generic arguments,
/// facilitating a fluent and type-safe approach to defining and executing commands within the Glide framework.
/// </remarks
public static partial class CustomCommand
{
    /// <summary>
    /// Creates a new instance of the <see cref="CustomCommand{T1, T2, T3, T4, T5, T6, T7, T8, T9, T10}"/> structure with the specified argument.
    /// </summary>
    /// <typeparam name="T1">The type of the first argument for the command.</typeparam>
    /// <param name="Arg1">The value of the first argument to be passed to the command.</param>
    /// <typeparam name="T2">The type of the second argument for the command.</typeparam>
    /// <param name="Arg2">The value of the second argument to be passed to the command.</param>
    /// <typeparam name="T3">The type of the third argument for the command.</typeparam>
    /// <param name="Arg3">The value of the third argument to be passed to the command.</param>
    /// <typeparam name="T4">The type of the fourth argument for the command.</typeparam>
    /// <param name="Arg4">The value of the fourth argument to be passed to the command.</param>
    /// <typeparam name="T5">The type of the fifth argument for the command.</typeparam>
    /// <param name="Arg5">The value of the fifth argument to be passed to the command.</param>
    /// <typeparam name="T6">The type of the sixth argument for the command.</typeparam>
    /// <param name="Arg6">The value of the sixth argument to be passed to the command.</param>
    /// <typeparam name="T7">The type of the seventh argument for the command.</typeparam>
    /// <param name="Arg7">The value of the seventh argument to be passed to the command.</param>
    /// <typeparam name="T8">The type of the eighth argument for the command.</typeparam>
    /// <param name="Arg8">The value of the eighth argument to be passed to the command.</param>
    /// <typeparam name="T9">The type of the ninth argument for the command.</typeparam>
    /// <param name="Arg9">The value of the ninth argument to be passed to the command.</param>
    /// <typeparam name="T10">The type of the tenth argument for the command.</typeparam>
    /// <param name="Arg10">The value of the tenth argument to be passed to the command.</param>
    /// <returns>An instance of <see cref="CustomCommand{T1, T2, T3, T4, T5, T6, T7, T8, T9, T10}"/> initialized with the provided argument.</returns>
    public static CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> Create<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10>(TRoutingInfo routingInfo, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10)
         where TRoutingInfo : IRoutingInfo
         => new CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10>{RoutingInfo = routingInfo}
                .WithArg1(arg1)
                .WithArg2(arg2)
                .WithArg3(arg3)
                .WithArg4(arg4)
                .WithArg5(arg5)
                .WithArg6(arg6)
                .WithArg7(arg7)
                .WithArg8(arg8)
                .WithArg9(arg9)
                .WithArg10(arg10);
}

/// <summary>
/// Represents a customizable command that can accept a specified number
/// of generic arguments and implements the IGlideCommand interface.
/// </summary>
/// <typeparam name="T1">The type of the first argument for the command.</typeparam>
/// <typeparam name="T2">The type of the second argument for the command.</typeparam>
/// <typeparam name="T3">The type of the third argument for the command.</typeparam>
/// <typeparam name="T4">The type of the fourth argument for the command.</typeparam>
/// <typeparam name="T5">The type of the fifth argument for the command.</typeparam>
/// <typeparam name="T6">The type of the sixth argument for the command.</typeparam>
/// <typeparam name="T7">The type of the seventh argument for the command.</typeparam>
/// <typeparam name="T8">The type of the eighth argument for the command.</typeparam>
/// <typeparam name="T9">The type of the ninth argument for the command.</typeparam>
/// <typeparam name="T10">The type of the tenth argument for the command.</typeparam>
/// <remarks>
/// <list type="bullet">
/// <item>
/// Each instance of the CustomCommand struct is immutable, and the usage of `WithArg` methods ensures
/// a fluent API for adding argument values while preserving immutability. It integrates with the IGlideCommand
/// interface for executing tasks asynchronously within the Glide framework.
/// </item>
/// <item>All values are handled as "values". If you want to issue command text, use the <see cref="Data.CommandText"/> struct</item>
/// </list>
/// </remarks>
public readonly struct CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> : IGlideCommand where TRoutingInfo : IRoutingInfo
{
    public required TRoutingInfo RoutingInfo { get; init; }

    private T1? Arg1 { get; init; }
    private bool Arg1Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> WithArg1(T1 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg1 = arg, Arg1Set = true };
    }

    private T2? Arg2 { get; init; }
    private bool Arg2Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> WithArg2(T2 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg2 = arg, Arg2Set = true };
    }

    private T3? Arg3 { get; init; }
    private bool Arg3Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> WithArg3(T3 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg3 = arg, Arg3Set = true };
    }

    private T4? Arg4 { get; init; }
    private bool Arg4Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> WithArg4(T4 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg4 = arg, Arg4Set = true };
    }

    private T5? Arg5 { get; init; }
    private bool Arg5Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> WithArg5(T5 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg5 = arg, Arg5Set = true };
    }

    private T6? Arg6 { get; init; }
    private bool Arg6Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> WithArg6(T6 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg6 = arg, Arg6Set = true };
    }

    private T7? Arg7 { get; init; }
    private bool Arg7Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> WithArg7(T7 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg7 = arg, Arg7Set = true };
    }

    private T8? Arg8 { get; init; }
    private bool Arg8Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> WithArg8(T8 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg8 = arg, Arg8Set = true };
    }

    private T9? Arg9 { get; init; }
    private bool Arg9Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> WithArg9(T9 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg9 = arg, Arg9Set = true };
    }

    private T10? Arg10 { get; init; }
    private bool Arg10Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> WithArg10(T10 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg10 = arg, Arg10Set = true };
    }

    Task<Value> IGlideCommand.ExecuteAsync(IGlideClient client, CancellationToken cancellationToken = default)
    {
        if (!Arg1Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 0));
        if (!Arg2Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 1));
        if (!Arg3Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 2));
        if (!Arg4Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 3));
        if (!Arg5Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 4));
        if (!Arg6Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 5));
        if (!Arg7Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 6));
        if (!Arg8Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 7));
        if (!Arg9Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 8));
        if (!Arg10Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 9));
        return client.SendCommandAsync(ERequestType.CustomCommand, RoutingInfo, client.ToParameter(Arg1), client.ToParameter(Arg2), client.ToParameter(Arg3), client.ToParameter(Arg4), client.ToParameter(Arg5), client.ToParameter(Arg6), client.ToParameter(Arg7), client.ToParameter(Arg8), client.ToParameter(Arg9), client.ToParameter(Arg10));
    }
}

/// <summary>
/// Provides factory methods for creating instances of the CustomCommand structures.
/// </summary>
/// <remarks>
/// This static class enables the creation of CustomCommand instances with varying numbers of generic arguments,
/// facilitating a fluent and type-safe approach to defining and executing commands within the Glide framework.
/// </remarks
public static partial class CustomCommand
{
    /// <summary>
    /// Creates a new instance of the <see cref="CustomCommand{T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11}"/> structure with the specified argument.
    /// </summary>
    /// <typeparam name="T1">The type of the first argument for the command.</typeparam>
    /// <param name="Arg1">The value of the first argument to be passed to the command.</param>
    /// <typeparam name="T2">The type of the second argument for the command.</typeparam>
    /// <param name="Arg2">The value of the second argument to be passed to the command.</param>
    /// <typeparam name="T3">The type of the third argument for the command.</typeparam>
    /// <param name="Arg3">The value of the third argument to be passed to the command.</param>
    /// <typeparam name="T4">The type of the fourth argument for the command.</typeparam>
    /// <param name="Arg4">The value of the fourth argument to be passed to the command.</param>
    /// <typeparam name="T5">The type of the fifth argument for the command.</typeparam>
    /// <param name="Arg5">The value of the fifth argument to be passed to the command.</param>
    /// <typeparam name="T6">The type of the sixth argument for the command.</typeparam>
    /// <param name="Arg6">The value of the sixth argument to be passed to the command.</param>
    /// <typeparam name="T7">The type of the seventh argument for the command.</typeparam>
    /// <param name="Arg7">The value of the seventh argument to be passed to the command.</param>
    /// <typeparam name="T8">The type of the eighth argument for the command.</typeparam>
    /// <param name="Arg8">The value of the eighth argument to be passed to the command.</param>
    /// <typeparam name="T9">The type of the ninth argument for the command.</typeparam>
    /// <param name="Arg9">The value of the ninth argument to be passed to the command.</param>
    /// <typeparam name="T10">The type of the tenth argument for the command.</typeparam>
    /// <param name="Arg10">The value of the tenth argument to be passed to the command.</param>
    /// <typeparam name="T11">The type of the eleventh argument for the command.</typeparam>
    /// <param name="Arg11">The value of the eleventh argument to be passed to the command.</param>
    /// <returns>An instance of <see cref="CustomCommand{T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11}"/> initialized with the provided argument.</returns>
    public static CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> Create<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11>(TRoutingInfo routingInfo, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11)
         where TRoutingInfo : IRoutingInfo
         => new CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11>{RoutingInfo = routingInfo}
                .WithArg1(arg1)
                .WithArg2(arg2)
                .WithArg3(arg3)
                .WithArg4(arg4)
                .WithArg5(arg5)
                .WithArg6(arg6)
                .WithArg7(arg7)
                .WithArg8(arg8)
                .WithArg9(arg9)
                .WithArg10(arg10)
                .WithArg11(arg11);
}

/// <summary>
/// Represents a customizable command that can accept a specified number
/// of generic arguments and implements the IGlideCommand interface.
/// </summary>
/// <typeparam name="T1">The type of the first argument for the command.</typeparam>
/// <typeparam name="T2">The type of the second argument for the command.</typeparam>
/// <typeparam name="T3">The type of the third argument for the command.</typeparam>
/// <typeparam name="T4">The type of the fourth argument for the command.</typeparam>
/// <typeparam name="T5">The type of the fifth argument for the command.</typeparam>
/// <typeparam name="T6">The type of the sixth argument for the command.</typeparam>
/// <typeparam name="T7">The type of the seventh argument for the command.</typeparam>
/// <typeparam name="T8">The type of the eighth argument for the command.</typeparam>
/// <typeparam name="T9">The type of the ninth argument for the command.</typeparam>
/// <typeparam name="T10">The type of the tenth argument for the command.</typeparam>
/// <typeparam name="T11">The type of the eleventh argument for the command.</typeparam>
/// <remarks>
/// <list type="bullet">
/// <item>
/// Each instance of the CustomCommand struct is immutable, and the usage of `WithArg` methods ensures
/// a fluent API for adding argument values while preserving immutability. It integrates with the IGlideCommand
/// interface for executing tasks asynchronously within the Glide framework.
/// </item>
/// <item>All values are handled as "values". If you want to issue command text, use the <see cref="Data.CommandText"/> struct</item>
/// </list>
/// </remarks>
public readonly struct CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> : IGlideCommand where TRoutingInfo : IRoutingInfo
{
    public required TRoutingInfo RoutingInfo { get; init; }

    private T1? Arg1 { get; init; }
    private bool Arg1Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> WithArg1(T1 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg1 = arg, Arg1Set = true };
    }

    private T2? Arg2 { get; init; }
    private bool Arg2Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> WithArg2(T2 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg2 = arg, Arg2Set = true };
    }

    private T3? Arg3 { get; init; }
    private bool Arg3Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> WithArg3(T3 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg3 = arg, Arg3Set = true };
    }

    private T4? Arg4 { get; init; }
    private bool Arg4Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> WithArg4(T4 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg4 = arg, Arg4Set = true };
    }

    private T5? Arg5 { get; init; }
    private bool Arg5Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> WithArg5(T5 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg5 = arg, Arg5Set = true };
    }

    private T6? Arg6 { get; init; }
    private bool Arg6Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> WithArg6(T6 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg6 = arg, Arg6Set = true };
    }

    private T7? Arg7 { get; init; }
    private bool Arg7Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> WithArg7(T7 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg7 = arg, Arg7Set = true };
    }

    private T8? Arg8 { get; init; }
    private bool Arg8Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> WithArg8(T8 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg8 = arg, Arg8Set = true };
    }

    private T9? Arg9 { get; init; }
    private bool Arg9Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> WithArg9(T9 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg9 = arg, Arg9Set = true };
    }

    private T10? Arg10 { get; init; }
    private bool Arg10Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> WithArg10(T10 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg10 = arg, Arg10Set = true };
    }

    private T11? Arg11 { get; init; }
    private bool Arg11Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> WithArg11(T11 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg11 = arg, Arg11Set = true };
    }

    Task<Value> IGlideCommand.ExecuteAsync(IGlideClient client, CancellationToken cancellationToken = default)
    {
        if (!Arg1Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 0));
        if (!Arg2Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 1));
        if (!Arg3Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 2));
        if (!Arg4Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 3));
        if (!Arg5Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 4));
        if (!Arg6Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 5));
        if (!Arg7Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 6));
        if (!Arg8Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 7));
        if (!Arg9Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 8));
        if (!Arg10Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 9));
        if (!Arg11Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 10));
        return client.SendCommandAsync(ERequestType.CustomCommand, RoutingInfo, client.ToParameter(Arg1), client.ToParameter(Arg2), client.ToParameter(Arg3), client.ToParameter(Arg4), client.ToParameter(Arg5), client.ToParameter(Arg6), client.ToParameter(Arg7), client.ToParameter(Arg8), client.ToParameter(Arg9), client.ToParameter(Arg10), client.ToParameter(Arg11));
    }
}

/// <summary>
/// Provides factory methods for creating instances of the CustomCommand structures.
/// </summary>
/// <remarks>
/// This static class enables the creation of CustomCommand instances with varying numbers of generic arguments,
/// facilitating a fluent and type-safe approach to defining and executing commands within the Glide framework.
/// </remarks
public static partial class CustomCommand
{
    /// <summary>
    /// Creates a new instance of the <see cref="CustomCommand{T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12}"/> structure with the specified argument.
    /// </summary>
    /// <typeparam name="T1">The type of the first argument for the command.</typeparam>
    /// <param name="Arg1">The value of the first argument to be passed to the command.</param>
    /// <typeparam name="T2">The type of the second argument for the command.</typeparam>
    /// <param name="Arg2">The value of the second argument to be passed to the command.</param>
    /// <typeparam name="T3">The type of the third argument for the command.</typeparam>
    /// <param name="Arg3">The value of the third argument to be passed to the command.</param>
    /// <typeparam name="T4">The type of the fourth argument for the command.</typeparam>
    /// <param name="Arg4">The value of the fourth argument to be passed to the command.</param>
    /// <typeparam name="T5">The type of the fifth argument for the command.</typeparam>
    /// <param name="Arg5">The value of the fifth argument to be passed to the command.</param>
    /// <typeparam name="T6">The type of the sixth argument for the command.</typeparam>
    /// <param name="Arg6">The value of the sixth argument to be passed to the command.</param>
    /// <typeparam name="T7">The type of the seventh argument for the command.</typeparam>
    /// <param name="Arg7">The value of the seventh argument to be passed to the command.</param>
    /// <typeparam name="T8">The type of the eighth argument for the command.</typeparam>
    /// <param name="Arg8">The value of the eighth argument to be passed to the command.</param>
    /// <typeparam name="T9">The type of the ninth argument for the command.</typeparam>
    /// <param name="Arg9">The value of the ninth argument to be passed to the command.</param>
    /// <typeparam name="T10">The type of the tenth argument for the command.</typeparam>
    /// <param name="Arg10">The value of the tenth argument to be passed to the command.</param>
    /// <typeparam name="T11">The type of the eleventh argument for the command.</typeparam>
    /// <param name="Arg11">The value of the eleventh argument to be passed to the command.</param>
    /// <typeparam name="T12">The type of the twelfth argument for the command.</typeparam>
    /// <param name="Arg12">The value of the twelfth argument to be passed to the command.</param>
    /// <returns>An instance of <see cref="CustomCommand{T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12}"/> initialized with the provided argument.</returns>
    public static CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> Create<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12>(TRoutingInfo routingInfo, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11, T12 arg12)
         where TRoutingInfo : IRoutingInfo
         => new CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12>{RoutingInfo = routingInfo}
                .WithArg1(arg1)
                .WithArg2(arg2)
                .WithArg3(arg3)
                .WithArg4(arg4)
                .WithArg5(arg5)
                .WithArg6(arg6)
                .WithArg7(arg7)
                .WithArg8(arg8)
                .WithArg9(arg9)
                .WithArg10(arg10)
                .WithArg11(arg11)
                .WithArg12(arg12);
}

/// <summary>
/// Represents a customizable command that can accept a specified number
/// of generic arguments and implements the IGlideCommand interface.
/// </summary>
/// <typeparam name="T1">The type of the first argument for the command.</typeparam>
/// <typeparam name="T2">The type of the second argument for the command.</typeparam>
/// <typeparam name="T3">The type of the third argument for the command.</typeparam>
/// <typeparam name="T4">The type of the fourth argument for the command.</typeparam>
/// <typeparam name="T5">The type of the fifth argument for the command.</typeparam>
/// <typeparam name="T6">The type of the sixth argument for the command.</typeparam>
/// <typeparam name="T7">The type of the seventh argument for the command.</typeparam>
/// <typeparam name="T8">The type of the eighth argument for the command.</typeparam>
/// <typeparam name="T9">The type of the ninth argument for the command.</typeparam>
/// <typeparam name="T10">The type of the tenth argument for the command.</typeparam>
/// <typeparam name="T11">The type of the eleventh argument for the command.</typeparam>
/// <typeparam name="T12">The type of the twelfth argument for the command.</typeparam>
/// <remarks>
/// <list type="bullet">
/// <item>
/// Each instance of the CustomCommand struct is immutable, and the usage of `WithArg` methods ensures
/// a fluent API for adding argument values while preserving immutability. It integrates with the IGlideCommand
/// interface for executing tasks asynchronously within the Glide framework.
/// </item>
/// <item>All values are handled as "values". If you want to issue command text, use the <see cref="Data.CommandText"/> struct</item>
/// </list>
/// </remarks>
public readonly struct CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> : IGlideCommand where TRoutingInfo : IRoutingInfo
{
    public required TRoutingInfo RoutingInfo { get; init; }

    private T1? Arg1 { get; init; }
    private bool Arg1Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> WithArg1(T1 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg1 = arg, Arg1Set = true };
    }

    private T2? Arg2 { get; init; }
    private bool Arg2Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> WithArg2(T2 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg2 = arg, Arg2Set = true };
    }

    private T3? Arg3 { get; init; }
    private bool Arg3Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> WithArg3(T3 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg3 = arg, Arg3Set = true };
    }

    private T4? Arg4 { get; init; }
    private bool Arg4Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> WithArg4(T4 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg4 = arg, Arg4Set = true };
    }

    private T5? Arg5 { get; init; }
    private bool Arg5Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> WithArg5(T5 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg5 = arg, Arg5Set = true };
    }

    private T6? Arg6 { get; init; }
    private bool Arg6Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> WithArg6(T6 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg6 = arg, Arg6Set = true };
    }

    private T7? Arg7 { get; init; }
    private bool Arg7Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> WithArg7(T7 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg7 = arg, Arg7Set = true };
    }

    private T8? Arg8 { get; init; }
    private bool Arg8Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> WithArg8(T8 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg8 = arg, Arg8Set = true };
    }

    private T9? Arg9 { get; init; }
    private bool Arg9Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> WithArg9(T9 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg9 = arg, Arg9Set = true };
    }

    private T10? Arg10 { get; init; }
    private bool Arg10Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> WithArg10(T10 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg10 = arg, Arg10Set = true };
    }

    private T11? Arg11 { get; init; }
    private bool Arg11Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> WithArg11(T11 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg11 = arg, Arg11Set = true };
    }

    private T12? Arg12 { get; init; }
    private bool Arg12Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> WithArg12(T12 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg12 = arg, Arg12Set = true };
    }

    Task<Value> IGlideCommand.ExecuteAsync(IGlideClient client, CancellationToken cancellationToken = default)
    {
        if (!Arg1Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 0));
        if (!Arg2Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 1));
        if (!Arg3Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 2));
        if (!Arg4Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 3));
        if (!Arg5Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 4));
        if (!Arg6Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 5));
        if (!Arg7Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 6));
        if (!Arg8Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 7));
        if (!Arg9Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 8));
        if (!Arg10Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 9));
        if (!Arg11Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 10));
        if (!Arg12Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 11));
        return client.SendCommandAsync(ERequestType.CustomCommand, RoutingInfo, client.ToParameter(Arg1), client.ToParameter(Arg2), client.ToParameter(Arg3), client.ToParameter(Arg4), client.ToParameter(Arg5), client.ToParameter(Arg6), client.ToParameter(Arg7), client.ToParameter(Arg8), client.ToParameter(Arg9), client.ToParameter(Arg10), client.ToParameter(Arg11), client.ToParameter(Arg12));
    }
}

/// <summary>
/// Provides factory methods for creating instances of the CustomCommand structures.
/// </summary>
/// <remarks>
/// This static class enables the creation of CustomCommand instances with varying numbers of generic arguments,
/// facilitating a fluent and type-safe approach to defining and executing commands within the Glide framework.
/// </remarks
public static partial class CustomCommand
{
    /// <summary>
    /// Creates a new instance of the <see cref="CustomCommand{T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13}"/> structure with the specified argument.
    /// </summary>
    /// <typeparam name="T1">The type of the first argument for the command.</typeparam>
    /// <param name="Arg1">The value of the first argument to be passed to the command.</param>
    /// <typeparam name="T2">The type of the second argument for the command.</typeparam>
    /// <param name="Arg2">The value of the second argument to be passed to the command.</param>
    /// <typeparam name="T3">The type of the third argument for the command.</typeparam>
    /// <param name="Arg3">The value of the third argument to be passed to the command.</param>
    /// <typeparam name="T4">The type of the fourth argument for the command.</typeparam>
    /// <param name="Arg4">The value of the fourth argument to be passed to the command.</param>
    /// <typeparam name="T5">The type of the fifth argument for the command.</typeparam>
    /// <param name="Arg5">The value of the fifth argument to be passed to the command.</param>
    /// <typeparam name="T6">The type of the sixth argument for the command.</typeparam>
    /// <param name="Arg6">The value of the sixth argument to be passed to the command.</param>
    /// <typeparam name="T7">The type of the seventh argument for the command.</typeparam>
    /// <param name="Arg7">The value of the seventh argument to be passed to the command.</param>
    /// <typeparam name="T8">The type of the eighth argument for the command.</typeparam>
    /// <param name="Arg8">The value of the eighth argument to be passed to the command.</param>
    /// <typeparam name="T9">The type of the ninth argument for the command.</typeparam>
    /// <param name="Arg9">The value of the ninth argument to be passed to the command.</param>
    /// <typeparam name="T10">The type of the tenth argument for the command.</typeparam>
    /// <param name="Arg10">The value of the tenth argument to be passed to the command.</param>
    /// <typeparam name="T11">The type of the eleventh argument for the command.</typeparam>
    /// <param name="Arg11">The value of the eleventh argument to be passed to the command.</param>
    /// <typeparam name="T12">The type of the twelfth argument for the command.</typeparam>
    /// <param name="Arg12">The value of the twelfth argument to be passed to the command.</param>
    /// <typeparam name="T13">The type of the thirteenth argument for the command.</typeparam>
    /// <param name="Arg13">The value of the thirteenth argument to be passed to the command.</param>
    /// <returns>An instance of <see cref="CustomCommand{T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13}"/> initialized with the provided argument.</returns>
    public static CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> Create<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13>(TRoutingInfo routingInfo, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11, T12 arg12, T13 arg13)
         where TRoutingInfo : IRoutingInfo
         => new CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13>{RoutingInfo = routingInfo}
                .WithArg1(arg1)
                .WithArg2(arg2)
                .WithArg3(arg3)
                .WithArg4(arg4)
                .WithArg5(arg5)
                .WithArg6(arg6)
                .WithArg7(arg7)
                .WithArg8(arg8)
                .WithArg9(arg9)
                .WithArg10(arg10)
                .WithArg11(arg11)
                .WithArg12(arg12)
                .WithArg13(arg13);
}

/// <summary>
/// Represents a customizable command that can accept a specified number
/// of generic arguments and implements the IGlideCommand interface.
/// </summary>
/// <typeparam name="T1">The type of the first argument for the command.</typeparam>
/// <typeparam name="T2">The type of the second argument for the command.</typeparam>
/// <typeparam name="T3">The type of the third argument for the command.</typeparam>
/// <typeparam name="T4">The type of the fourth argument for the command.</typeparam>
/// <typeparam name="T5">The type of the fifth argument for the command.</typeparam>
/// <typeparam name="T6">The type of the sixth argument for the command.</typeparam>
/// <typeparam name="T7">The type of the seventh argument for the command.</typeparam>
/// <typeparam name="T8">The type of the eighth argument for the command.</typeparam>
/// <typeparam name="T9">The type of the ninth argument for the command.</typeparam>
/// <typeparam name="T10">The type of the tenth argument for the command.</typeparam>
/// <typeparam name="T11">The type of the eleventh argument for the command.</typeparam>
/// <typeparam name="T12">The type of the twelfth argument for the command.</typeparam>
/// <typeparam name="T13">The type of the thirteenth argument for the command.</typeparam>
/// <remarks>
/// <list type="bullet">
/// <item>
/// Each instance of the CustomCommand struct is immutable, and the usage of `WithArg` methods ensures
/// a fluent API for adding argument values while preserving immutability. It integrates with the IGlideCommand
/// interface for executing tasks asynchronously within the Glide framework.
/// </item>
/// <item>All values are handled as "values". If you want to issue command text, use the <see cref="Data.CommandText"/> struct</item>
/// </list>
/// </remarks>
public readonly struct CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> : IGlideCommand where TRoutingInfo : IRoutingInfo
{
    public required TRoutingInfo RoutingInfo { get; init; }

    private T1? Arg1 { get; init; }
    private bool Arg1Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> WithArg1(T1 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg1 = arg, Arg1Set = true };
    }

    private T2? Arg2 { get; init; }
    private bool Arg2Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> WithArg2(T2 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg2 = arg, Arg2Set = true };
    }

    private T3? Arg3 { get; init; }
    private bool Arg3Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> WithArg3(T3 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg3 = arg, Arg3Set = true };
    }

    private T4? Arg4 { get; init; }
    private bool Arg4Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> WithArg4(T4 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg4 = arg, Arg4Set = true };
    }

    private T5? Arg5 { get; init; }
    private bool Arg5Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> WithArg5(T5 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg5 = arg, Arg5Set = true };
    }

    private T6? Arg6 { get; init; }
    private bool Arg6Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> WithArg6(T6 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg6 = arg, Arg6Set = true };
    }

    private T7? Arg7 { get; init; }
    private bool Arg7Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> WithArg7(T7 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg7 = arg, Arg7Set = true };
    }

    private T8? Arg8 { get; init; }
    private bool Arg8Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> WithArg8(T8 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg8 = arg, Arg8Set = true };
    }

    private T9? Arg9 { get; init; }
    private bool Arg9Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> WithArg9(T9 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg9 = arg, Arg9Set = true };
    }

    private T10? Arg10 { get; init; }
    private bool Arg10Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> WithArg10(T10 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg10 = arg, Arg10Set = true };
    }

    private T11? Arg11 { get; init; }
    private bool Arg11Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> WithArg11(T11 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg11 = arg, Arg11Set = true };
    }

    private T12? Arg12 { get; init; }
    private bool Arg12Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> WithArg12(T12 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg12 = arg, Arg12Set = true };
    }

    private T13? Arg13 { get; init; }
    private bool Arg13Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> WithArg13(T13 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg13 = arg, Arg13Set = true };
    }

    Task<Value> IGlideCommand.ExecuteAsync(IGlideClient client, CancellationToken cancellationToken = default)
    {
        if (!Arg1Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 0));
        if (!Arg2Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 1));
        if (!Arg3Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 2));
        if (!Arg4Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 3));
        if (!Arg5Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 4));
        if (!Arg6Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 5));
        if (!Arg7Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 6));
        if (!Arg8Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 7));
        if (!Arg9Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 8));
        if (!Arg10Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 9));
        if (!Arg11Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 10));
        if (!Arg12Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 11));
        if (!Arg13Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 12));
        return client.SendCommandAsync(ERequestType.CustomCommand, RoutingInfo, client.ToParameter(Arg1), client.ToParameter(Arg2), client.ToParameter(Arg3), client.ToParameter(Arg4), client.ToParameter(Arg5), client.ToParameter(Arg6), client.ToParameter(Arg7), client.ToParameter(Arg8), client.ToParameter(Arg9), client.ToParameter(Arg10), client.ToParameter(Arg11), client.ToParameter(Arg12), client.ToParameter(Arg13));
    }
}

/// <summary>
/// Provides factory methods for creating instances of the CustomCommand structures.
/// </summary>
/// <remarks>
/// This static class enables the creation of CustomCommand instances with varying numbers of generic arguments,
/// facilitating a fluent and type-safe approach to defining and executing commands within the Glide framework.
/// </remarks
public static partial class CustomCommand
{
    /// <summary>
    /// Creates a new instance of the <see cref="CustomCommand{T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14}"/> structure with the specified argument.
    /// </summary>
    /// <typeparam name="T1">The type of the first argument for the command.</typeparam>
    /// <param name="Arg1">The value of the first argument to be passed to the command.</param>
    /// <typeparam name="T2">The type of the second argument for the command.</typeparam>
    /// <param name="Arg2">The value of the second argument to be passed to the command.</param>
    /// <typeparam name="T3">The type of the third argument for the command.</typeparam>
    /// <param name="Arg3">The value of the third argument to be passed to the command.</param>
    /// <typeparam name="T4">The type of the fourth argument for the command.</typeparam>
    /// <param name="Arg4">The value of the fourth argument to be passed to the command.</param>
    /// <typeparam name="T5">The type of the fifth argument for the command.</typeparam>
    /// <param name="Arg5">The value of the fifth argument to be passed to the command.</param>
    /// <typeparam name="T6">The type of the sixth argument for the command.</typeparam>
    /// <param name="Arg6">The value of the sixth argument to be passed to the command.</param>
    /// <typeparam name="T7">The type of the seventh argument for the command.</typeparam>
    /// <param name="Arg7">The value of the seventh argument to be passed to the command.</param>
    /// <typeparam name="T8">The type of the eighth argument for the command.</typeparam>
    /// <param name="Arg8">The value of the eighth argument to be passed to the command.</param>
    /// <typeparam name="T9">The type of the ninth argument for the command.</typeparam>
    /// <param name="Arg9">The value of the ninth argument to be passed to the command.</param>
    /// <typeparam name="T10">The type of the tenth argument for the command.</typeparam>
    /// <param name="Arg10">The value of the tenth argument to be passed to the command.</param>
    /// <typeparam name="T11">The type of the eleventh argument for the command.</typeparam>
    /// <param name="Arg11">The value of the eleventh argument to be passed to the command.</param>
    /// <typeparam name="T12">The type of the twelfth argument for the command.</typeparam>
    /// <param name="Arg12">The value of the twelfth argument to be passed to the command.</param>
    /// <typeparam name="T13">The type of the thirteenth argument for the command.</typeparam>
    /// <param name="Arg13">The value of the thirteenth argument to be passed to the command.</param>
    /// <typeparam name="T14">The type of the fourteenth argument for the command.</typeparam>
    /// <param name="Arg14">The value of the fourteenth argument to be passed to the command.</param>
    /// <returns>An instance of <see cref="CustomCommand{T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14}"/> initialized with the provided argument.</returns>
    public static CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> Create<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14>(TRoutingInfo routingInfo, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11, T12 arg12, T13 arg13, T14 arg14)
         where TRoutingInfo : IRoutingInfo
         => new CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14>{RoutingInfo = routingInfo}
                .WithArg1(arg1)
                .WithArg2(arg2)
                .WithArg3(arg3)
                .WithArg4(arg4)
                .WithArg5(arg5)
                .WithArg6(arg6)
                .WithArg7(arg7)
                .WithArg8(arg8)
                .WithArg9(arg9)
                .WithArg10(arg10)
                .WithArg11(arg11)
                .WithArg12(arg12)
                .WithArg13(arg13)
                .WithArg14(arg14);
}

/// <summary>
/// Represents a customizable command that can accept a specified number
/// of generic arguments and implements the IGlideCommand interface.
/// </summary>
/// <typeparam name="T1">The type of the first argument for the command.</typeparam>
/// <typeparam name="T2">The type of the second argument for the command.</typeparam>
/// <typeparam name="T3">The type of the third argument for the command.</typeparam>
/// <typeparam name="T4">The type of the fourth argument for the command.</typeparam>
/// <typeparam name="T5">The type of the fifth argument for the command.</typeparam>
/// <typeparam name="T6">The type of the sixth argument for the command.</typeparam>
/// <typeparam name="T7">The type of the seventh argument for the command.</typeparam>
/// <typeparam name="T8">The type of the eighth argument for the command.</typeparam>
/// <typeparam name="T9">The type of the ninth argument for the command.</typeparam>
/// <typeparam name="T10">The type of the tenth argument for the command.</typeparam>
/// <typeparam name="T11">The type of the eleventh argument for the command.</typeparam>
/// <typeparam name="T12">The type of the twelfth argument for the command.</typeparam>
/// <typeparam name="T13">The type of the thirteenth argument for the command.</typeparam>
/// <typeparam name="T14">The type of the fourteenth argument for the command.</typeparam>
/// <remarks>
/// <list type="bullet">
/// <item>
/// Each instance of the CustomCommand struct is immutable, and the usage of `WithArg` methods ensures
/// a fluent API for adding argument values while preserving immutability. It integrates with the IGlideCommand
/// interface for executing tasks asynchronously within the Glide framework.
/// </item>
/// <item>All values are handled as "values". If you want to issue command text, use the <see cref="Data.CommandText"/> struct</item>
/// </list>
/// </remarks>
public readonly struct CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> : IGlideCommand where TRoutingInfo : IRoutingInfo
{
    public required TRoutingInfo RoutingInfo { get; init; }

    private T1? Arg1 { get; init; }
    private bool Arg1Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> WithArg1(T1 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg1 = arg, Arg1Set = true };
    }

    private T2? Arg2 { get; init; }
    private bool Arg2Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> WithArg2(T2 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg2 = arg, Arg2Set = true };
    }

    private T3? Arg3 { get; init; }
    private bool Arg3Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> WithArg3(T3 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg3 = arg, Arg3Set = true };
    }

    private T4? Arg4 { get; init; }
    private bool Arg4Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> WithArg4(T4 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg4 = arg, Arg4Set = true };
    }

    private T5? Arg5 { get; init; }
    private bool Arg5Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> WithArg5(T5 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg5 = arg, Arg5Set = true };
    }

    private T6? Arg6 { get; init; }
    private bool Arg6Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> WithArg6(T6 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg6 = arg, Arg6Set = true };
    }

    private T7? Arg7 { get; init; }
    private bool Arg7Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> WithArg7(T7 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg7 = arg, Arg7Set = true };
    }

    private T8? Arg8 { get; init; }
    private bool Arg8Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> WithArg8(T8 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg8 = arg, Arg8Set = true };
    }

    private T9? Arg9 { get; init; }
    private bool Arg9Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> WithArg9(T9 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg9 = arg, Arg9Set = true };
    }

    private T10? Arg10 { get; init; }
    private bool Arg10Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> WithArg10(T10 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg10 = arg, Arg10Set = true };
    }

    private T11? Arg11 { get; init; }
    private bool Arg11Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> WithArg11(T11 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg11 = arg, Arg11Set = true };
    }

    private T12? Arg12 { get; init; }
    private bool Arg12Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> WithArg12(T12 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg12 = arg, Arg12Set = true };
    }

    private T13? Arg13 { get; init; }
    private bool Arg13Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> WithArg13(T13 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg13 = arg, Arg13Set = true };
    }

    private T14? Arg14 { get; init; }
    private bool Arg14Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> WithArg14(T14 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg14 = arg, Arg14Set = true };
    }

    Task<Value> IGlideCommand.ExecuteAsync(IGlideClient client, CancellationToken cancellationToken = default)
    {
        if (!Arg1Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 0));
        if (!Arg2Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 1));
        if (!Arg3Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 2));
        if (!Arg4Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 3));
        if (!Arg5Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 4));
        if (!Arg6Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 5));
        if (!Arg7Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 6));
        if (!Arg8Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 7));
        if (!Arg9Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 8));
        if (!Arg10Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 9));
        if (!Arg11Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 10));
        if (!Arg12Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 11));
        if (!Arg13Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 12));
        if (!Arg14Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 13));
        return client.SendCommandAsync(ERequestType.CustomCommand, RoutingInfo, client.ToParameter(Arg1), client.ToParameter(Arg2), client.ToParameter(Arg3), client.ToParameter(Arg4), client.ToParameter(Arg5), client.ToParameter(Arg6), client.ToParameter(Arg7), client.ToParameter(Arg8), client.ToParameter(Arg9), client.ToParameter(Arg10), client.ToParameter(Arg11), client.ToParameter(Arg12), client.ToParameter(Arg13), client.ToParameter(Arg14));
    }
}

/// <summary>
/// Provides factory methods for creating instances of the CustomCommand structures.
/// </summary>
/// <remarks>
/// This static class enables the creation of CustomCommand instances with varying numbers of generic arguments,
/// facilitating a fluent and type-safe approach to defining and executing commands within the Glide framework.
/// </remarks
public static partial class CustomCommand
{
    /// <summary>
    /// Creates a new instance of the <see cref="CustomCommand{T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15}"/> structure with the specified argument.
    /// </summary>
    /// <typeparam name="T1">The type of the first argument for the command.</typeparam>
    /// <param name="Arg1">The value of the first argument to be passed to the command.</param>
    /// <typeparam name="T2">The type of the second argument for the command.</typeparam>
    /// <param name="Arg2">The value of the second argument to be passed to the command.</param>
    /// <typeparam name="T3">The type of the third argument for the command.</typeparam>
    /// <param name="Arg3">The value of the third argument to be passed to the command.</param>
    /// <typeparam name="T4">The type of the fourth argument for the command.</typeparam>
    /// <param name="Arg4">The value of the fourth argument to be passed to the command.</param>
    /// <typeparam name="T5">The type of the fifth argument for the command.</typeparam>
    /// <param name="Arg5">The value of the fifth argument to be passed to the command.</param>
    /// <typeparam name="T6">The type of the sixth argument for the command.</typeparam>
    /// <param name="Arg6">The value of the sixth argument to be passed to the command.</param>
    /// <typeparam name="T7">The type of the seventh argument for the command.</typeparam>
    /// <param name="Arg7">The value of the seventh argument to be passed to the command.</param>
    /// <typeparam name="T8">The type of the eighth argument for the command.</typeparam>
    /// <param name="Arg8">The value of the eighth argument to be passed to the command.</param>
    /// <typeparam name="T9">The type of the ninth argument for the command.</typeparam>
    /// <param name="Arg9">The value of the ninth argument to be passed to the command.</param>
    /// <typeparam name="T10">The type of the tenth argument for the command.</typeparam>
    /// <param name="Arg10">The value of the tenth argument to be passed to the command.</param>
    /// <typeparam name="T11">The type of the eleventh argument for the command.</typeparam>
    /// <param name="Arg11">The value of the eleventh argument to be passed to the command.</param>
    /// <typeparam name="T12">The type of the twelfth argument for the command.</typeparam>
    /// <param name="Arg12">The value of the twelfth argument to be passed to the command.</param>
    /// <typeparam name="T13">The type of the thirteenth argument for the command.</typeparam>
    /// <param name="Arg13">The value of the thirteenth argument to be passed to the command.</param>
    /// <typeparam name="T14">The type of the fourteenth argument for the command.</typeparam>
    /// <param name="Arg14">The value of the fourteenth argument to be passed to the command.</param>
    /// <typeparam name="T15">The type of the fifteenth argument for the command.</typeparam>
    /// <param name="Arg15">The value of the fifteenth argument to be passed to the command.</param>
    /// <returns>An instance of <see cref="CustomCommand{T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15}"/> initialized with the provided argument.</returns>
    public static CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> Create<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15>(TRoutingInfo routingInfo, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11, T12 arg12, T13 arg13, T14 arg14, T15 arg15)
         where TRoutingInfo : IRoutingInfo
         => new CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15>{RoutingInfo = routingInfo}
                .WithArg1(arg1)
                .WithArg2(arg2)
                .WithArg3(arg3)
                .WithArg4(arg4)
                .WithArg5(arg5)
                .WithArg6(arg6)
                .WithArg7(arg7)
                .WithArg8(arg8)
                .WithArg9(arg9)
                .WithArg10(arg10)
                .WithArg11(arg11)
                .WithArg12(arg12)
                .WithArg13(arg13)
                .WithArg14(arg14)
                .WithArg15(arg15);
}

/// <summary>
/// Represents a customizable command that can accept a specified number
/// of generic arguments and implements the IGlideCommand interface.
/// </summary>
/// <typeparam name="T1">The type of the first argument for the command.</typeparam>
/// <typeparam name="T2">The type of the second argument for the command.</typeparam>
/// <typeparam name="T3">The type of the third argument for the command.</typeparam>
/// <typeparam name="T4">The type of the fourth argument for the command.</typeparam>
/// <typeparam name="T5">The type of the fifth argument for the command.</typeparam>
/// <typeparam name="T6">The type of the sixth argument for the command.</typeparam>
/// <typeparam name="T7">The type of the seventh argument for the command.</typeparam>
/// <typeparam name="T8">The type of the eighth argument for the command.</typeparam>
/// <typeparam name="T9">The type of the ninth argument for the command.</typeparam>
/// <typeparam name="T10">The type of the tenth argument for the command.</typeparam>
/// <typeparam name="T11">The type of the eleventh argument for the command.</typeparam>
/// <typeparam name="T12">The type of the twelfth argument for the command.</typeparam>
/// <typeparam name="T13">The type of the thirteenth argument for the command.</typeparam>
/// <typeparam name="T14">The type of the fourteenth argument for the command.</typeparam>
/// <typeparam name="T15">The type of the fifteenth argument for the command.</typeparam>
/// <remarks>
/// <list type="bullet">
/// <item>
/// Each instance of the CustomCommand struct is immutable, and the usage of `WithArg` methods ensures
/// a fluent API for adding argument values while preserving immutability. It integrates with the IGlideCommand
/// interface for executing tasks asynchronously within the Glide framework.
/// </item>
/// <item>All values are handled as "values". If you want to issue command text, use the <see cref="Data.CommandText"/> struct</item>
/// </list>
/// </remarks>
public readonly struct CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> : IGlideCommand where TRoutingInfo : IRoutingInfo
{
    public required TRoutingInfo RoutingInfo { get; init; }

    private T1? Arg1 { get; init; }
    private bool Arg1Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> WithArg1(T1 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg1 = arg, Arg1Set = true };
    }

    private T2? Arg2 { get; init; }
    private bool Arg2Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> WithArg2(T2 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg2 = arg, Arg2Set = true };
    }

    private T3? Arg3 { get; init; }
    private bool Arg3Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> WithArg3(T3 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg3 = arg, Arg3Set = true };
    }

    private T4? Arg4 { get; init; }
    private bool Arg4Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> WithArg4(T4 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg4 = arg, Arg4Set = true };
    }

    private T5? Arg5 { get; init; }
    private bool Arg5Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> WithArg5(T5 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg5 = arg, Arg5Set = true };
    }

    private T6? Arg6 { get; init; }
    private bool Arg6Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> WithArg6(T6 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg6 = arg, Arg6Set = true };
    }

    private T7? Arg7 { get; init; }
    private bool Arg7Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> WithArg7(T7 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg7 = arg, Arg7Set = true };
    }

    private T8? Arg8 { get; init; }
    private bool Arg8Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> WithArg8(T8 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg8 = arg, Arg8Set = true };
    }

    private T9? Arg9 { get; init; }
    private bool Arg9Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> WithArg9(T9 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg9 = arg, Arg9Set = true };
    }

    private T10? Arg10 { get; init; }
    private bool Arg10Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> WithArg10(T10 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg10 = arg, Arg10Set = true };
    }

    private T11? Arg11 { get; init; }
    private bool Arg11Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> WithArg11(T11 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg11 = arg, Arg11Set = true };
    }

    private T12? Arg12 { get; init; }
    private bool Arg12Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> WithArg12(T12 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg12 = arg, Arg12Set = true };
    }

    private T13? Arg13 { get; init; }
    private bool Arg13Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> WithArg13(T13 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg13 = arg, Arg13Set = true };
    }

    private T14? Arg14 { get; init; }
    private bool Arg14Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> WithArg14(T14 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg14 = arg, Arg14Set = true };
    }

    private T15? Arg15 { get; init; }
    private bool Arg15Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> WithArg15(T15 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg15 = arg, Arg15Set = true };
    }

    Task<Value> IGlideCommand.ExecuteAsync(IGlideClient client, CancellationToken cancellationToken = default)
    {
        if (!Arg1Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 0));
        if (!Arg2Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 1));
        if (!Arg3Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 2));
        if (!Arg4Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 3));
        if (!Arg5Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 4));
        if (!Arg6Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 5));
        if (!Arg7Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 6));
        if (!Arg8Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 7));
        if (!Arg9Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 8));
        if (!Arg10Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 9));
        if (!Arg11Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 10));
        if (!Arg12Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 11));
        if (!Arg13Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 12));
        if (!Arg14Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 13));
        if (!Arg15Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 14));
        return client.SendCommandAsync(ERequestType.CustomCommand, RoutingInfo, client.ToParameter(Arg1), client.ToParameter(Arg2), client.ToParameter(Arg3), client.ToParameter(Arg4), client.ToParameter(Arg5), client.ToParameter(Arg6), client.ToParameter(Arg7), client.ToParameter(Arg8), client.ToParameter(Arg9), client.ToParameter(Arg10), client.ToParameter(Arg11), client.ToParameter(Arg12), client.ToParameter(Arg13), client.ToParameter(Arg14), client.ToParameter(Arg15));
    }
}

/// <summary>
/// Provides factory methods for creating instances of the CustomCommand structures.
/// </summary>
/// <remarks>
/// This static class enables the creation of CustomCommand instances with varying numbers of generic arguments,
/// facilitating a fluent and type-safe approach to defining and executing commands within the Glide framework.
/// </remarks
public static partial class CustomCommand
{
    /// <summary>
    /// Creates a new instance of the <see cref="CustomCommand{T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16}"/> structure with the specified argument.
    /// </summary>
    /// <typeparam name="T1">The type of the first argument for the command.</typeparam>
    /// <param name="Arg1">The value of the first argument to be passed to the command.</param>
    /// <typeparam name="T2">The type of the second argument for the command.</typeparam>
    /// <param name="Arg2">The value of the second argument to be passed to the command.</param>
    /// <typeparam name="T3">The type of the third argument for the command.</typeparam>
    /// <param name="Arg3">The value of the third argument to be passed to the command.</param>
    /// <typeparam name="T4">The type of the fourth argument for the command.</typeparam>
    /// <param name="Arg4">The value of the fourth argument to be passed to the command.</param>
    /// <typeparam name="T5">The type of the fifth argument for the command.</typeparam>
    /// <param name="Arg5">The value of the fifth argument to be passed to the command.</param>
    /// <typeparam name="T6">The type of the sixth argument for the command.</typeparam>
    /// <param name="Arg6">The value of the sixth argument to be passed to the command.</param>
    /// <typeparam name="T7">The type of the seventh argument for the command.</typeparam>
    /// <param name="Arg7">The value of the seventh argument to be passed to the command.</param>
    /// <typeparam name="T8">The type of the eighth argument for the command.</typeparam>
    /// <param name="Arg8">The value of the eighth argument to be passed to the command.</param>
    /// <typeparam name="T9">The type of the ninth argument for the command.</typeparam>
    /// <param name="Arg9">The value of the ninth argument to be passed to the command.</param>
    /// <typeparam name="T10">The type of the tenth argument for the command.</typeparam>
    /// <param name="Arg10">The value of the tenth argument to be passed to the command.</param>
    /// <typeparam name="T11">The type of the eleventh argument for the command.</typeparam>
    /// <param name="Arg11">The value of the eleventh argument to be passed to the command.</param>
    /// <typeparam name="T12">The type of the twelfth argument for the command.</typeparam>
    /// <param name="Arg12">The value of the twelfth argument to be passed to the command.</param>
    /// <typeparam name="T13">The type of the thirteenth argument for the command.</typeparam>
    /// <param name="Arg13">The value of the thirteenth argument to be passed to the command.</param>
    /// <typeparam name="T14">The type of the fourteenth argument for the command.</typeparam>
    /// <param name="Arg14">The value of the fourteenth argument to be passed to the command.</param>
    /// <typeparam name="T15">The type of the fifteenth argument for the command.</typeparam>
    /// <param name="Arg15">The value of the fifteenth argument to be passed to the command.</param>
    /// <typeparam name="T16">The type of the sixteenth argument for the command.</typeparam>
    /// <param name="Arg16">The value of the sixteenth argument to be passed to the command.</param>
    /// <returns>An instance of <see cref="CustomCommand{T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16}"/> initialized with the provided argument.</returns>
    public static CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> Create<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16>(TRoutingInfo routingInfo, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11, T12 arg12, T13 arg13, T14 arg14, T15 arg15, T16 arg16)
         where TRoutingInfo : IRoutingInfo
         => new CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16>{RoutingInfo = routingInfo}
                .WithArg1(arg1)
                .WithArg2(arg2)
                .WithArg3(arg3)
                .WithArg4(arg4)
                .WithArg5(arg5)
                .WithArg6(arg6)
                .WithArg7(arg7)
                .WithArg8(arg8)
                .WithArg9(arg9)
                .WithArg10(arg10)
                .WithArg11(arg11)
                .WithArg12(arg12)
                .WithArg13(arg13)
                .WithArg14(arg14)
                .WithArg15(arg15)
                .WithArg16(arg16);
}

/// <summary>
/// Represents a customizable command that can accept a specified number
/// of generic arguments and implements the IGlideCommand interface.
/// </summary>
/// <typeparam name="T1">The type of the first argument for the command.</typeparam>
/// <typeparam name="T2">The type of the second argument for the command.</typeparam>
/// <typeparam name="T3">The type of the third argument for the command.</typeparam>
/// <typeparam name="T4">The type of the fourth argument for the command.</typeparam>
/// <typeparam name="T5">The type of the fifth argument for the command.</typeparam>
/// <typeparam name="T6">The type of the sixth argument for the command.</typeparam>
/// <typeparam name="T7">The type of the seventh argument for the command.</typeparam>
/// <typeparam name="T8">The type of the eighth argument for the command.</typeparam>
/// <typeparam name="T9">The type of the ninth argument for the command.</typeparam>
/// <typeparam name="T10">The type of the tenth argument for the command.</typeparam>
/// <typeparam name="T11">The type of the eleventh argument for the command.</typeparam>
/// <typeparam name="T12">The type of the twelfth argument for the command.</typeparam>
/// <typeparam name="T13">The type of the thirteenth argument for the command.</typeparam>
/// <typeparam name="T14">The type of the fourteenth argument for the command.</typeparam>
/// <typeparam name="T15">The type of the fifteenth argument for the command.</typeparam>
/// <typeparam name="T16">The type of the sixteenth argument for the command.</typeparam>
/// <remarks>
/// <list type="bullet">
/// <item>
/// Each instance of the CustomCommand struct is immutable, and the usage of `WithArg` methods ensures
/// a fluent API for adding argument values while preserving immutability. It integrates with the IGlideCommand
/// interface for executing tasks asynchronously within the Glide framework.
/// </item>
/// <item>All values are handled as "values". If you want to issue command text, use the <see cref="Data.CommandText"/> struct</item>
/// </list>
/// </remarks>
public readonly struct CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> : IGlideCommand where TRoutingInfo : IRoutingInfo
{
    public required TRoutingInfo RoutingInfo { get; init; }

    private T1? Arg1 { get; init; }
    private bool Arg1Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> WithArg1(T1 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg1 = arg, Arg1Set = true };
    }

    private T2? Arg2 { get; init; }
    private bool Arg2Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> WithArg2(T2 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg2 = arg, Arg2Set = true };
    }

    private T3? Arg3 { get; init; }
    private bool Arg3Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> WithArg3(T3 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg3 = arg, Arg3Set = true };
    }

    private T4? Arg4 { get; init; }
    private bool Arg4Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> WithArg4(T4 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg4 = arg, Arg4Set = true };
    }

    private T5? Arg5 { get; init; }
    private bool Arg5Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> WithArg5(T5 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg5 = arg, Arg5Set = true };
    }

    private T6? Arg6 { get; init; }
    private bool Arg6Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> WithArg6(T6 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg6 = arg, Arg6Set = true };
    }

    private T7? Arg7 { get; init; }
    private bool Arg7Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> WithArg7(T7 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg7 = arg, Arg7Set = true };
    }

    private T8? Arg8 { get; init; }
    private bool Arg8Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> WithArg8(T8 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg8 = arg, Arg8Set = true };
    }

    private T9? Arg9 { get; init; }
    private bool Arg9Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> WithArg9(T9 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg9 = arg, Arg9Set = true };
    }

    private T10? Arg10 { get; init; }
    private bool Arg10Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> WithArg10(T10 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg10 = arg, Arg10Set = true };
    }

    private T11? Arg11 { get; init; }
    private bool Arg11Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> WithArg11(T11 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg11 = arg, Arg11Set = true };
    }

    private T12? Arg12 { get; init; }
    private bool Arg12Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> WithArg12(T12 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg12 = arg, Arg12Set = true };
    }

    private T13? Arg13 { get; init; }
    private bool Arg13Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> WithArg13(T13 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg13 = arg, Arg13Set = true };
    }

    private T14? Arg14 { get; init; }
    private bool Arg14Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> WithArg14(T14 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg14 = arg, Arg14Set = true };
    }

    private T15? Arg15 { get; init; }
    private bool Arg15Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> WithArg15(T15 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg15 = arg, Arg15Set = true };
    }

    private T16? Arg16 { get; init; }
    private bool Arg16Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> WithArg16(T16 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg16 = arg, Arg16Set = true };
    }

    Task<Value> IGlideCommand.ExecuteAsync(IGlideClient client, CancellationToken cancellationToken = default)
    {
        if (!Arg1Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 0));
        if (!Arg2Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 1));
        if (!Arg3Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 2));
        if (!Arg4Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 3));
        if (!Arg5Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 4));
        if (!Arg6Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 5));
        if (!Arg7Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 6));
        if (!Arg8Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 7));
        if (!Arg9Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 8));
        if (!Arg10Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 9));
        if (!Arg11Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 10));
        if (!Arg12Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 11));
        if (!Arg13Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 12));
        if (!Arg14Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 13));
        if (!Arg15Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 14));
        if (!Arg16Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 15));
        return client.SendCommandAsync(ERequestType.CustomCommand, RoutingInfo, client.ToParameter(Arg1), client.ToParameter(Arg2), client.ToParameter(Arg3), client.ToParameter(Arg4), client.ToParameter(Arg5), client.ToParameter(Arg6), client.ToParameter(Arg7), client.ToParameter(Arg8), client.ToParameter(Arg9), client.ToParameter(Arg10), client.ToParameter(Arg11), client.ToParameter(Arg12), client.ToParameter(Arg13), client.ToParameter(Arg14), client.ToParameter(Arg15), client.ToParameter(Arg16));
    }
}

/// <summary>
/// Provides factory methods for creating instances of the CustomCommand structures.
/// </summary>
/// <remarks>
/// This static class enables the creation of CustomCommand instances with varying numbers of generic arguments,
/// facilitating a fluent and type-safe approach to defining and executing commands within the Glide framework.
/// </remarks
public static partial class CustomCommand
{
    /// <summary>
    /// Creates a new instance of the <see cref="CustomCommand{T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17}"/> structure with the specified argument.
    /// </summary>
    /// <typeparam name="T1">The type of the first argument for the command.</typeparam>
    /// <param name="Arg1">The value of the first argument to be passed to the command.</param>
    /// <typeparam name="T2">The type of the second argument for the command.</typeparam>
    /// <param name="Arg2">The value of the second argument to be passed to the command.</param>
    /// <typeparam name="T3">The type of the third argument for the command.</typeparam>
    /// <param name="Arg3">The value of the third argument to be passed to the command.</param>
    /// <typeparam name="T4">The type of the fourth argument for the command.</typeparam>
    /// <param name="Arg4">The value of the fourth argument to be passed to the command.</param>
    /// <typeparam name="T5">The type of the fifth argument for the command.</typeparam>
    /// <param name="Arg5">The value of the fifth argument to be passed to the command.</param>
    /// <typeparam name="T6">The type of the sixth argument for the command.</typeparam>
    /// <param name="Arg6">The value of the sixth argument to be passed to the command.</param>
    /// <typeparam name="T7">The type of the seventh argument for the command.</typeparam>
    /// <param name="Arg7">The value of the seventh argument to be passed to the command.</param>
    /// <typeparam name="T8">The type of the eighth argument for the command.</typeparam>
    /// <param name="Arg8">The value of the eighth argument to be passed to the command.</param>
    /// <typeparam name="T9">The type of the ninth argument for the command.</typeparam>
    /// <param name="Arg9">The value of the ninth argument to be passed to the command.</param>
    /// <typeparam name="T10">The type of the tenth argument for the command.</typeparam>
    /// <param name="Arg10">The value of the tenth argument to be passed to the command.</param>
    /// <typeparam name="T11">The type of the eleventh argument for the command.</typeparam>
    /// <param name="Arg11">The value of the eleventh argument to be passed to the command.</param>
    /// <typeparam name="T12">The type of the twelfth argument for the command.</typeparam>
    /// <param name="Arg12">The value of the twelfth argument to be passed to the command.</param>
    /// <typeparam name="T13">The type of the thirteenth argument for the command.</typeparam>
    /// <param name="Arg13">The value of the thirteenth argument to be passed to the command.</param>
    /// <typeparam name="T14">The type of the fourteenth argument for the command.</typeparam>
    /// <param name="Arg14">The value of the fourteenth argument to be passed to the command.</param>
    /// <typeparam name="T15">The type of the fifteenth argument for the command.</typeparam>
    /// <param name="Arg15">The value of the fifteenth argument to be passed to the command.</param>
    /// <typeparam name="T16">The type of the sixteenth argument for the command.</typeparam>
    /// <param name="Arg16">The value of the sixteenth argument to be passed to the command.</param>
    /// <typeparam name="T17">The type of the seventeenth argument for the command.</typeparam>
    /// <param name="Arg17">The value of the seventeenth argument to be passed to the command.</param>
    /// <returns>An instance of <see cref="CustomCommand{T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17}"/> initialized with the provided argument.</returns>
    public static CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> Create<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17>(TRoutingInfo routingInfo, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11, T12 arg12, T13 arg13, T14 arg14, T15 arg15, T16 arg16, T17 arg17)
         where TRoutingInfo : IRoutingInfo
         => new CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17>{RoutingInfo = routingInfo}
                .WithArg1(arg1)
                .WithArg2(arg2)
                .WithArg3(arg3)
                .WithArg4(arg4)
                .WithArg5(arg5)
                .WithArg6(arg6)
                .WithArg7(arg7)
                .WithArg8(arg8)
                .WithArg9(arg9)
                .WithArg10(arg10)
                .WithArg11(arg11)
                .WithArg12(arg12)
                .WithArg13(arg13)
                .WithArg14(arg14)
                .WithArg15(arg15)
                .WithArg16(arg16)
                .WithArg17(arg17);
}

/// <summary>
/// Represents a customizable command that can accept a specified number
/// of generic arguments and implements the IGlideCommand interface.
/// </summary>
/// <typeparam name="T1">The type of the first argument for the command.</typeparam>
/// <typeparam name="T2">The type of the second argument for the command.</typeparam>
/// <typeparam name="T3">The type of the third argument for the command.</typeparam>
/// <typeparam name="T4">The type of the fourth argument for the command.</typeparam>
/// <typeparam name="T5">The type of the fifth argument for the command.</typeparam>
/// <typeparam name="T6">The type of the sixth argument for the command.</typeparam>
/// <typeparam name="T7">The type of the seventh argument for the command.</typeparam>
/// <typeparam name="T8">The type of the eighth argument for the command.</typeparam>
/// <typeparam name="T9">The type of the ninth argument for the command.</typeparam>
/// <typeparam name="T10">The type of the tenth argument for the command.</typeparam>
/// <typeparam name="T11">The type of the eleventh argument for the command.</typeparam>
/// <typeparam name="T12">The type of the twelfth argument for the command.</typeparam>
/// <typeparam name="T13">The type of the thirteenth argument for the command.</typeparam>
/// <typeparam name="T14">The type of the fourteenth argument for the command.</typeparam>
/// <typeparam name="T15">The type of the fifteenth argument for the command.</typeparam>
/// <typeparam name="T16">The type of the sixteenth argument for the command.</typeparam>
/// <typeparam name="T17">The type of the seventeenth argument for the command.</typeparam>
/// <remarks>
/// <list type="bullet">
/// <item>
/// Each instance of the CustomCommand struct is immutable, and the usage of `WithArg` methods ensures
/// a fluent API for adding argument values while preserving immutability. It integrates with the IGlideCommand
/// interface for executing tasks asynchronously within the Glide framework.
/// </item>
/// <item>All values are handled as "values". If you want to issue command text, use the <see cref="Data.CommandText"/> struct</item>
/// </list>
/// </remarks>
public readonly struct CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> : IGlideCommand where TRoutingInfo : IRoutingInfo
{
    public required TRoutingInfo RoutingInfo { get; init; }

    private T1? Arg1 { get; init; }
    private bool Arg1Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> WithArg1(T1 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg1 = arg, Arg1Set = true };
    }

    private T2? Arg2 { get; init; }
    private bool Arg2Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> WithArg2(T2 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg2 = arg, Arg2Set = true };
    }

    private T3? Arg3 { get; init; }
    private bool Arg3Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> WithArg3(T3 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg3 = arg, Arg3Set = true };
    }

    private T4? Arg4 { get; init; }
    private bool Arg4Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> WithArg4(T4 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg4 = arg, Arg4Set = true };
    }

    private T5? Arg5 { get; init; }
    private bool Arg5Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> WithArg5(T5 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg5 = arg, Arg5Set = true };
    }

    private T6? Arg6 { get; init; }
    private bool Arg6Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> WithArg6(T6 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg6 = arg, Arg6Set = true };
    }

    private T7? Arg7 { get; init; }
    private bool Arg7Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> WithArg7(T7 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg7 = arg, Arg7Set = true };
    }

    private T8? Arg8 { get; init; }
    private bool Arg8Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> WithArg8(T8 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg8 = arg, Arg8Set = true };
    }

    private T9? Arg9 { get; init; }
    private bool Arg9Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> WithArg9(T9 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg9 = arg, Arg9Set = true };
    }

    private T10? Arg10 { get; init; }
    private bool Arg10Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> WithArg10(T10 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg10 = arg, Arg10Set = true };
    }

    private T11? Arg11 { get; init; }
    private bool Arg11Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> WithArg11(T11 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg11 = arg, Arg11Set = true };
    }

    private T12? Arg12 { get; init; }
    private bool Arg12Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> WithArg12(T12 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg12 = arg, Arg12Set = true };
    }

    private T13? Arg13 { get; init; }
    private bool Arg13Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> WithArg13(T13 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg13 = arg, Arg13Set = true };
    }

    private T14? Arg14 { get; init; }
    private bool Arg14Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> WithArg14(T14 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg14 = arg, Arg14Set = true };
    }

    private T15? Arg15 { get; init; }
    private bool Arg15Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> WithArg15(T15 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg15 = arg, Arg15Set = true };
    }

    private T16? Arg16 { get; init; }
    private bool Arg16Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> WithArg16(T16 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg16 = arg, Arg16Set = true };
    }

    private T17? Arg17 { get; init; }
    private bool Arg17Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17> WithArg17(T17 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg17 = arg, Arg17Set = true };
    }

    Task<Value> IGlideCommand.ExecuteAsync(IGlideClient client, CancellationToken cancellationToken = default)
    {
        if (!Arg1Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 0));
        if (!Arg2Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 1));
        if (!Arg3Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 2));
        if (!Arg4Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 3));
        if (!Arg5Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 4));
        if (!Arg6Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 5));
        if (!Arg7Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 6));
        if (!Arg8Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 7));
        if (!Arg9Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 8));
        if (!Arg10Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 9));
        if (!Arg11Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 10));
        if (!Arg12Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 11));
        if (!Arg13Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 12));
        if (!Arg14Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 13));
        if (!Arg15Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 14));
        if (!Arg16Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 15));
        if (!Arg17Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 16));
        return client.SendCommandAsync(ERequestType.CustomCommand, RoutingInfo, client.ToParameter(Arg1), client.ToParameter(Arg2), client.ToParameter(Arg3), client.ToParameter(Arg4), client.ToParameter(Arg5), client.ToParameter(Arg6), client.ToParameter(Arg7), client.ToParameter(Arg8), client.ToParameter(Arg9), client.ToParameter(Arg10), client.ToParameter(Arg11), client.ToParameter(Arg12), client.ToParameter(Arg13), client.ToParameter(Arg14), client.ToParameter(Arg15), client.ToParameter(Arg16), client.ToParameter(Arg17));
    }
}

/// <summary>
/// Provides factory methods for creating instances of the CustomCommand structures.
/// </summary>
/// <remarks>
/// This static class enables the creation of CustomCommand instances with varying numbers of generic arguments,
/// facilitating a fluent and type-safe approach to defining and executing commands within the Glide framework.
/// </remarks
public static partial class CustomCommand
{
    /// <summary>
    /// Creates a new instance of the <see cref="CustomCommand{T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18}"/> structure with the specified argument.
    /// </summary>
    /// <typeparam name="T1">The type of the first argument for the command.</typeparam>
    /// <param name="Arg1">The value of the first argument to be passed to the command.</param>
    /// <typeparam name="T2">The type of the second argument for the command.</typeparam>
    /// <param name="Arg2">The value of the second argument to be passed to the command.</param>
    /// <typeparam name="T3">The type of the third argument for the command.</typeparam>
    /// <param name="Arg3">The value of the third argument to be passed to the command.</param>
    /// <typeparam name="T4">The type of the fourth argument for the command.</typeparam>
    /// <param name="Arg4">The value of the fourth argument to be passed to the command.</param>
    /// <typeparam name="T5">The type of the fifth argument for the command.</typeparam>
    /// <param name="Arg5">The value of the fifth argument to be passed to the command.</param>
    /// <typeparam name="T6">The type of the sixth argument for the command.</typeparam>
    /// <param name="Arg6">The value of the sixth argument to be passed to the command.</param>
    /// <typeparam name="T7">The type of the seventh argument for the command.</typeparam>
    /// <param name="Arg7">The value of the seventh argument to be passed to the command.</param>
    /// <typeparam name="T8">The type of the eighth argument for the command.</typeparam>
    /// <param name="Arg8">The value of the eighth argument to be passed to the command.</param>
    /// <typeparam name="T9">The type of the ninth argument for the command.</typeparam>
    /// <param name="Arg9">The value of the ninth argument to be passed to the command.</param>
    /// <typeparam name="T10">The type of the tenth argument for the command.</typeparam>
    /// <param name="Arg10">The value of the tenth argument to be passed to the command.</param>
    /// <typeparam name="T11">The type of the eleventh argument for the command.</typeparam>
    /// <param name="Arg11">The value of the eleventh argument to be passed to the command.</param>
    /// <typeparam name="T12">The type of the twelfth argument for the command.</typeparam>
    /// <param name="Arg12">The value of the twelfth argument to be passed to the command.</param>
    /// <typeparam name="T13">The type of the thirteenth argument for the command.</typeparam>
    /// <param name="Arg13">The value of the thirteenth argument to be passed to the command.</param>
    /// <typeparam name="T14">The type of the fourteenth argument for the command.</typeparam>
    /// <param name="Arg14">The value of the fourteenth argument to be passed to the command.</param>
    /// <typeparam name="T15">The type of the fifteenth argument for the command.</typeparam>
    /// <param name="Arg15">The value of the fifteenth argument to be passed to the command.</param>
    /// <typeparam name="T16">The type of the sixteenth argument for the command.</typeparam>
    /// <param name="Arg16">The value of the sixteenth argument to be passed to the command.</param>
    /// <typeparam name="T17">The type of the seventeenth argument for the command.</typeparam>
    /// <param name="Arg17">The value of the seventeenth argument to be passed to the command.</param>
    /// <typeparam name="T18">The type of the eighteenth argument for the command.</typeparam>
    /// <param name="Arg18">The value of the eighteenth argument to be passed to the command.</param>
    /// <returns>An instance of <see cref="CustomCommand{T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18}"/> initialized with the provided argument.</returns>
    public static CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> Create<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18>(TRoutingInfo routingInfo, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11, T12 arg12, T13 arg13, T14 arg14, T15 arg15, T16 arg16, T17 arg17, T18 arg18)
         where TRoutingInfo : IRoutingInfo
         => new CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18>{RoutingInfo = routingInfo}
                .WithArg1(arg1)
                .WithArg2(arg2)
                .WithArg3(arg3)
                .WithArg4(arg4)
                .WithArg5(arg5)
                .WithArg6(arg6)
                .WithArg7(arg7)
                .WithArg8(arg8)
                .WithArg9(arg9)
                .WithArg10(arg10)
                .WithArg11(arg11)
                .WithArg12(arg12)
                .WithArg13(arg13)
                .WithArg14(arg14)
                .WithArg15(arg15)
                .WithArg16(arg16)
                .WithArg17(arg17)
                .WithArg18(arg18);
}

/// <summary>
/// Represents a customizable command that can accept a specified number
/// of generic arguments and implements the IGlideCommand interface.
/// </summary>
/// <typeparam name="T1">The type of the first argument for the command.</typeparam>
/// <typeparam name="T2">The type of the second argument for the command.</typeparam>
/// <typeparam name="T3">The type of the third argument for the command.</typeparam>
/// <typeparam name="T4">The type of the fourth argument for the command.</typeparam>
/// <typeparam name="T5">The type of the fifth argument for the command.</typeparam>
/// <typeparam name="T6">The type of the sixth argument for the command.</typeparam>
/// <typeparam name="T7">The type of the seventh argument for the command.</typeparam>
/// <typeparam name="T8">The type of the eighth argument for the command.</typeparam>
/// <typeparam name="T9">The type of the ninth argument for the command.</typeparam>
/// <typeparam name="T10">The type of the tenth argument for the command.</typeparam>
/// <typeparam name="T11">The type of the eleventh argument for the command.</typeparam>
/// <typeparam name="T12">The type of the twelfth argument for the command.</typeparam>
/// <typeparam name="T13">The type of the thirteenth argument for the command.</typeparam>
/// <typeparam name="T14">The type of the fourteenth argument for the command.</typeparam>
/// <typeparam name="T15">The type of the fifteenth argument for the command.</typeparam>
/// <typeparam name="T16">The type of the sixteenth argument for the command.</typeparam>
/// <typeparam name="T17">The type of the seventeenth argument for the command.</typeparam>
/// <typeparam name="T18">The type of the eighteenth argument for the command.</typeparam>
/// <remarks>
/// <list type="bullet">
/// <item>
/// Each instance of the CustomCommand struct is immutable, and the usage of `WithArg` methods ensures
/// a fluent API for adding argument values while preserving immutability. It integrates with the IGlideCommand
/// interface for executing tasks asynchronously within the Glide framework.
/// </item>
/// <item>All values are handled as "values". If you want to issue command text, use the <see cref="Data.CommandText"/> struct</item>
/// </list>
/// </remarks>
public readonly struct CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> : IGlideCommand where TRoutingInfo : IRoutingInfo
{
    public required TRoutingInfo RoutingInfo { get; init; }

    private T1? Arg1 { get; init; }
    private bool Arg1Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> WithArg1(T1 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg1 = arg, Arg1Set = true };
    }

    private T2? Arg2 { get; init; }
    private bool Arg2Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> WithArg2(T2 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg2 = arg, Arg2Set = true };
    }

    private T3? Arg3 { get; init; }
    private bool Arg3Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> WithArg3(T3 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg3 = arg, Arg3Set = true };
    }

    private T4? Arg4 { get; init; }
    private bool Arg4Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> WithArg4(T4 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg4 = arg, Arg4Set = true };
    }

    private T5? Arg5 { get; init; }
    private bool Arg5Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> WithArg5(T5 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg5 = arg, Arg5Set = true };
    }

    private T6? Arg6 { get; init; }
    private bool Arg6Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> WithArg6(T6 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg6 = arg, Arg6Set = true };
    }

    private T7? Arg7 { get; init; }
    private bool Arg7Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> WithArg7(T7 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg7 = arg, Arg7Set = true };
    }

    private T8? Arg8 { get; init; }
    private bool Arg8Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> WithArg8(T8 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg8 = arg, Arg8Set = true };
    }

    private T9? Arg9 { get; init; }
    private bool Arg9Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> WithArg9(T9 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg9 = arg, Arg9Set = true };
    }

    private T10? Arg10 { get; init; }
    private bool Arg10Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> WithArg10(T10 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg10 = arg, Arg10Set = true };
    }

    private T11? Arg11 { get; init; }
    private bool Arg11Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> WithArg11(T11 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg11 = arg, Arg11Set = true };
    }

    private T12? Arg12 { get; init; }
    private bool Arg12Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> WithArg12(T12 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg12 = arg, Arg12Set = true };
    }

    private T13? Arg13 { get; init; }
    private bool Arg13Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> WithArg13(T13 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg13 = arg, Arg13Set = true };
    }

    private T14? Arg14 { get; init; }
    private bool Arg14Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> WithArg14(T14 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg14 = arg, Arg14Set = true };
    }

    private T15? Arg15 { get; init; }
    private bool Arg15Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> WithArg15(T15 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg15 = arg, Arg15Set = true };
    }

    private T16? Arg16 { get; init; }
    private bool Arg16Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> WithArg16(T16 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg16 = arg, Arg16Set = true };
    }

    private T17? Arg17 { get; init; }
    private bool Arg17Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> WithArg17(T17 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg17 = arg, Arg17Set = true };
    }

    private T18? Arg18 { get; init; }
    private bool Arg18Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18> WithArg18(T18 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg18 = arg, Arg18Set = true };
    }

    Task<Value> IGlideCommand.ExecuteAsync(IGlideClient client, CancellationToken cancellationToken = default)
    {
        if (!Arg1Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 0));
        if (!Arg2Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 1));
        if (!Arg3Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 2));
        if (!Arg4Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 3));
        if (!Arg5Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 4));
        if (!Arg6Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 5));
        if (!Arg7Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 6));
        if (!Arg8Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 7));
        if (!Arg9Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 8));
        if (!Arg10Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 9));
        if (!Arg11Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 10));
        if (!Arg12Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 11));
        if (!Arg13Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 12));
        if (!Arg14Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 13));
        if (!Arg15Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 14));
        if (!Arg16Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 15));
        if (!Arg17Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 16));
        if (!Arg18Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 17));
        return client.SendCommandAsync(ERequestType.CustomCommand, RoutingInfo, client.ToParameter(Arg1), client.ToParameter(Arg2), client.ToParameter(Arg3), client.ToParameter(Arg4), client.ToParameter(Arg5), client.ToParameter(Arg6), client.ToParameter(Arg7), client.ToParameter(Arg8), client.ToParameter(Arg9), client.ToParameter(Arg10), client.ToParameter(Arg11), client.ToParameter(Arg12), client.ToParameter(Arg13), client.ToParameter(Arg14), client.ToParameter(Arg15), client.ToParameter(Arg16), client.ToParameter(Arg17), client.ToParameter(Arg18));
    }
}

/// <summary>
/// Provides factory methods for creating instances of the CustomCommand structures.
/// </summary>
/// <remarks>
/// This static class enables the creation of CustomCommand instances with varying numbers of generic arguments,
/// facilitating a fluent and type-safe approach to defining and executing commands within the Glide framework.
/// </remarks
public static partial class CustomCommand
{
    /// <summary>
    /// Creates a new instance of the <see cref="CustomCommand{T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19}"/> structure with the specified argument.
    /// </summary>
    /// <typeparam name="T1">The type of the first argument for the command.</typeparam>
    /// <param name="Arg1">The value of the first argument to be passed to the command.</param>
    /// <typeparam name="T2">The type of the second argument for the command.</typeparam>
    /// <param name="Arg2">The value of the second argument to be passed to the command.</param>
    /// <typeparam name="T3">The type of the third argument for the command.</typeparam>
    /// <param name="Arg3">The value of the third argument to be passed to the command.</param>
    /// <typeparam name="T4">The type of the fourth argument for the command.</typeparam>
    /// <param name="Arg4">The value of the fourth argument to be passed to the command.</param>
    /// <typeparam name="T5">The type of the fifth argument for the command.</typeparam>
    /// <param name="Arg5">The value of the fifth argument to be passed to the command.</param>
    /// <typeparam name="T6">The type of the sixth argument for the command.</typeparam>
    /// <param name="Arg6">The value of the sixth argument to be passed to the command.</param>
    /// <typeparam name="T7">The type of the seventh argument for the command.</typeparam>
    /// <param name="Arg7">The value of the seventh argument to be passed to the command.</param>
    /// <typeparam name="T8">The type of the eighth argument for the command.</typeparam>
    /// <param name="Arg8">The value of the eighth argument to be passed to the command.</param>
    /// <typeparam name="T9">The type of the ninth argument for the command.</typeparam>
    /// <param name="Arg9">The value of the ninth argument to be passed to the command.</param>
    /// <typeparam name="T10">The type of the tenth argument for the command.</typeparam>
    /// <param name="Arg10">The value of the tenth argument to be passed to the command.</param>
    /// <typeparam name="T11">The type of the eleventh argument for the command.</typeparam>
    /// <param name="Arg11">The value of the eleventh argument to be passed to the command.</param>
    /// <typeparam name="T12">The type of the twelfth argument for the command.</typeparam>
    /// <param name="Arg12">The value of the twelfth argument to be passed to the command.</param>
    /// <typeparam name="T13">The type of the thirteenth argument for the command.</typeparam>
    /// <param name="Arg13">The value of the thirteenth argument to be passed to the command.</param>
    /// <typeparam name="T14">The type of the fourteenth argument for the command.</typeparam>
    /// <param name="Arg14">The value of the fourteenth argument to be passed to the command.</param>
    /// <typeparam name="T15">The type of the fifteenth argument for the command.</typeparam>
    /// <param name="Arg15">The value of the fifteenth argument to be passed to the command.</param>
    /// <typeparam name="T16">The type of the sixteenth argument for the command.</typeparam>
    /// <param name="Arg16">The value of the sixteenth argument to be passed to the command.</param>
    /// <typeparam name="T17">The type of the seventeenth argument for the command.</typeparam>
    /// <param name="Arg17">The value of the seventeenth argument to be passed to the command.</param>
    /// <typeparam name="T18">The type of the eighteenth argument for the command.</typeparam>
    /// <param name="Arg18">The value of the eighteenth argument to be passed to the command.</param>
    /// <typeparam name="T19">The type of the nineteenth argument for the command.</typeparam>
    /// <param name="Arg19">The value of the nineteenth argument to be passed to the command.</param>
    /// <returns>An instance of <see cref="CustomCommand{T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19}"/> initialized with the provided argument.</returns>
    public static CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> Create<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19>(TRoutingInfo routingInfo, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11, T12 arg12, T13 arg13, T14 arg14, T15 arg15, T16 arg16, T17 arg17, T18 arg18, T19 arg19)
         where TRoutingInfo : IRoutingInfo
         => new CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19>{RoutingInfo = routingInfo}
                .WithArg1(arg1)
                .WithArg2(arg2)
                .WithArg3(arg3)
                .WithArg4(arg4)
                .WithArg5(arg5)
                .WithArg6(arg6)
                .WithArg7(arg7)
                .WithArg8(arg8)
                .WithArg9(arg9)
                .WithArg10(arg10)
                .WithArg11(arg11)
                .WithArg12(arg12)
                .WithArg13(arg13)
                .WithArg14(arg14)
                .WithArg15(arg15)
                .WithArg16(arg16)
                .WithArg17(arg17)
                .WithArg18(arg18)
                .WithArg19(arg19);
}

/// <summary>
/// Represents a customizable command that can accept a specified number
/// of generic arguments and implements the IGlideCommand interface.
/// </summary>
/// <typeparam name="T1">The type of the first argument for the command.</typeparam>
/// <typeparam name="T2">The type of the second argument for the command.</typeparam>
/// <typeparam name="T3">The type of the third argument for the command.</typeparam>
/// <typeparam name="T4">The type of the fourth argument for the command.</typeparam>
/// <typeparam name="T5">The type of the fifth argument for the command.</typeparam>
/// <typeparam name="T6">The type of the sixth argument for the command.</typeparam>
/// <typeparam name="T7">The type of the seventh argument for the command.</typeparam>
/// <typeparam name="T8">The type of the eighth argument for the command.</typeparam>
/// <typeparam name="T9">The type of the ninth argument for the command.</typeparam>
/// <typeparam name="T10">The type of the tenth argument for the command.</typeparam>
/// <typeparam name="T11">The type of the eleventh argument for the command.</typeparam>
/// <typeparam name="T12">The type of the twelfth argument for the command.</typeparam>
/// <typeparam name="T13">The type of the thirteenth argument for the command.</typeparam>
/// <typeparam name="T14">The type of the fourteenth argument for the command.</typeparam>
/// <typeparam name="T15">The type of the fifteenth argument for the command.</typeparam>
/// <typeparam name="T16">The type of the sixteenth argument for the command.</typeparam>
/// <typeparam name="T17">The type of the seventeenth argument for the command.</typeparam>
/// <typeparam name="T18">The type of the eighteenth argument for the command.</typeparam>
/// <typeparam name="T19">The type of the nineteenth argument for the command.</typeparam>
/// <remarks>
/// <list type="bullet">
/// <item>
/// Each instance of the CustomCommand struct is immutable, and the usage of `WithArg` methods ensures
/// a fluent API for adding argument values while preserving immutability. It integrates with the IGlideCommand
/// interface for executing tasks asynchronously within the Glide framework.
/// </item>
/// <item>All values are handled as "values". If you want to issue command text, use the <see cref="Data.CommandText"/> struct</item>
/// </list>
/// </remarks>
public readonly struct CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> : IGlideCommand where TRoutingInfo : IRoutingInfo
{
    public required TRoutingInfo RoutingInfo { get; init; }

    private T1? Arg1 { get; init; }
    private bool Arg1Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> WithArg1(T1 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg1 = arg, Arg1Set = true };
    }

    private T2? Arg2 { get; init; }
    private bool Arg2Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> WithArg2(T2 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg2 = arg, Arg2Set = true };
    }

    private T3? Arg3 { get; init; }
    private bool Arg3Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> WithArg3(T3 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg3 = arg, Arg3Set = true };
    }

    private T4? Arg4 { get; init; }
    private bool Arg4Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> WithArg4(T4 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg4 = arg, Arg4Set = true };
    }

    private T5? Arg5 { get; init; }
    private bool Arg5Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> WithArg5(T5 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg5 = arg, Arg5Set = true };
    }

    private T6? Arg6 { get; init; }
    private bool Arg6Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> WithArg6(T6 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg6 = arg, Arg6Set = true };
    }

    private T7? Arg7 { get; init; }
    private bool Arg7Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> WithArg7(T7 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg7 = arg, Arg7Set = true };
    }

    private T8? Arg8 { get; init; }
    private bool Arg8Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> WithArg8(T8 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg8 = arg, Arg8Set = true };
    }

    private T9? Arg9 { get; init; }
    private bool Arg9Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> WithArg9(T9 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg9 = arg, Arg9Set = true };
    }

    private T10? Arg10 { get; init; }
    private bool Arg10Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> WithArg10(T10 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg10 = arg, Arg10Set = true };
    }

    private T11? Arg11 { get; init; }
    private bool Arg11Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> WithArg11(T11 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg11 = arg, Arg11Set = true };
    }

    private T12? Arg12 { get; init; }
    private bool Arg12Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> WithArg12(T12 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg12 = arg, Arg12Set = true };
    }

    private T13? Arg13 { get; init; }
    private bool Arg13Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> WithArg13(T13 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg13 = arg, Arg13Set = true };
    }

    private T14? Arg14 { get; init; }
    private bool Arg14Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> WithArg14(T14 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg14 = arg, Arg14Set = true };
    }

    private T15? Arg15 { get; init; }
    private bool Arg15Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> WithArg15(T15 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg15 = arg, Arg15Set = true };
    }

    private T16? Arg16 { get; init; }
    private bool Arg16Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> WithArg16(T16 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg16 = arg, Arg16Set = true };
    }

    private T17? Arg17 { get; init; }
    private bool Arg17Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> WithArg17(T17 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg17 = arg, Arg17Set = true };
    }

    private T18? Arg18 { get; init; }
    private bool Arg18Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> WithArg18(T18 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg18 = arg, Arg18Set = true };
    }

    private T19? Arg19 { get; init; }
    private bool Arg19Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19> WithArg19(T19 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg19 = arg, Arg19Set = true };
    }

    Task<Value> IGlideCommand.ExecuteAsync(IGlideClient client, CancellationToken cancellationToken = default)
    {
        if (!Arg1Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 0));
        if (!Arg2Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 1));
        if (!Arg3Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 2));
        if (!Arg4Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 3));
        if (!Arg5Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 4));
        if (!Arg6Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 5));
        if (!Arg7Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 6));
        if (!Arg8Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 7));
        if (!Arg9Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 8));
        if (!Arg10Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 9));
        if (!Arg11Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 10));
        if (!Arg12Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 11));
        if (!Arg13Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 12));
        if (!Arg14Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 13));
        if (!Arg15Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 14));
        if (!Arg16Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 15));
        if (!Arg17Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 16));
        if (!Arg18Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 17));
        if (!Arg19Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 18));
        return client.SendCommandAsync(ERequestType.CustomCommand, RoutingInfo, client.ToParameter(Arg1), client.ToParameter(Arg2), client.ToParameter(Arg3), client.ToParameter(Arg4), client.ToParameter(Arg5), client.ToParameter(Arg6), client.ToParameter(Arg7), client.ToParameter(Arg8), client.ToParameter(Arg9), client.ToParameter(Arg10), client.ToParameter(Arg11), client.ToParameter(Arg12), client.ToParameter(Arg13), client.ToParameter(Arg14), client.ToParameter(Arg15), client.ToParameter(Arg16), client.ToParameter(Arg17), client.ToParameter(Arg18), client.ToParameter(Arg19));
    }
}

/// <summary>
/// Provides factory methods for creating instances of the CustomCommand structures.
/// </summary>
/// <remarks>
/// This static class enables the creation of CustomCommand instances with varying numbers of generic arguments,
/// facilitating a fluent and type-safe approach to defining and executing commands within the Glide framework.
/// </remarks
public static partial class CustomCommand
{
    /// <summary>
    /// Creates a new instance of the <see cref="CustomCommand{T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20}"/> structure with the specified argument.
    /// </summary>
    /// <typeparam name="T1">The type of the first argument for the command.</typeparam>
    /// <param name="Arg1">The value of the first argument to be passed to the command.</param>
    /// <typeparam name="T2">The type of the second argument for the command.</typeparam>
    /// <param name="Arg2">The value of the second argument to be passed to the command.</param>
    /// <typeparam name="T3">The type of the third argument for the command.</typeparam>
    /// <param name="Arg3">The value of the third argument to be passed to the command.</param>
    /// <typeparam name="T4">The type of the fourth argument for the command.</typeparam>
    /// <param name="Arg4">The value of the fourth argument to be passed to the command.</param>
    /// <typeparam name="T5">The type of the fifth argument for the command.</typeparam>
    /// <param name="Arg5">The value of the fifth argument to be passed to the command.</param>
    /// <typeparam name="T6">The type of the sixth argument for the command.</typeparam>
    /// <param name="Arg6">The value of the sixth argument to be passed to the command.</param>
    /// <typeparam name="T7">The type of the seventh argument for the command.</typeparam>
    /// <param name="Arg7">The value of the seventh argument to be passed to the command.</param>
    /// <typeparam name="T8">The type of the eighth argument for the command.</typeparam>
    /// <param name="Arg8">The value of the eighth argument to be passed to the command.</param>
    /// <typeparam name="T9">The type of the ninth argument for the command.</typeparam>
    /// <param name="Arg9">The value of the ninth argument to be passed to the command.</param>
    /// <typeparam name="T10">The type of the tenth argument for the command.</typeparam>
    /// <param name="Arg10">The value of the tenth argument to be passed to the command.</param>
    /// <typeparam name="T11">The type of the eleventh argument for the command.</typeparam>
    /// <param name="Arg11">The value of the eleventh argument to be passed to the command.</param>
    /// <typeparam name="T12">The type of the twelfth argument for the command.</typeparam>
    /// <param name="Arg12">The value of the twelfth argument to be passed to the command.</param>
    /// <typeparam name="T13">The type of the thirteenth argument for the command.</typeparam>
    /// <param name="Arg13">The value of the thirteenth argument to be passed to the command.</param>
    /// <typeparam name="T14">The type of the fourteenth argument for the command.</typeparam>
    /// <param name="Arg14">The value of the fourteenth argument to be passed to the command.</param>
    /// <typeparam name="T15">The type of the fifteenth argument for the command.</typeparam>
    /// <param name="Arg15">The value of the fifteenth argument to be passed to the command.</param>
    /// <typeparam name="T16">The type of the sixteenth argument for the command.</typeparam>
    /// <param name="Arg16">The value of the sixteenth argument to be passed to the command.</param>
    /// <typeparam name="T17">The type of the seventeenth argument for the command.</typeparam>
    /// <param name="Arg17">The value of the seventeenth argument to be passed to the command.</param>
    /// <typeparam name="T18">The type of the eighteenth argument for the command.</typeparam>
    /// <param name="Arg18">The value of the eighteenth argument to be passed to the command.</param>
    /// <typeparam name="T19">The type of the nineteenth argument for the command.</typeparam>
    /// <param name="Arg19">The value of the nineteenth argument to be passed to the command.</param>
    /// <typeparam name="T20">The type of the twentieth argument for the command.</typeparam>
    /// <param name="Arg20">The value of the twentieth argument to be passed to the command.</param>
    /// <returns>An instance of <see cref="CustomCommand{T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20}"/> initialized with the provided argument.</returns>
    public static CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20> Create<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20>(TRoutingInfo routingInfo, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11, T12 arg12, T13 arg13, T14 arg14, T15 arg15, T16 arg16, T17 arg17, T18 arg18, T19 arg19, T20 arg20)
         where TRoutingInfo : IRoutingInfo
         => new CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20>{RoutingInfo = routingInfo}
                .WithArg1(arg1)
                .WithArg2(arg2)
                .WithArg3(arg3)
                .WithArg4(arg4)
                .WithArg5(arg5)
                .WithArg6(arg6)
                .WithArg7(arg7)
                .WithArg8(arg8)
                .WithArg9(arg9)
                .WithArg10(arg10)
                .WithArg11(arg11)
                .WithArg12(arg12)
                .WithArg13(arg13)
                .WithArg14(arg14)
                .WithArg15(arg15)
                .WithArg16(arg16)
                .WithArg17(arg17)
                .WithArg18(arg18)
                .WithArg19(arg19)
                .WithArg20(arg20);
}

/// <summary>
/// Represents a customizable command that can accept a specified number
/// of generic arguments and implements the IGlideCommand interface.
/// </summary>
/// <typeparam name="T1">The type of the first argument for the command.</typeparam>
/// <typeparam name="T2">The type of the second argument for the command.</typeparam>
/// <typeparam name="T3">The type of the third argument for the command.</typeparam>
/// <typeparam name="T4">The type of the fourth argument for the command.</typeparam>
/// <typeparam name="T5">The type of the fifth argument for the command.</typeparam>
/// <typeparam name="T6">The type of the sixth argument for the command.</typeparam>
/// <typeparam name="T7">The type of the seventh argument for the command.</typeparam>
/// <typeparam name="T8">The type of the eighth argument for the command.</typeparam>
/// <typeparam name="T9">The type of the ninth argument for the command.</typeparam>
/// <typeparam name="T10">The type of the tenth argument for the command.</typeparam>
/// <typeparam name="T11">The type of the eleventh argument for the command.</typeparam>
/// <typeparam name="T12">The type of the twelfth argument for the command.</typeparam>
/// <typeparam name="T13">The type of the thirteenth argument for the command.</typeparam>
/// <typeparam name="T14">The type of the fourteenth argument for the command.</typeparam>
/// <typeparam name="T15">The type of the fifteenth argument for the command.</typeparam>
/// <typeparam name="T16">The type of the sixteenth argument for the command.</typeparam>
/// <typeparam name="T17">The type of the seventeenth argument for the command.</typeparam>
/// <typeparam name="T18">The type of the eighteenth argument for the command.</typeparam>
/// <typeparam name="T19">The type of the nineteenth argument for the command.</typeparam>
/// <typeparam name="T20">The type of the twentieth argument for the command.</typeparam>
/// <remarks>
/// <list type="bullet">
/// <item>
/// Each instance of the CustomCommand struct is immutable, and the usage of `WithArg` methods ensures
/// a fluent API for adding argument values while preserving immutability. It integrates with the IGlideCommand
/// interface for executing tasks asynchronously within the Glide framework.
/// </item>
/// <item>All values are handled as "values". If you want to issue command text, use the <see cref="Data.CommandText"/> struct</item>
/// </list>
/// </remarks>
public readonly struct CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20> : IGlideCommand where TRoutingInfo : IRoutingInfo
{
    public required TRoutingInfo RoutingInfo { get; init; }

    private T1? Arg1 { get; init; }
    private bool Arg1Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20> WithArg1(T1 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg1 = arg, Arg1Set = true };
    }

    private T2? Arg2 { get; init; }
    private bool Arg2Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20> WithArg2(T2 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg2 = arg, Arg2Set = true };
    }

    private T3? Arg3 { get; init; }
    private bool Arg3Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20> WithArg3(T3 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg3 = arg, Arg3Set = true };
    }

    private T4? Arg4 { get; init; }
    private bool Arg4Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20> WithArg4(T4 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg4 = arg, Arg4Set = true };
    }

    private T5? Arg5 { get; init; }
    private bool Arg5Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20> WithArg5(T5 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg5 = arg, Arg5Set = true };
    }

    private T6? Arg6 { get; init; }
    private bool Arg6Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20> WithArg6(T6 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg6 = arg, Arg6Set = true };
    }

    private T7? Arg7 { get; init; }
    private bool Arg7Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20> WithArg7(T7 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg7 = arg, Arg7Set = true };
    }

    private T8? Arg8 { get; init; }
    private bool Arg8Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20> WithArg8(T8 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg8 = arg, Arg8Set = true };
    }

    private T9? Arg9 { get; init; }
    private bool Arg9Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20> WithArg9(T9 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg9 = arg, Arg9Set = true };
    }

    private T10? Arg10 { get; init; }
    private bool Arg10Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20> WithArg10(T10 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg10 = arg, Arg10Set = true };
    }

    private T11? Arg11 { get; init; }
    private bool Arg11Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20> WithArg11(T11 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg11 = arg, Arg11Set = true };
    }

    private T12? Arg12 { get; init; }
    private bool Arg12Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20> WithArg12(T12 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg12 = arg, Arg12Set = true };
    }

    private T13? Arg13 { get; init; }
    private bool Arg13Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20> WithArg13(T13 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg13 = arg, Arg13Set = true };
    }

    private T14? Arg14 { get; init; }
    private bool Arg14Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20> WithArg14(T14 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg14 = arg, Arg14Set = true };
    }

    private T15? Arg15 { get; init; }
    private bool Arg15Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20> WithArg15(T15 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg15 = arg, Arg15Set = true };
    }

    private T16? Arg16 { get; init; }
    private bool Arg16Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20> WithArg16(T16 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg16 = arg, Arg16Set = true };
    }

    private T17? Arg17 { get; init; }
    private bool Arg17Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20> WithArg17(T17 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg17 = arg, Arg17Set = true };
    }

    private T18? Arg18 { get; init; }
    private bool Arg18Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20> WithArg18(T18 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg18 = arg, Arg18Set = true };
    }

    private T19? Arg19 { get; init; }
    private bool Arg19Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20> WithArg19(T19 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg19 = arg, Arg19Set = true };
    }

    private T20? Arg20 { get; init; }
    private bool Arg20Set { get; init; }
    public CustomCommand<TRoutingInfo, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20> WithArg20(T20 arg)
    {
        // ReSharper disable once WithExpressionModifiesAllMembers
        // ReSharper disable once ArrangeMethodOrOperatorBody
        return this with { Arg20 = arg, Arg20Set = true };
    }

    Task<Value> IGlideCommand.ExecuteAsync(IGlideClient client, CancellationToken cancellationToken = default)
    {
        if (!Arg1Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 0));
        if (!Arg2Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 1));
        if (!Arg3Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 2));
        if (!Arg4Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 3));
        if (!Arg5Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 4));
        if (!Arg6Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 5));
        if (!Arg7Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 6));
        if (!Arg8Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 7));
        if (!Arg9Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 8));
        if (!Arg10Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 9));
        if (!Arg11Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 10));
        if (!Arg12Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 11));
        if (!Arg13Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 12));
        if (!Arg14Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 13));
        if (!Arg15Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 14));
        if (!Arg16Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 15));
        if (!Arg17Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 16));
        if (!Arg18Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 17));
        if (!Arg19Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 18));
        if (!Arg20Set)
            throw new InvalidOperationException(string.Format(Properties.Language.CustomCommand_ArgumentNotSet_0index, 19));
        return client.SendCommandAsync(ERequestType.CustomCommand, RoutingInfo, client.ToParameter(Arg1), client.ToParameter(Arg2), client.ToParameter(Arg3), client.ToParameter(Arg4), client.ToParameter(Arg5), client.ToParameter(Arg6), client.ToParameter(Arg7), client.ToParameter(Arg8), client.ToParameter(Arg9), client.ToParameter(Arg10), client.ToParameter(Arg11), client.ToParameter(Arg12), client.ToParameter(Arg13), client.ToParameter(Arg14), client.ToParameter(Arg15), client.ToParameter(Arg16), client.ToParameter(Arg17), client.ToParameter(Arg18), client.ToParameter(Arg19), client.ToParameter(Arg20));
    }
}
