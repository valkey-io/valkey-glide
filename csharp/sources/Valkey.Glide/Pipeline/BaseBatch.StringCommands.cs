// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Internals;

namespace Valkey.Glide.Pipeline;

public abstract partial class BaseBatch<T> where T : BaseBatch<T>
{
    /// <inheritdoc cref="IBatchStringCommands.Get(GlideString)" />
    public T Get(GlideString key) => AddCmd(Request.Get(key));

    /// <inheritdoc cref="IBatchStringCommands.MGet(GlideString[])" />
    public T MGet(GlideString[] keys)
        => keys.Length == 0
            ? throw new ArgumentException("Keys array cannot be empty", nameof(keys))
            : AddCmd(Request.MGet(keys));

    /// <inheritdoc cref="IBatchStringCommands.Set(GlideString, GlideString)" />
    public T Set(GlideString key, GlideString value) => AddCmd(Request.Set(key, value));

    /// <inheritdoc cref="IBatchStringCommands.MSet(Dictionary{GlideString, GlideString})" />
    public T MSet(Dictionary<GlideString, GlideString> keyValueMap)
    {
        if (keyValueMap.Count == 0)
        {
            throw new ArgumentException("Key-value map cannot be empty", nameof(keyValueMap));
        }

        GlideString[] keyValuePairs = ConvertDictionaryToKeyValueArray(keyValueMap);
        return AddCmd(Request.MSet(keyValuePairs));
    }

    /// <inheritdoc cref="IBatchStringCommands.GetRange(GlideString, long, long)" />
    public T GetRange(GlideString key, long start, long end) => AddCmd(Request.GetRange(key, start, end));

    /// <inheritdoc cref="IBatchStringCommands.SetRange(GlideString, long, GlideString)" />
    public T SetRange(GlideString key, long offset, GlideString value) => AddCmd(Request.SetRange(key, offset, value));

    /// <inheritdoc cref="IBatchStringCommands.Strlen(GlideString)" />
    public T Strlen(GlideString key) => AddCmd(Request.Strlen(key));

    /// <summary>
    /// Converts a Dictionary to an array of key-value pairs for use with MSet command.
    /// </summary>
    /// <param name="keyValueMap">The dictionary to convert.</param>
    /// <returns>An array where keys and values are interleaved: [key1, value1, key2, value2, ...]</returns>
    private static GlideString[] ConvertDictionaryToKeyValueArray(Dictionary<GlideString, GlideString> keyValueMap)
    {
        GlideString[] result = new GlideString[keyValueMap.Count * 2];
        int index = 0;
        foreach (KeyValuePair<GlideString, GlideString> kvp in keyValueMap)
        {
            result[index++] = kvp.Key;
            result[index++] = kvp.Value;
        }
        return result;
    }

    IBatch IBatchStringCommands.Get(GlideString key) => Get(key);
    IBatch IBatchStringCommands.MGet(GlideString[] keys) => MGet(keys);
    IBatch IBatchStringCommands.Set(GlideString key, GlideString value) => Set(key, value);
    IBatch IBatchStringCommands.MSet(Dictionary<GlideString, GlideString> keyValueMap) => MSet(keyValueMap);
    IBatch IBatchStringCommands.GetRange(GlideString key, long start, long end) => GetRange(key, start, end);
    IBatch IBatchStringCommands.SetRange(GlideString key, long offset, GlideString value) => SetRange(key, offset, value);
    IBatch IBatchStringCommands.Strlen(GlideString key) => Strlen(key);
}
