// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using static Valkey.Glide.Commands.Constants.Constants;
using static Valkey.Glide.Internals.FFI;

namespace Valkey.Glide.Internals;

internal partial class Request
{
    public static Cmd<GlideString, GlideString> StringGet(GlideString key)
        => Simple<GlideString>(RequestType.Get, [key], true);

    public static Cmd<string, bool> StringSet(ValkeyKey key, ValkeyValue value)
    {
        GlideString[] args = [key.ToGlideString(), value.ToGlideString()];
        return OKToBool(RequestType.Set, args);
    }

    public static Cmd<object[], ValkeyValue[]> StringGetMultiple(ValkeyKey[] keys)
    {
        GlideString[] glideKeys = [.. keys.Select(k => k.ToGlideString())];
        return new(RequestType.MGet, glideKeys, false, array =>
            [.. array.Select(item => item is null ? ValkeyValue.Null : (ValkeyValue)(GlideString)item)]);
    }

    public static Cmd<string, bool> StringSetMultiple(KeyValuePair<ValkeyKey, ValkeyValue>[] values)
    {
        KeyValuePair<GlideString, GlideString>[] glideValues = [..
            values.Select(kvp => new KeyValuePair<GlideString, GlideString>(kvp.Key.ToGlideString(), kvp.Value.ToGlideString()))
        ];
        GlideString[] keyValuePairs = Helpers.ConvertKeyValuePairsToArray(glideValues);
        return OKToBool(RequestType.MSet, keyValuePairs);
    }

    public static Cmd<bool, bool> StringSetMultipleNX(KeyValuePair<ValkeyKey, ValkeyValue>[] values)
    {
        KeyValuePair<GlideString, GlideString>[] glideValues = [..
            values.Select(kvp => new KeyValuePair<GlideString, GlideString>(kvp.Key.ToGlideString(), kvp.Value.ToGlideString()))
        ];
        GlideString[] keyValuePairs = Helpers.ConvertKeyValuePairsToArray(glideValues);
        return Simple<bool>(RequestType.MSetNX, keyValuePairs);
    }

    public static Cmd<long, long> StringSetRange(GlideString key, long offset, GlideString value)
        => Simple<long>(RequestType.SetRange, [key, offset.ToGlideString(), value]);

    public static Cmd<GlideString, GlideString> StringGetRange(GlideString key, long start, long end)
        => Simple<GlideString>(RequestType.GetRange, [key, start.ToGlideString(), end.ToGlideString()], true);

    public static Cmd<long, long> StringLength(GlideString key)
        => Simple<long>(RequestType.Strlen, [key]);

    public static Cmd<long, long> StringAppend(ValkeyKey key, ValkeyValue value)
        => Simple<long>(RequestType.Append, [key.ToGlideString(), value.ToGlideString()]);

    public static Cmd<long, long> StringDecr(ValkeyKey key)
        => Simple<long>(RequestType.Decr, [key.ToGlideString()]);

    public static Cmd<long, long> StringDecrBy(ValkeyKey key, long decrement)
        => Simple<long>(RequestType.DecrBy, [key.ToGlideString(), decrement.ToGlideString()]);

    public static Cmd<long, long> StringIncr(ValkeyKey key)
        => Simple<long>(RequestType.Incr, [key.ToGlideString()]);

    public static Cmd<long, long> StringIncrBy(ValkeyKey key, long increment)
        => Simple<long>(RequestType.IncrBy, [key.ToGlideString(), increment.ToGlideString()]);

    public static Cmd<double, double> StringIncrByFloat(ValkeyKey key, double increment)
        => Simple<double>(RequestType.IncrByFloat, [key.ToGlideString(), increment.ToString(System.Globalization.CultureInfo.InvariantCulture).ToGlideString()]);

    public static Cmd<GlideString, ValkeyValue> StringGetDelete(ValkeyKey key)
        => new(RequestType.GetDel, [key.ToGlideString()], true, response => response is null ? ValkeyValue.Null : (ValkeyValue)response);

    public static Cmd<GlideString, ValkeyValue> StringGetSetExpiry(ValkeyKey key, TimeSpan? expiry)
    {
        List<GlideString> args = [key.ToGlideString()];
        if (expiry.HasValue)
        {
            args.Add(ExpiryKeyword.ToGlideString());
            args.Add(((long)expiry.Value.TotalSeconds).ToGlideString());
        }
        else
        {
            args.Add(PersistKeyword.ToGlideString());
        }
        return new(RequestType.GetEx, [.. args], true, response => response is null ? ValkeyValue.Null : (ValkeyValue)response);
    }

#pragma warning disable IDE0072 // Add missing cases
    public static Cmd<GlideString, ValkeyValue> StringGetSetExpiry(ValkeyKey key, DateTime expiry)
    {
        long unixTimestamp = expiry.Kind switch
        {
            DateTimeKind.Local => ((DateTimeOffset)expiry.ToUniversalTime()).ToUnixTimeSeconds(),
            DateTimeKind.Utc => ((DateTimeOffset)expiry).ToUnixTimeSeconds(),
            _ => throw new ArgumentException("Expiry time must be either Utc or Local", nameof(expiry))
        };
        GlideString[] args = [key.ToGlideString(), ExpiryAtKeyword.ToGlideString(), unixTimestamp.ToGlideString()];
        return new(RequestType.GetEx, args, true, response => response is null ? ValkeyValue.Null : (ValkeyValue)response);
    }
#pragma warning restore IDE0072 // Add missing cases

    public static Cmd<GlideString, string?> StringLongestCommonSubsequence(ValkeyKey first, ValkeyKey second)
        => new(RequestType.LCS, [first.ToGlideString(), second.ToGlideString()], true, response => response?.ToString());

    public static Cmd<long, long> StringLongestCommonSubsequenceLength(ValkeyKey first, ValkeyKey second)
        => Simple<long>(RequestType.LCS, [first.ToGlideString(), second.ToGlideString(), LenKeyword.ToGlideString()]);

    public static Cmd<object, LCSMatchResult> StringLongestCommonSubsequenceWithMatches(ValkeyKey first, ValkeyKey second, long minLength = 0)
    {
        List<GlideString> args = [first.ToGlideString(), second.ToGlideString(), IdxKeyword.ToGlideString(), MinMatchLenKeyword.ToGlideString(), minLength.ToGlideString(), WithMatchLenKeyword.ToGlideString()];
        return new(RequestType.LCS, [.. args], false, ConvertLCSMatchResult);
    }

    private static LCSMatchResult ConvertLCSMatchResult(object response) =>
        // Handle dictionary response (expected format)
        response is Dictionary<GlideString, object> dictResponse
            ? ConvertLCSMatchResultFromDictionary(dictResponse)
            : LCSMatchResult.Null;

    private static LCSMatchResult ConvertLCSMatchResultFromDictionary(Dictionary<GlideString, object> response)
    {
        List<LCSMatchResult.LCSMatch> matches = [];
        long totalLength = 0;

        // Extract length
        if (response.TryGetValue("len".ToGlideString(), out object? lengthValue))
        {
            totalLength = lengthValue is long l ? l : 0;
        }

        // Extract matches
        if (response.TryGetValue("matches".ToGlideString(), out object? matchesValue) && matchesValue is object[] matchesArray)
        {
            foreach (object matchObj in matchesArray)
            {
                if (matchObj is object[] matchArray && matchArray.Length >= 3)
                {
                    object[]? firstRange = matchArray[0] as object[];
                    object[]? secondRange = matchArray[1] as object[];
                    object matchLength = matchArray[2];

                    if (firstRange?.Length >= 2 && secondRange?.Length >= 2)
                    {
                        long firstStart = Convert.ToInt64(firstRange[0]);
                        long secondStart = Convert.ToInt64(secondRange[0]);
                        long length = Convert.ToInt64(matchLength);

                        matches.Add(new LCSMatchResult.LCSMatch(firstStart, secondStart, length));
                    }
                }
            }
        }

        return new LCSMatchResult([.. matches], totalLength);
    }
}
