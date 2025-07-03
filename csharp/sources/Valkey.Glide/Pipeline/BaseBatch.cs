// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System.Diagnostics;

using Valkey.Glide.Internals;

using static Valkey.Glide.Commands.Options.InfoOptions;
using static Valkey.Glide.Internals.Request;

namespace Valkey.Glide.Pipeline;

/// <summary>
/// Base class encompassing shared commands for both standalone and cluster server installations.
/// Batches allow the execution of a group of commands in a single step.
/// <para />
/// Batch Response: An <c>array</c> of command responses is returned by the client <c>Exec</c> API,
/// in the order they were given. Each element in the array represents a command given to the <c>Batch</c>.
/// The response for each command depends on the executed Valkey command. Specific response types are
/// documented alongside each method.
/// </summary>
/// <typeparam name="T">Child typing for chaining method calls.</typeparam>
/// <param name="isAtomic">
/// Determines whether the batch is atomic or non-atomic. If <see langword="true" />, the batch will be executed as
/// an atomic transaction. If <see langword="false" />, the batch will be executed as a non-atomic pipeline.
/// </param>
public abstract class BaseBatch<T>(bool isAtomic) : IBatch where T : BaseBatch<T>
{
    private readonly List<ICmd> _commands = [];

    internal bool IsAtomic { get; private set; } = isAtomic;

    internal FFI.Batch ToFFI() => new([.. _commands.Select(c => c.ToFfi())], IsAtomic);

    /// <summary>
    /// Convert a response received from the server.
    /// </summary>
    internal object?[]? ConvertResponse(object?[]? response)
    {
        if (response is null)
        {
            return null;
        }

        Debug.Assert(response.Length == _commands.Count,
            $"Response misaligned: received {response.Length} responses but submitted {_commands.Count} commands");

        for (int i = 0; i < response?.Length; i++)
        {
            response[i] = _commands[i].GetConverter()(response[i]);
        }
        return response;
    }

    internal T AddCmd(ICmd cmd)
    {
        _commands.Add(cmd);
        return (T)this;
    }

    /// <inheritdoc cref="IBatch.CustomCommand(GlideString[])" />
    public T CustomCommand(GlideString[] args) => AddCmd(Request.CustomCommand(args));

    /// <inheritdoc cref="IBatch.Get(GlideString)" />
    public T Get(GlideString key) => AddCmd(Request.Get(key));

    /// <inheritdoc cref="IBatch.Set(GlideString, GlideString)" />
    public T Set(GlideString key, GlideString value) => AddCmd(Request.Set(key, value));

    /// <inheritdoc cref="IBatch.Info()" />
    public T Info() => Info([]);

    /// <inheritdoc cref="IBatch.Info(Section[])" />
    public T Info(Section[] sections) => AddCmd(Request.Info(sections));

    /// <inheritdoc cref="IBatch.SetAdd(ValkeyKey, ValkeyValue)" />
    public T SetAdd(ValkeyKey key, ValkeyValue value) => AddCmd(Request.SetAddAsync(key, value));

    /// <inheritdoc cref="IBatch.SetAdd(ValkeyKey, ValkeyValue[])" />
    public T SetAdd(ValkeyKey key, ValkeyValue[] values) => AddCmd(Request.SetAddAsync(key, values));

    /// <inheritdoc cref="IBatch.SetRemove(ValkeyKey, ValkeyValue)" />
    public T SetRemove(ValkeyKey key, ValkeyValue value) => AddCmd(Request.SetRemoveAsync(key, value));

    /// <inheritdoc cref="IBatch.SetRemove(ValkeyKey, ValkeyValue[])" />
    public T SetRemove(ValkeyKey key, ValkeyValue[] values) => AddCmd(Request.SetRemoveAsync(key, values));

    /// <inheritdoc cref="IBatch.SetMembers(ValkeyKey)" />
    public T SetMembers(ValkeyKey key) => AddCmd(Request.SetMembersAsync(key));

    /// <inheritdoc cref="IBatch.SetLength(ValkeyKey)" />
    public T SetLength(ValkeyKey key) => AddCmd(Request.SetLengthAsync(key));

    /// <inheritdoc cref="IBatch.SetIntersectionLength(ValkeyKey[], long)" />
    public T SetIntersectionLength(ValkeyKey[] keys, long limit = 0) => AddCmd(Request.SetIntersectionLengthAsync(keys, limit));

    /// <inheritdoc cref="IBatch.SetPop(ValkeyKey)" />
    public T SetPop(ValkeyKey key) => AddCmd(Request.SetPopAsync(key));

    /// <inheritdoc cref="IBatch.SetPop(ValkeyKey, long)" />
    public T SetPop(ValkeyKey key, long count) => AddCmd(Request.SetPopAsync(key, count));

    /// <inheritdoc cref="IBatch.SetUnion(ValkeyKey, ValkeyKey)" />
    public T SetUnion(ValkeyKey first, ValkeyKey second) => AddCmd(Request.SetUnionAsync([first, second]));

    /// <inheritdoc cref="IBatch.SetUnion(ValkeyKey[])" />
    public T SetUnion(ValkeyKey[] keys) => AddCmd(Request.SetUnionAsync(keys));

    /// <inheritdoc cref="IBatch.SetIntersect(ValkeyKey, ValkeyKey)" />
    public T SetIntersect(ValkeyKey first, ValkeyKey second) => AddCmd(Request.SetIntersectAsync([first, second]));

    /// <inheritdoc cref="IBatch.SetIntersect(ValkeyKey[])" />
    public T SetIntersect(ValkeyKey[] keys) => AddCmd(Request.SetIntersectAsync(keys));

    /// <inheritdoc cref="IBatch.SetDifference(ValkeyKey, ValkeyKey)" />
    public T SetDifference(ValkeyKey first, ValkeyKey second) => AddCmd(Request.SetDifferenceAsync([first, second]));

    /// <inheritdoc cref="IBatch.SetDifference(ValkeyKey[])" />
    public T SetDifference(ValkeyKey[] keys) => AddCmd(Request.SetDifferenceAsync(keys));

    /// <inheritdoc cref="IBatch.SetUnionStore(ValkeyKey, ValkeyKey, ValkeyKey)" />
    public T SetUnionStore(ValkeyKey destination, ValkeyKey first, ValkeyKey second) => AddCmd(Request.SetUnionStoreAsync(destination, [first, second]));

    /// <inheritdoc cref="IBatch.SetUnionStore(ValkeyKey, ValkeyKey[])" />
    public T SetUnionStore(ValkeyKey destination, ValkeyKey[] keys) => AddCmd(Request.SetUnionStoreAsync(destination, keys));

    /// <inheritdoc cref="IBatch.SetIntersectStore(ValkeyKey, ValkeyKey, ValkeyKey)" />
    public T SetIntersectStore(ValkeyKey destination, ValkeyKey first, ValkeyKey second) => AddCmd(Request.SetIntersectStoreAsync(destination, [first, second]));

    /// <inheritdoc cref="IBatch.SetIntersectStore(ValkeyKey, ValkeyKey[])" />
    public T SetIntersectStore(ValkeyKey destination, ValkeyKey[] keys) => AddCmd(Request.SetIntersectStoreAsync(destination, keys));

    /// <inheritdoc cref="IBatch.SetDifferenceStore(ValkeyKey, ValkeyKey, ValkeyKey)" />
    public T SetDifferenceStore(ValkeyKey destination, ValkeyKey first, ValkeyKey second) => AddCmd(Request.SetDifferenceStoreAsync(destination, [first, second]));

    /// <inheritdoc cref="IBatch.SetDifferenceStore(ValkeyKey, ValkeyKey[])" />
    public T SetDifferenceStore(ValkeyKey destination, ValkeyKey[] keys) => AddCmd(Request.SetDifferenceStoreAsync(destination, keys));

    IBatch IBatch.CustomCommand(GlideString[] args) => CustomCommand(args);
    IBatch IBatch.Get(GlideString key) => Get(key);
    IBatch IBatch.Set(GlideString key, GlideString value) => Set(key, value);
    IBatch IBatch.Info() => Info();
    IBatch IBatch.Info(Section[] sections) => Info(sections);
    IBatch IBatch.SetAdd(ValkeyKey key, ValkeyValue value) => SetAdd(key, value);
    IBatch IBatch.SetAdd(ValkeyKey key, ValkeyValue[] values) => SetAdd(key, values);
    IBatch IBatch.SetRemove(ValkeyKey key, ValkeyValue value) => SetRemove(key, value);
    IBatch IBatch.SetRemove(ValkeyKey key, ValkeyValue[] values) => SetRemove(key, values);
    IBatch IBatch.SetMembers(ValkeyKey key) => SetMembers(key);
    IBatch IBatch.SetLength(ValkeyKey key) => SetLength(key);
    IBatch IBatch.SetIntersectionLength(ValkeyKey[] keys, long limit) => SetIntersectionLength(keys, limit);
    IBatch IBatch.SetPop(ValkeyKey key) => SetPop(key);
    IBatch IBatch.SetPop(ValkeyKey key, long count) => SetPop(key, count);
    IBatch IBatch.SetUnion(ValkeyKey first, ValkeyKey second) => SetUnion(first, second);
    IBatch IBatch.SetUnion(ValkeyKey[] keys) => SetUnion(keys);
    IBatch IBatch.SetIntersect(ValkeyKey first, ValkeyKey second) => SetIntersect(first, second);
    IBatch IBatch.SetIntersect(ValkeyKey[] keys) => SetIntersect(keys);
    IBatch IBatch.SetDifference(ValkeyKey first, ValkeyKey second) => SetDifference(first, second);
    IBatch IBatch.SetDifference(ValkeyKey[] keys) => SetDifference(keys);
    IBatch IBatch.SetUnionStore(ValkeyKey destination, ValkeyKey first, ValkeyKey second) => SetUnionStore(destination, first, second);
    IBatch IBatch.SetUnionStore(ValkeyKey destination, ValkeyKey[] keys) => SetUnionStore(destination, keys);
    IBatch IBatch.SetIntersectStore(ValkeyKey destination, ValkeyKey first, ValkeyKey second) => SetIntersectStore(destination, first, second);
    IBatch IBatch.SetIntersectStore(ValkeyKey destination, ValkeyKey[] keys) => SetIntersectStore(destination, keys);
    IBatch IBatch.SetDifferenceStore(ValkeyKey destination, ValkeyKey first, ValkeyKey second) => SetDifferenceStore(destination, first, second);
    IBatch IBatch.SetDifferenceStore(ValkeyKey destination, ValkeyKey[] keys) => SetDifferenceStore(destination, keys);
}
