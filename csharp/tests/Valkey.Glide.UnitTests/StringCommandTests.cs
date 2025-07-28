// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Internals;

namespace Valkey.Glide.UnitTests;

public class StringCommandTests
{
    [Fact]
    public void StringSetMultipleNX_ValidatesArgs()
    {
        KeyValuePair<ValkeyKey, ValkeyValue>[] values = [
            new("key1", "value1"),
            new("key2", "value2")
        ];

        var request = Request.StringSetMultipleNX(values);
        string[] args = request.GetArgs();

        Assert.Equal(["MSETNX", "key1", "value1", "key2", "value2"], args);
    }

    [Fact]
    public void StringSetMultipleNX_EmptyArray_ValidatesArgs()
    {
        KeyValuePair<ValkeyKey, ValkeyValue>[] values = [];

        var request = Request.StringSetMultipleNX(values);
        string[] args = request.GetArgs();

        Assert.Equal(["MSETNX"], args);
    }

    [Fact]
    public void StringGetDelete_ValidatesArgs()
    {
        var request = Request.StringGetDelete("test_key");
        string[] args = request.GetArgs();

        Assert.Equal(["GETDEL", "test_key"], args);
    }

    [Fact]
    public void StringGetSetExpiry_TimeSpan_ValidatesArgs()
    {
        var request = Request.StringGetSetExpiry("test_key", TimeSpan.FromSeconds(60));
        string[] args = request.GetArgs();

        Assert.Equal(["GETEX", "test_key", "EX", "60"], args);
    }

    [Fact]
    public void StringGetSetExpiry_TimeSpan_Null_ValidatesArgs()
    {
        var request = Request.StringGetSetExpiry("test_key", null);
        string[] args = request.GetArgs();

        Assert.Equal(["GETEX", "test_key", "PERSIST"], args);
    }

    [Fact]
    public void StringGetSetExpiry_DateTime_ValidatesArgs()
    {
        var dateTime = new DateTime(2021, 1, 1, 0, 0, 0, DateTimeKind.Utc);
        var request = Request.StringGetSetExpiry("test_key", dateTime);
        string[] args = request.GetArgs();

        Assert.Equal(["GETEX", "test_key", "EXAT", "1609459200"], args);
    }

    [Fact]
    public void StringLongestCommonSubsequence_ValidatesArgs()
    {
        var request = Request.StringLongestCommonSubsequence("key1", "key2");
        string[] args = request.GetArgs();

        Assert.Equal(["LCS", "key1", "key2"], args);
    }

    [Fact]
    public void StringLongestCommonSubsequenceLength_ValidatesArgs()
    {
        var request = Request.StringLongestCommonSubsequenceLength("key1", "key2");
        string[] args = request.GetArgs();

        Assert.Equal(["LCS", "key1", "key2", "LEN"], args);
    }

    [Fact]
    public void StringLongestCommonSubsequenceWithMatches_ValidatesArgs()
    {
        var request = Request.StringLongestCommonSubsequenceWithMatches("key1", "key2");
        string[] args = request.GetArgs();

        Assert.Equal(["LCS", "key1", "key2", "IDX", "MINMATCHLEN", "0", "WITHMATCHLEN"], args);
    }

    [Fact]
    public void StringLongestCommonSubsequenceWithMatches_WithMinLength_ValidatesArgs()
    {
        var request = Request.StringLongestCommonSubsequenceWithMatches("key1", "key2", 5);
        string[] args = request.GetArgs();

        Assert.Equal(["LCS", "key1", "key2", "IDX", "MINMATCHLEN", "5", "WITHMATCHLEN"], args);
    }

    [Fact]
    public void StringLongestCommonSubsequenceWithMatches_ZeroMinLength_ValidatesArgs()
    {
        var request = Request.StringLongestCommonSubsequenceWithMatches("key1", "key2", 0);
        string[] args = request.GetArgs();

        Assert.Equal(["LCS", "key1", "key2", "IDX", "MINMATCHLEN", "0", "WITHMATCHLEN"], args);
    }

    [Fact]
    public void StringSetMultipleNX_Converter_ReturnsTrue()
    {
        KeyValuePair<ValkeyKey, ValkeyValue>[] values = [
            new("key1", "value1")
        ];

        var request = Request.StringSetMultipleNX(values);
        bool result = request.Converter(true);

        Assert.True(result);
    }

    [Fact]
    public void StringSetMultipleNX_Converter_ReturnsFalse()
    {
        KeyValuePair<ValkeyKey, ValkeyValue>[] values = [
            new("key1", "value1")
        ];

        var request = Request.StringSetMultipleNX(values);
        bool result = request.Converter(false);

        Assert.False(result);
    }

    [Fact]
    public void StringGetDelete_Converter_ReturnsValue()
    {
        var request = Request.StringGetDelete("test_key");
        var glideString = new GlideString("test_value");
        var result = request.Converter(glideString);

        Assert.Equal("test_value", result.ToString());
    }

    [Fact]
    public void StringGetDelete_Converter_ReturnsNull()
    {
        var request = Request.StringGetDelete("test_key");
        ValkeyValue result = request.Converter(null);

        Assert.True(result.IsNull);
    }

    [Fact]
    public void StringGetSetExpiry_TimeSpan_Converter_ReturnsValue()
    {
        var request = Request.StringGetSetExpiry("test_key", TimeSpan.FromSeconds(60));
        var glideString = new GlideString("test_value");
        var result = request.Converter(glideString);

        Assert.Equal("test_value", result.ToString());
    }

    [Fact]
    public void StringGetSetExpiry_DateTime_Converter_ReturnsValue()
    {
        var dateTime = new DateTime(2021, 1, 1, 0, 0, 0, DateTimeKind.Utc);
        var request = Request.StringGetSetExpiry("test_key", dateTime);
        var glideString = new GlideString("test_value");
        var result = request.Converter(glideString);

        Assert.Equal("test_value", result.ToString());
    }

    [Fact]
    public void StringLongestCommonSubsequence_Converter_ReturnsValue()
    {
        var request = Request.StringLongestCommonSubsequence("key1", "key2");
        var glideString = new GlideString("common");
        var result = request.Converter(glideString);

        Assert.Equal("common", result.ToString());
    }

    [Fact]
    public void StringLongestCommonSubsequenceLength_Converter_ReturnsLength()
    {
        var request = Request.StringLongestCommonSubsequenceLength("key1", "key2");
        long result = request.Converter(5L);

        Assert.Equal(5L, result);
    }
}
