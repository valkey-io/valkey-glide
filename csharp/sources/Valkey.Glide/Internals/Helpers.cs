// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System.Text;

namespace Valkey.Glide.Internals;

internal static class Helpers
{
    // Downcast dictionary keys from `GlideString` to `string`
    public static Dictionary<string, T> DownCastKeys<T>(this Dictionary<GlideString, T> dict)
        => dict.Select(p => (Key: p.Key.ToString(), p.Value)).ToDictionary(p => p.Key, p => p.Value);

    // TODO make recursive?
    // Downcast dictionary values from `GlideString` to `string`
    public static Dictionary<T, string> DownCastVals<T>(this Dictionary<T, GlideString> dict) where T : class
        => dict.Select(p => (p.Key, Value: p.Value.ToString())).ToDictionary(p => p.Key, p => p.Value);

    // Convert values in a dictionary from V to T where input dict has type `Dictionary<K, V>` and returns `Dictionary<K, T>`
    public static Dictionary<K, T> ConvertValues<K, V, T>(this Dictionary<K, V> dict, Func<V, T> converter) where K : notnull
        => dict.Select(p => (p.Key, Value: converter(p.Value))).ToDictionary(p => p.Key, p => p.Value);

    // Convert values in a dictionary from (object)V to T where input dict has type `Dictionary<K, V>` and returns `Dictionary<K, T>`
    // We receive maps as `Dictionary<GlideString, object>` or `Dictionary<String, object>` from `ResponseHandler`. This converter
    // assumes that all values in a map have the same type V.
    public static Dictionary<K, T> ConvertValues<K, V, T>(this Dictionary<K, object> dict, Func<V, T> converter) where K : notnull
        => dict.Select(p => (p.Key, Value: converter((V)p.Value))).ToDictionary(p => p.Key, p => p.Value);

    // Get type name in format like "Dictionary<GlideString, GlideString>" (not "Dictionary`2")
    public static string GetRealTypeName(this Type t)
    {
        if (!t.IsGenericType)
        {
            return t.Name;
        }

        StringBuilder sb = new();
        _ = sb.Append(t.Name.AsSpan(0, t.Name.IndexOf('`'))).Append('<');
        bool appendComma = false;
        foreach (Type arg in t.GetGenericArguments())
        {
            if (appendComma)
            {
                _ = sb.Append(", ");
            }
            _ = sb.Append(GetRealTypeName(arg));
            appendComma = true;
        }
        return sb.Append('>').ToString();
    }
}
