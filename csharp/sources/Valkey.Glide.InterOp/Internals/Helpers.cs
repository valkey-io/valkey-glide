// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System.Globalization;
using System.Text;

using Valkey.Glide.InterOp.Native;

namespace Valkey.Glide.InterOp.Internals;

public static class Helpers
{
    // Downcast dictionary keys from `GlideString` to `string`
    public static Dictionary<string, T> DownCastKeys<T>(this Dictionary<GlideString, T> dict)
        => dict.Select(p => (Key: p.Key.ToString(), p.Value)).ToDictionary(p => p.Key, p => p.Value);

    // TODO make recursive?
    // Downcast dictionary values from `GlideString` to `string`
    public static Dictionary<T, string> DownCastVals<T>(this Dictionary<T, GlideString> dict) where T : class
        => dict.Select(p => (p.Key, Value: p.Value.ToString())).ToDictionary(p => p.Key, p => p.Value);

    // Convert values in a dictionary from T to V where input dict has type `Dictionary<K, object>`
    public static Dictionary<K, V> ConvertValues<K, V, T>(this Dictionary<K, object> dict, Func<T, V> converter) where K : class
        => dict.Select(p => (p.Key, Value: converter((T)p.Value))).ToDictionary(p => p.Key, p => p.Value);

    // Get type name in format like "Dictionary<GlideString, GlideString>" (not "Dictionary`2")
    public static string GetRealTypeName(this Type t)
    {
        // src: https://github.com/X39/cs-x39-util/blob/master/X39.Util/TypeExtensionMethods.cs#L174 "FullNameUncached"
        // Contributed in accordance with license terms of Glide project perpetual, signed off by X39
        if (t.FullName is null)
            return t.Name;
        if (t.IsGenericParameter)
            return t.FullName;
        StringBuilder builder = new();
        string? fullName = t.FullName;
        int argumentsIndex = fullName.IndexOf('[');
        string typePart = argumentsIndex == -1 ? fullName : fullName.Substring(0, argumentsIndex);
        string[]? plusSplit = typePart.Split('+');
        Type[] genericArguments = t.GetGenericArguments();
        int index = 0;
        bool ran = false;
        foreach (string str in plusSplit)
        {
            if (ran)
                builder.Append('.');
            ran = true;
            int gravisIndex = str.IndexOf('`');
            if (gravisIndex == -1)
                builder.Append(str);
            else
            {
                string left = str.Substring(0, gravisIndex);
                builder.Append(left);

                string right = str.Substring(gravisIndex + 1);
                int num = int.Parse(right, NumberStyles.Integer, CultureInfo.InvariantCulture);
                builder.Append('<');
                builder.Append(string.Join(", ", genericArguments.Skip(index).Take(num).Select(GetRealTypeName)));
                builder.Append('>');
                index += num;
            }
        }

        if (t.IsArray)
        {
            builder.Append('[');
            builder.Append(',', t.GetArrayRank() - 1);
            builder.Append(']');
        }
        return builder.ToString();
    }
}
