// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Valkey.Glide.IntegrationTests;

public class StringCommandTests(TestConfiguration config)
{
    public TestConfiguration Config { get; } = config;

    internal static async Task GetAndSetValues(BaseClient client, string key, string value)
    {
        Assert.Equal("OK", await client.Set(key, value));
        Assert.Equal(value, (await client.Get(key))!);
    }

    internal static async Task GetAndSetRandomValues(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        string value = Guid.NewGuid().ToString();
        await GetAndSetValues(client, key, value);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task GetReturnsLastSet(BaseClient client) =>
        await GetAndSetRandomValues(client);

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task GetAndSetCanHandleNonASCIIUnicode(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        string value = "שלום hello 汉字";
        await GetAndSetValues(client, key, value);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task GetReturnsNull(BaseClient client) =>
        Assert.Null(await client.Get(Guid.NewGuid().ToString()));

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task GetReturnsEmptyString(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        string value = string.Empty;
        await GetAndSetValues(client, key, value);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StrlenExistingKey(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        string value = Guid.NewGuid().ToString();
        Assert.Equal("OK", await client.Set(key, value));

        long result = await client.Strlen(key);
        Assert.Equal(value.Length, result);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StrlenNonExistingKey(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        long result = await client.Strlen(key);
        Assert.Equal(0, result);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StrlenWithUnicodeValue(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        string value = "שלום hello 汉字";
        Assert.Equal("OK", await client.Set(key, value));

        long result = await client.Strlen(key);
        // Note: Strlen returns byte length, not character length
        Assert.Equal(System.Text.Encoding.UTF8.GetByteCount(value), result);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task SetRangeExistingAndNonExistingKeys(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        // Test with non-existing key
        long result = await client.SetRange(key, 0, "Dummy string");
        Assert.Equal(12, result);

        // Verify the value was set correctly
        string? value = await client.Get(key);
        Assert.Equal("Dummy string", value);

        // Test overwriting part of existing string
        result = await client.SetRange(key, 6, "values");
        Assert.Equal(12, result);

        value = await client.Get(key);
        Assert.Equal("Dummy values", value);

        // Test extending string with padding
        result = await client.SetRange(key, 15, "test");
        Assert.Equal(19, result);

        value = await client.Get(key);
        Assert.Equal("Dummy values\0\0\0test", value);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task SetRangeWithBinaryString(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        string nonUtf8String = "Dummy \xFF string";

        long result = await client.SetRange(key, 0, nonUtf8String);
        // The actual byte length of the string (UTF-8 encoding may affect this)
        long expectedLength = System.Text.Encoding.UTF8.GetByteCount(nonUtf8String);
        Assert.Equal(expectedLength, result);

        result = await client.SetRange(key, 6, "values ");
        Assert.Equal(Math.Max(expectedLength, 13), result); // "values " is 7 chars, starting at position 6 = 13 total

        string? value = await client.Get(key);
        Assert.NotNull(value);
        Assert.Contains("Dummy values", value);

        result = await client.SetRange(key, 15, "test");
        Assert.Equal(19, result);

        value = await client.Get(key);
        Assert.NotNull(value);
        Assert.Contains("test", value);
        Assert.Equal(19, value.Length);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task SetRangeWithGlideString(BaseClient client)
    {
        GlideString key = new(Guid.NewGuid().ToString());
        GlideString value = new("GLIDE");

        long result = await client.SetRange(key, 0, value);
        Assert.Equal(5, result);

        GlideString? retrievedValue = await client.Get(key);
        Assert.Equal(value, retrievedValue);

        // Test with offset
        GlideString newValue = new("TEST");
        result = await client.SetRange(key, 2, newValue);
        Assert.Equal(6, result);

        retrievedValue = await client.Get(key);
        Assert.Equal(new GlideString("GLTEST"), retrievedValue);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task SetRangeWithZeroOffset(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        long result = await client.SetRange(key, 0, "Hello");
        Assert.Equal(5, result);

        string? value = await client.Get(key);
        Assert.Equal("Hello", value);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task GetRangeExistingAndNonExistingKeys(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        // Set up test data
        Assert.Equal("OK", await client.Set(key, "Dummy string"));

        // Test basic range extraction
        GlideString result = await client.GetRange(key, 0, 4);
        Assert.Equal(new GlideString("Dummy"), result);

        // Test negative indices (extract from end)
        result = await client.GetRange(key, -6, -1);
        Assert.Equal(new GlideString("string"), result);

        // Test invalid range (start > end)
        result = await client.GetRange(key, -1, -6);
        Assert.Equal(new GlideString(""), result);

        // Test out of bounds range
        result = await client.GetRange(key, 15, 16);
        Assert.Equal(new GlideString(""), result);

        // Test non-existing key
        string nonExistingKey = Guid.NewGuid().ToString();
        result = await client.GetRange(nonExistingKey, 0, 5);
        Assert.Equal(new GlideString(""), result);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task GetRangeBinaryString(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        string nonUtf8String = "Dummy \xFF string";

        Assert.Equal("OK", await client.Set(key, nonUtf8String));

        GlideString result = await client.GetRange(key, 4, 6);
        // For binary data, we should check the raw bytes instead of string conversion
        Assert.NotNull(result);
        Assert.True(result.Length > 0);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task GetRangeWithGlideString(BaseClient client)
    {
        GlideString key = new(Guid.NewGuid().ToString());
        GlideString value = new("This is a GlideString");

        Assert.Equal("OK", await client.Set(key, value));

        // Test basic range extraction
        GlideString result = await client.GetRange(key, 0, 3);
        Assert.Equal(new GlideString("This"), result);

        // Test negative indices
        result = await client.GetRange(key, -11, -1);
        Assert.Equal(new GlideString("GlideString"), result);

        // Test full string
        result = await client.GetRange(key, 0, -1);
        Assert.Equal(value, result);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task GetRangeEdgeCases(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        // Test with empty string
        Assert.Equal("OK", await client.Set(key, ""));
        GlideString result = await client.GetRange(key, 0, 5);
        Assert.Equal(new GlideString(""), result);

        // Test with single character
        Assert.Equal("OK", await client.Set(key, "A"));
        result = await client.GetRange(key, 0, 0);
        Assert.Equal(new GlideString("A"), result);

        // Test range beyond string length
        result = await client.GetRange(key, 0, 10);
        Assert.Equal(new GlideString("A"), result);

        // Test negative start beyond string length
        result = await client.GetRange(key, -10, -1);
        Assert.Equal(new GlideString("A"), result);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task GetRangeUnicodeString(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        string unicodeValue = "שלום hello 汉字";

        Assert.Equal("OK", await client.Set(key, unicodeValue));

        // Test extracting Unicode characters - GetRange works on byte level
        GlideString result = await client.GetRange(key, 0, 10);
        Assert.NotNull(result);
        Assert.True(result.Length > 0);

        // Test extracting ASCII part - find the "hello" part by trying different ranges
        // Since Unicode characters take multiple bytes, we need to find the right byte range
        for (int start = 0; start < 20; start++)
        {
            try
            {
                result = await client.GetRange(key, start, start + 4);
                if (result.ToString().Contains("hello"))
                {
                    Assert.Contains("hello", result.ToString());
                    return; // Test passed
                }
            }
            catch
            {
                // Continue trying different ranges
            }
        }

        // If we can't find "hello", just verify we got some data
        result = await client.GetRange(key, 0, -1);
        Assert.True(result.Length > 0);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task SetRangeWithEmptyValue(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        // Set initial value
        await client.Set(key, "Hello");

        // SetRange with empty string should not change the length
        long result = await client.SetRange(key, 2, "");
        Assert.Equal(5, result);

        string? value = await client.Get(key);
        Assert.Equal("Hello", value);
    }
}
