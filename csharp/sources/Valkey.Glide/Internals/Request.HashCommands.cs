// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using static Valkey.Glide.Internals.FFI;

namespace Valkey.Glide.Internals;

internal partial class Request
{
    public static Cmd<GlideString, ValkeyValue> HashGetAsync(ValkeyKey key, ValkeyValue hashField)
    {
        GlideString[] args = [key.ToGlideString(), hashField.ToGlideString()];
        return new(RequestType.HGet, args, true, response => (ValkeyValue)response);
    }

    public static Cmd<object?[], ValkeyValue[]> HashGetAsync(ValkeyKey key, ValkeyValue[] hashFields)
    {
        GlideString[] args = [key.ToGlideString(), .. hashFields.ToGlideStrings()];
        return new(RequestType.HMGet, args, false, response => response.Select(item =>
            item == null ? ValkeyValue.Null : (ValkeyValue)(GlideString)item).ToArray());
    }

    public static Cmd<Dictionary<object, object>, HashEntry[]> HashGetAllAsync(ValkeyKey key)
    {
        GlideString[] args = [key.ToGlideString()];
        return new(RequestType.HGetAll, args, false, dict => dict.Select(kv =>
            new HashEntry((ValkeyValue)(GlideString)kv.Key, (ValkeyValue)(GlideString)kv.Value)).ToArray());
    }

    public static Cmd<string, string> HashSetAsync(ValkeyKey key, HashEntry[] hashFields)
    {
        List<GlideString> args = [key.ToGlideString()];
        foreach (var entry in hashFields)
        {
            args.Add(entry.Name.ToGlideString());
            args.Add(entry.Value.ToGlideString());
        }
        return OK(RequestType.HMSet, [.. args]);
    }

    public static Cmd<long, bool> HashSetAsync(ValkeyKey key, ValkeyValue hashField, ValkeyValue value, When when)
    {
        List<GlideString> args = [key.ToGlideString(), hashField.ToGlideString(), value.ToGlideString()];

        if (when == When.NotExists)
        {
            return new(RequestType.HSetNX, [.. args], false, response => response == 1);
        }

        if (when == When.Exists)
        {
            args.Add("XX");
        }

        return new(RequestType.HSet, [.. args], false, response => response > 0);
    }

    public static Cmd<long, bool> HashDeleteAsync(ValkeyKey key, ValkeyValue hashField)
    {
        GlideString[] args = [key.ToGlideString(), hashField.ToGlideString()];
        return new(RequestType.HDel, args, false, response => response == 1);
    }

    public static Cmd<long, long> HashDeleteAsync(ValkeyKey key, ValkeyValue[] hashFields)
    {
        GlideString[] args = [key.ToGlideString(), .. hashFields.ToGlideStrings()];
        return Simple<long>(RequestType.HDel, args);
    }

    public static Cmd<long, bool> HashExistsAsync(ValkeyKey key, ValkeyValue hashField)
    {
        GlideString[] args = [key.ToGlideString(), hashField.ToGlideString()];
        return new(RequestType.HExists, args, false, response => response == 1);
    }

    public static Cmd<long, long> HashLengthAsync(ValkeyKey key)
    {
        GlideString[] args = [key.ToGlideString()];
        return Simple<long>(RequestType.HLen, args);
    }

    public static Cmd<long, long> HashStringLengthAsync(ValkeyKey key, ValkeyValue hashField)
    {
        GlideString[] args = [key.ToGlideString(), hashField.ToGlideString()];
        return Simple<long>(RequestType.HStrlen, args);
    }

    public static Cmd<HashSet<object>, ValkeyValue[]> HashValuesAsync(ValkeyKey key)
    {
        GlideString[] args = [key.ToGlideString()];
        return new(RequestType.HVals, args, false, set =>
            [.. set.Cast<GlideString>().Select(gs => (ValkeyValue)gs)]);
    }

    public static Cmd<GlideString, ValkeyValue> HashRandomFieldAsync(ValkeyKey key)
    {
        GlideString[] args = [key.ToGlideString()];
        return new(RequestType.HRandField, args, true, response => (ValkeyValue)response);
    }

    public static Cmd<HashSet<object>, ValkeyValue[]> HashRandomFieldsAsync(ValkeyKey key, long count)
    {
        GlideString[] args = [key.ToGlideString(), count.ToGlideString()];
        return new(RequestType.HRandField, args, false, set =>
            [.. set.Cast<GlideString>().Select(gs => (ValkeyValue)gs)]);
    }

    public static Cmd<Dictionary<object, object>, HashEntry[]> HashRandomFieldsWithValuesAsync(ValkeyKey key, long count)
    {
        GlideString[] args = [key.ToGlideString(), count.ToGlideString(), "WITHVALUES"];
        return new(RequestType.HRandField, args, false, dict => dict.Select(kv =>
            new HashEntry((ValkeyValue)(GlideString)kv.Key, (ValkeyValue)(GlideString)kv.Value)).ToArray());
    }
}
