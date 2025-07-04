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
