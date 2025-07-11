// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using static Valkey.Glide.Internals.FFI;

namespace Valkey.Glide.Internals;

internal partial class Request
{
    public static Cmd<GlideString, ValkeyValue> HashGetAsync(ValkeyKey key, ValkeyValue hashField)
    {
        GlideString[] args = [key.ToGlideString(), hashField.ToGlideString()];
        return ToValkeyValue(RequestType.HGet, args, true);
    }

    public static Cmd<Object[], ValkeyValue[]> HashGetAsync(ValkeyKey key, ValkeyValue[] hashFields)
    {
        GlideString[] args = [key.ToGlideString(), .. hashFields.ToGlideStrings()];
        return new(RequestType.HMGet, args, false, response => [.. response.Select(item =>
            item == null ? ValkeyValue.Null : (ValkeyValue)(GlideString)item)]);
    }

    public static Cmd<Dictionary<GlideString, Object>, HashEntry[]> HashGetAllAsync(ValkeyKey key)
    {
        GlideString[] args = [key.ToGlideString()];
        return DictionaryToHashEntries(RequestType.HGetAll, args);
    }

    public static Cmd<String, String> HashSetAsync(ValkeyKey key, HashEntry[] hashFields)
    {
        List<GlideString> args = [key.ToGlideString()];
        foreach (HashEntry entry in hashFields)
        {
            args.Add(entry.Name.ToGlideString());
            args.Add(entry.Value.ToGlideString());
        }
        return OK(RequestType.HMSet, [.. args]);
    }

    public static Cmd<object, bool> HashSetAsync(ValkeyKey key, ValkeyValue hashField, ValkeyValue value, When when)
    {
        GlideString[] args = [key.ToGlideString(), hashField.ToGlideString(), value.ToGlideString()];

        WhenAlwaysOrNotExists(when);
        return value.IsNull
            ? Boolean<object>(RequestType.HDel, args[..2])
            : when == When.Always
                ? Boolean<object>(RequestType.HSet, args)
                : Boolean<object>(RequestType.HSetNX, args);
    }

    public static Cmd<long, bool> HashDeleteAsync(ValkeyKey key, ValkeyValue hashField)
    {
        GlideString[] args = [key.ToGlideString(), hashField.ToGlideString()];
        return Boolean<long>(RequestType.HDel, args);
    }

    public static Cmd<long, long> HashDeleteAsync(ValkeyKey key, ValkeyValue[] hashFields)
    {
        GlideString[] args = [key.ToGlideString(), .. hashFields.ToGlideStrings()];
        return Simple<long>(RequestType.HDel, args);
    }

    public static Cmd<bool, bool> HashExistsAsync(ValkeyKey key, ValkeyValue hashField)
    {
        GlideString[] args = [key.ToGlideString(), hashField.ToGlideString()];
        return Boolean<bool>(RequestType.HExists, args);
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

    public static Cmd<object[], ValkeyValue[]> HashValuesAsync(ValkeyKey key)
    {
        GlideString[] args = [key.ToGlideString()];
        return ObjectArrayToValkeyValueArray(RequestType.HVals, args);
    }

    public static Cmd<GlideString, ValkeyValue> HashRandomFieldAsync(ValkeyKey key)
    {
        GlideString[] args = [key.ToGlideString()];
        return ToValkeyValue(RequestType.HRandField, args, true);
    }

    public static Cmd<object[], ValkeyValue[]> HashRandomFieldsAsync(ValkeyKey key, long count)
    {
        GlideString[] args = [key.ToGlideString(), count.ToGlideString()];
        return ObjectArrayToValkeyValueArray(RequestType.HRandField, args);
    }

    public static Cmd<Object[], HashEntry[]> HashRandomFieldsWithValuesAsync(ValkeyKey key, long count)
    {
        GlideString[] args = [key.ToGlideString(), count.ToGlideString(), "WITHVALUES"];
        return ObjectArrayToHashEntries(RequestType.HRandField, args);
    }

    protected static void WhenAlwaysOrNotExists(When when)
    {
        switch (when)
        {
            case When.Always:
            case When.NotExists:
                break;
            default:
                throw new ArgumentException(when + " is not valid in this context; the permitted values are: Always, NotExists");
        }
    }


}
