// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System.Runtime.CompilerServices;
using Valkey.Glide.Commands.Abstraction;
using Valkey.Glide.InterOp.Native;
using Valkey.Glide.InterOp.Routing;
using Value = Valkey.Glide.InterOp.Value;

namespace Valkey.Glide.Commands;

/// <summary>
/// Provides a set of methods for creating and configuring SetCommand instances,
/// enabling streamlined functionality for setting values in a Glide client.
/// </summary>
public static class SetCommand
{
    /// <summary>
    /// Creates a new instance of <see cref="SetCommand{T}"/>, pre-configured with a key and a value.
    /// </summary>
    /// <typeparam name="T">The type of the value to be set.</typeparam>
    /// <param name="key">The key associated with the value being set.</param>
    /// <param name="value">The value to be set.</param>
    /// <returns>A new instance of <see cref="SetCommand{T}"/> configured with the specified key and value.</returns>
    public static SetCommand<NoRouting, TValue> Create<TValue>(string key, TValue value) =>
        new SetCommand<NoRouting, TValue> {RoutingInfo = new NoRouting()}.WithKey(key).WithValue(value);

    /// <summary>
    /// Creates a new instance of <see cref="SetCommand{NoRouting, TValue}"/> configured with a key and a value using default routing.
    /// </summary>
    /// <typeparam name="TValue">The type of the value to be set.</typeparam>
    /// <param name="key">The key associated with the value to be set.</param>
    /// <param name="value">The value to be set.</param>
    /// <returns>A new instance of <see cref="SetCommand{NoRouting, TValue}"/> configured with the specified key and value.</returns>
    public static SetCommand<TRoutingInfo, TValue> Create<TRoutingInfo, TValue>(
        TRoutingInfo routingInfo,
        string key,
        TValue value
    ) where TRoutingInfo : IRoutingInfo =>
        new SetCommand<TRoutingInfo, TValue> {RoutingInfo = routingInfo}.WithKey(key).WithValue(value);
}

/// <summary>
/// Represents a structured command for setting a value in a Glide client.
/// This immutable struct provides configuration options for controlling the behavior of the set operation,
/// including expiration policies, conditional setting, and value retrieval.
/// </summary>
/// <typeparam name="T">The type of the value being set.</typeparam>
public readonly struct SetCommand<TRoutingInfo, TValue>()
    : IGlideCommand where TRoutingInfo : IRoutingInfo
{
    private string Key { get; init; } = string.Empty; // key
    private bool ValueSet { get; init; }
    private TValue? Value { get; init; } = default; // value
    private bool Get { get; init; } // GET

    private TimeSpan? ExpiresIn { get; init; } // EX seconds / PX milliseconds
    private DateTime? ExpiresAt { get; init; } // EXAT unix-time-seconds / PXAT unix-time-milliseconds
    private bool KeepTtl { get; init; } // KEEPTTL

    private bool SetIfDoesNotExists { get; init; } // NX
    private bool SetIfExists { get; init; } // XX
    private string? SetIfEquals { get; init; } // IFEQ comparison-value
    public required TRoutingInfo RoutingInfo { get; init; }

    [MethodImpl(MethodImplOptions.AggressiveInlining | MethodImplOptions.AggressiveOptimization)]
    public SetCommand<TRoutingInfo, TValue> WithKey(string key)
        => this with {Key = key};

    [MethodImpl(MethodImplOptions.AggressiveInlining | MethodImplOptions.AggressiveOptimization)]
    public SetCommand<TRoutingInfo, TValue> WithValue(TValue value)
        => this with {Value = value, ValueSet = true};

    [MethodImpl(MethodImplOptions.AggressiveInlining | MethodImplOptions.AggressiveOptimization)]
    public SetCommand<TRoutingInfo, TValue> WithGet()
        => this with {Get = true};

    [MethodImpl(MethodImplOptions.AggressiveInlining | MethodImplOptions.AggressiveOptimization)]
    public SetCommand<TRoutingInfo, TValue> WithExpiresIn(TimeSpan expiresIn)
        => this with {ExpiresIn = expiresIn, ExpiresAt = null, KeepTtl = false};

    [MethodImpl(MethodImplOptions.AggressiveInlining | MethodImplOptions.AggressiveOptimization)]
    public SetCommand<TRoutingInfo, TValue> WithExpiresAt(DateTime expiresAt)
        => this with {ExpiresIn = null, ExpiresAt = expiresAt, KeepTtl = false};

    [MethodImpl(MethodImplOptions.AggressiveInlining | MethodImplOptions.AggressiveOptimization)]
    public SetCommand<TRoutingInfo, TValue> WithKeepTtl()
        => this with {ExpiresIn = null, ExpiresAt = null, KeepTtl = true};

    [MethodImpl(MethodImplOptions.AggressiveInlining | MethodImplOptions.AggressiveOptimization)]
    public SetCommand<TRoutingInfo, TValue> WithSetIfNotExists()
        => this with {SetIfDoesNotExists = true, SetIfExists = false, SetIfEquals = null};

    [MethodImpl(MethodImplOptions.AggressiveInlining | MethodImplOptions.AggressiveOptimization)]
    public SetCommand<TRoutingInfo, TValue> WithSetIfExists()
        => this with {SetIfDoesNotExists = false, SetIfExists = true, SetIfEquals = null};

    [MethodImpl(MethodImplOptions.AggressiveInlining | MethodImplOptions.AggressiveOptimization)]
    public SetCommand<TRoutingInfo, TValue> WithSetIfEquals(string value)
        => this with {SetIfDoesNotExists = false, SetIfExists = false, SetIfEquals = value};


    /// <inheritdoc />
    [MethodImpl(MethodImplOptions.AggressiveInlining | MethodImplOptions.AggressiveOptimization)]
    public Task<Value> ExecuteAsync(IGlideClient client, CancellationToken cancellationToken = default)
    {
        if (ExpiresIn.HasValue)
            if (ExpiresIn.Value.TotalMilliseconds % 1000 != 0)
                return ExecuteTtlWithGet(
                    client,
                    [
                        "PX".AsRedisCommandText(),
                        ((ulong)ExpiresIn.Value.TotalMilliseconds).ToString()
                        .AsRedisInteger()
                    ]
                );
            else
                return ExecuteTtlWithGet(
                    client,
                    [
                        "EX".AsRedisCommandText(),
                        ((ulong)ExpiresIn.Value.TotalSeconds).ToString()
                        .AsRedisInteger()
                    ]
                );
        if (ExpiresAt.HasValue)
        {
            TimeSpan unixTimeSpan = ExpiresAt.Value - DateTime.UnixEpoch;
            if (unixTimeSpan.TotalMilliseconds % 1000 != 0)
                return ExecuteTtlWithGet(
                    client,
                    [
                        "PXAT".AsRedisCommandText(),
                        ((ulong)unixTimeSpan.TotalMilliseconds).ToString()
                        .AsRedisInteger()
                    ]
                );
            else
                return ExecuteTtlWithGet(
                    client,
                    [
                        "EXAT".AsRedisCommandText(),
                        ((ulong)unixTimeSpan.TotalSeconds).ToString()
                        .AsRedisInteger()
                    ]
                );
        }

        if (KeepTtl)
            return ExecuteTtlWithGet(client, ["KEEPTTL".AsRedisCommandText()]);
        return ExecuteTtlWithGet(client, []);
    }

    [MethodImpl(MethodImplOptions.AggressiveInlining | MethodImplOptions.AggressiveOptimization)]
    private Task<Value> ExecuteTtlWithGet(IGlideClient client, string[] ttlParameters)
    {
        if (Get)
            return ExecuteInner(client, ["GET".AsRedisCommandText(), ..ttlParameters]);
        return ExecuteInner(client, ttlParameters);
    }

    [MethodImpl(MethodImplOptions.AggressiveInlining | MethodImplOptions.AggressiveOptimization)]
    private Task<Value> ExecuteInner(IGlideClient client, string[] ttlParameters)
    {
        if (string.IsNullOrWhiteSpace(Key))
            throw new InvalidOperationException(Properties.Language.SetCommand_KeyNotSet);
        if (!ValueSet)
            throw new InvalidOperationException(Properties.Language.SetCommand_ValueNotSet);

        if (SetIfDoesNotExists)
            return client.CommandAsync(
                ERequestType.Set,
                RoutingInfo,
                [Key.AsRedisCommandText(), client.Transform(Value), "NX".AsRedisCommandText(), ..ttlParameters]
            );
        if (SetIfExists)
            return client.CommandAsync(
                ERequestType.Set,
                RoutingInfo,
                [Key.AsRedisCommandText(), client.Transform(Value), "XX".AsRedisCommandText(), ..ttlParameters]
            );
        if (SetIfEquals is not null)
            return client.CommandAsync(
                ERequestType.Set,
                RoutingInfo,
                [
                    Key.AsRedisCommandText(),
                    client.Transform(Value),
                    "IFEQ".AsRedisCommandText(),
                    SetIfEquals.AsRedisString(),
                    ..ttlParameters
                ]
            );
        return client.CommandAsync(
            ERequestType.Set,
            RoutingInfo,
            [Key.AsRedisCommandText(), client.Transform(Value), ..ttlParameters]
        );
    }
}
