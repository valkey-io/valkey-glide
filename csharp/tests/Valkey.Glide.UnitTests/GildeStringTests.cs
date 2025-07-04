// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Valkey.Glide.UnitTests;

public class GildeStringTests
{
    [Fact]
    public void Sorting()
    {
        gs[] arr = ["abc", "abcd", "abcde", "abd", "abb", "ab1"];
#if NET8_0_OR_GREATER
        Assert.Equal(new gs[] { "ab1", "abb", "abc", "abd", "abcd", "abcde" }, [.. arr.Order()]);
#endif
        Assert.Equal(new gs[] { "ab1", "abb", "abc", "abd", "abcd", "abcde" }, [.. arr.OrderBy(s => s)]);
    }

    [Fact]
    public void Comparing()
    {
        Assert.Equal(new gs("abc"), new gs("abc"));
        Assert.NotEqual(new gs("abc"), new gs("abd"));
        Assert.Equal(new gs("abc"), new gs([.. "abc".ToCharArray().Select(c => (byte)c)]));
        Assert.Equal(new gs("abc123"), new gs("abc") + "123");
        Assert.Equal(new gs("abc123"), "abc" + new gs("123"));
    }

    [Fact]
    public void Handling()
    {
        gs gs = new("abc");
        gs += "123";
        Assert.Equal(new gs("abc123"), gs);
        Assert.True(gs.CanConvertToString());
        string str = "שלום hello 汉字";
        gs = str;
        Assert.True(gs.Length > str.Length); // because GS stores UTF8 bytes
        Assert.True(gs.CanConvertToString());

        gs = new byte[] { 0, 42, 255, 243, 0, 253, 15 };
        Assert.False(gs.CanConvertToString());
        Assert.Contains("Value isn't convertible to string", gs);

        gs += "123";
        Assert.False(gs.CanConvertToString());
        Assert.Contains("Value isn't convertible to string", gs);

        gs = new gs("abc") + "123";
        Assert.True(gs.CanConvertToString());
        Assert.Equal("abc123", gs.ToString());

        Assert.Equal(new gs("abc"), "abc".ToGlideString());
    }

    [Fact]
    public void ValkeyKeyToGlideStringConversion()
    {
        // Test null ValkeyKey
        ValkeyKey nullKey = ValkeyKey.Null;
        GlideString result = nullKey;
        Assert.Equal(new GlideString([]), result);

        // Test simple string key without prefix
        ValkeyKey stringKey = new ValkeyKey("test");
        result = stringKey;
        Assert.Equal(new GlideString("test"), result);

        // Test simple byte array key without prefix
        byte[] testBytes = [1, 2, 3, 4];
        ValkeyKey byteKey = new ValkeyKey(null, testBytes);
        result = byteKey;
        Assert.Equal(new GlideString(testBytes), result);

        // Test empty string key
        ValkeyKey emptyStringKey = new ValkeyKey("");
        result = emptyStringKey;
        Assert.Equal(new GlideString(""), result);

        // Test empty byte array key
        ValkeyKey emptyByteKey = new ValkeyKey(null, new byte[0]);
        result = emptyByteKey;
        Assert.Equal(new GlideString([]), result);
    }

    [Fact]
    public void ValkeyKeyWithPrefixToGlideStringConversion()
    {
        // Test string key with prefix
        byte[] prefix = [0x70, 0x72, 0x65]; // "pre" in bytes
        ValkeyKey prefixedStringKey = ValkeyKey.WithPrefix(prefix, new ValkeyKey("fix"));
        GlideString result = prefixedStringKey;

        // Expected: prefix bytes + "fix" bytes
        byte[] expectedBytes = [0x70, 0x72, 0x65, 0x66, 0x69, 0x78]; // "prefix"
        Assert.Equal(new GlideString(expectedBytes), result);
        Assert.Equal("prefix", result.ToString());

        // Test byte array key with prefix
        byte[] valueBytes = [0x66, 0x69, 0x78]; // "fix" in bytes
        ValkeyKey prefixedByteKey = ValkeyKey.WithPrefix(prefix, new ValkeyKey(null, valueBytes));
        result = prefixedByteKey;
        Assert.Equal(new GlideString(expectedBytes), result);
    }

    [Fact]
    public void ValkeyKeyComplexPrefixScenarios()
    {
        // Test multiple prefix concatenations
        ValkeyKey baseKey = new ValkeyKey("value");
        ValkeyKey withPrefix1 = ValkeyKey.WithPrefix([0x41, 0x42], baseKey); // "AB" + "value"
        ValkeyKey withPrefix2 = ValkeyKey.WithPrefix([0x43, 0x44], withPrefix1); // "CD" + "AB" + "value"

        GlideString result = withPrefix2;
        byte[] expected = [0x43, 0x44, 0x41, 0x42, 0x76, 0x61, 0x6C, 0x75, 0x65]; // "CDABvalue"
        Assert.Equal(new GlideString(expected), result);
        Assert.Equal("CDABvalue", result.ToString());

        // Test prefix with empty value
        ValkeyKey prefixOnly = ValkeyKey.WithPrefix([0x70, 0x72, 0x65], new ValkeyKey(""));
        result = prefixOnly;
        Assert.Equal(new GlideString([0x70, 0x72, 0x65]), result);

        // Test empty prefix with value
        ValkeyKey emptyPrefixKey = ValkeyKey.WithPrefix([], new ValkeyKey("test"));
        result = emptyPrefixKey;
        Assert.Equal(new GlideString("test"), result);

        // Test null prefix with value
        ValkeyKey nullPrefixKey = ValkeyKey.WithPrefix(null, new ValkeyKey("test"));
        result = nullPrefixKey;
        Assert.Equal(new GlideString("test"), result);
    }

    [Fact]
    public void ValkeyKeyBinaryDataConversion()
    {
        // Test with binary data that's not valid UTF-8
        byte[] binaryData = [0x00, 0xFF, 0x80, 0x7F, 0xC0, 0xC1];
        ValkeyKey binaryKey = new ValkeyKey(null, binaryData);
        GlideString result = binaryKey;

        Assert.Equal(new GlideString(binaryData), result);

        // Test binary data with prefix
        byte[] prefix = [0x01, 0x02];
        ValkeyKey prefixedBinaryKey = ValkeyKey.WithPrefix(prefix, binaryKey);
        result = prefixedBinaryKey;

        byte[] expectedBinary = new byte[prefix.Length + binaryData.Length];
        System.Buffer.BlockCopy(prefix, 0, expectedBinary, 0, prefix.Length);
        System.Buffer.BlockCopy(binaryData, 0, expectedBinary, prefix.Length, binaryData.Length);

        Assert.Equal(new GlideString(expectedBinary), result);
    }
}
