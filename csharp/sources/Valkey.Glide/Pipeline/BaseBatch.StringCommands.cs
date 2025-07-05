// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Internals;

namespace Valkey.Glide.Pipeline;

public abstract partial class BaseBatch<T> where T : BaseBatch<T>
{
    /// <inheritdoc cref="IBatchStringCommands.StringGet(GlideString)" />
    public T StringGet(GlideString key) => AddCmd(Request.Get(key));

    /// <inheritdoc cref="IBatchStringCommands.StringGet(GlideString[])" />
    public T StringGet(GlideString[] keys)
        => keys.Length == 0
            ? throw new ArgumentException("Keys array cannot be empty", nameof(keys))
            : AddCmd(Request.MGet(keys));

    /// <inheritdoc cref="IBatchStringCommands.StringSet(GlideString, GlideString)" />
    public T StringSet(GlideString key, GlideString value) => AddCmd(Request.Set(key, value));

    /// <inheritdoc cref="IBatchStringCommands.StringSet(KeyValuePair{GlideString, GlideString}[])" />
    public T StringSet(KeyValuePair<GlideString, GlideString>[] values)
    {
        if (values.Length == 0)
        {
            throw new ArgumentException("Values array cannot be empty", nameof(values));
        }

        GlideString[] keyValuePairs = Helpers.ConvertKeyValuePairsToArray(values);
        return AddCmd(Request.MSet(keyValuePairs));
    }

    /// <inheritdoc cref="IBatchStringCommands.StringGetRange(GlideString, long, long)" />
    public T StringGetRange(GlideString key, long start, long end) => AddCmd(Request.GetRange(key, start, end));

    /// <inheritdoc cref="IBatchStringCommands.StringSetRange(GlideString, long, GlideString)" />
    public T StringSetRange(GlideString key, long offset, GlideString value) => AddCmd(Request.SetRange(key, offset, value));

    /// <inheritdoc cref="IBatchStringCommands.StringLength(GlideString)" />
    public T StringLength(GlideString key) => AddCmd(Request.Strlen(key));

    IBatch IBatchStringCommands.StringGet(GlideString key) => StringGet(key);
    IBatch IBatchStringCommands.StringGet(GlideString[] keys) => StringGet(keys);
    IBatch IBatchStringCommands.StringSet(GlideString key, GlideString value) => StringSet(key, value);
    IBatch IBatchStringCommands.StringSet(KeyValuePair<GlideString, GlideString>[] values) => StringSet(values);
    IBatch IBatchStringCommands.StringGetRange(GlideString key, long start, long end) => StringGetRange(key, start, end);
    IBatch IBatchStringCommands.StringSetRange(GlideString key, long offset, GlideString value) => StringSetRange(key, offset, value);
    IBatch IBatchStringCommands.StringLength(GlideString key) => StringLength(key);
}
