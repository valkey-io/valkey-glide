// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Internals;

namespace Valkey.Glide.Pipeline;

public abstract partial class BaseBatch<T> where T : BaseBatch<T>
{
    /// <inheritdoc cref="IBatchStringCommands.StringGet(GlideString)" />
    public T StringGet(GlideString key) => AddCmd(Request.StringGet(key));

    /// <inheritdoc cref="IBatchStringCommands.StringGet(GlideString[])" />
    public T StringGet(GlideString[] keys) => AddCmd(Request.StringGetAsync(keys));

    /// <inheritdoc cref="IBatchStringCommands.StringSet(GlideString, GlideString)" />
    public T StringSet(GlideString key, GlideString value) => AddCmd(Request.StringSet(key, value));

    /// <inheritdoc cref="IBatchStringCommands.StringSet(KeyValuePair{GlideString, GlideString}[])" />
    public T StringSet(KeyValuePair<GlideString, GlideString>[] values)
    {
        GlideString[] keyValuePairs = Helpers.ConvertKeyValuePairsToArray(values);
        return AddCmd(Request.StringSetAsync(keyValuePairs));
    }

    /// <inheritdoc cref="IBatchStringCommands.StringGetRange(GlideString, long, long)" />
    public T StringGetRange(GlideString key, long start, long end) => AddCmd(Request.StringGetRange(key, start, end));

    /// <inheritdoc cref="IBatchStringCommands.StringSetRange(GlideString, long, GlideString)" />
    public T StringSetRange(GlideString key, long offset, GlideString value) => AddCmd(Request.StringSetRange(key, offset, value));

    /// <inheritdoc cref="IBatchStringCommands.StringLength(GlideString)" />
    public T StringLength(GlideString key) => AddCmd(Request.StringLength(key));

    IBatch IBatchStringCommands.StringGet(GlideString key) => StringGet(key);
    IBatch IBatchStringCommands.StringGet(GlideString[] keys) => StringGet(keys);
    IBatch IBatchStringCommands.StringSet(GlideString key, GlideString value) => StringSet(key, value);
    IBatch IBatchStringCommands.StringSet(KeyValuePair<GlideString, GlideString>[] values) => StringSet(values);
    IBatch IBatchStringCommands.StringGetRange(GlideString key, long start, long end) => StringGetRange(key, start, end);
    IBatch IBatchStringCommands.StringSetRange(GlideString key, long offset, GlideString value) => StringSetRange(key, offset, value);
    IBatch IBatchStringCommands.StringLength(GlideString key) => StringLength(key);
}
