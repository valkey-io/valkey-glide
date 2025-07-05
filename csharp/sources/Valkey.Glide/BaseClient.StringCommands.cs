// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Commands;
using Valkey.Glide.Internals;

namespace Valkey.Glide;

public abstract partial class BaseClient : IStringBaseCommands
{
    public async Task<string> Set(GlideString key, GlideString value)
        => await Command(Request.Set(key, value));

    public async Task<GlideString?> Get(GlideString key)
        => await Command(Request.Get(key));

    public async Task<GlideString?[]> MGet(GlideString[] keys)
        => keys.Length == 0
            ? throw new ArgumentException("Keys array cannot be empty", nameof(keys))
            : await Command(Request.MGet(keys));

    public async Task<string> MSet(Dictionary<GlideString, GlideString> keyValueMap)
    {
        if (keyValueMap.Count == 0)
        {
            throw new ArgumentException("Key-value map cannot be empty", nameof(keyValueMap));
        }

        GlideString[] keyValuePairs = ConvertDictionaryToKeyValueArray(keyValueMap);
        return await Command(Request.MSet(keyValuePairs));
    }

    public async Task<GlideString> GetRange(GlideString key, long start, long end)
        => await Command(Request.GetRange(key, start, end));

    public async Task<long> SetRange(GlideString key, long offset, GlideString value)
        => await Command(Request.SetRange(key, offset, value));

    public async Task<long> Strlen(GlideString key)
        => await Command(Request.Strlen(key));

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
}
