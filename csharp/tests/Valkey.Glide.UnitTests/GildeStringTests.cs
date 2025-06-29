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
}
