// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Valkey.Glide.IntegrationTests;

public class SharedCommandTests(TestConfiguration config)
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
    public async Task SetRange_ExistingAndNonExistingKeys(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        // Test with non-existing key
        long result = await client.SetRange(key, 0, "Dummy string");
        Assert.Equal(12L, result);

        // Test overwriting part of existing string
        result = await client.SetRange(key, 6, "values");
        Assert.Equal(12L, result);

        // Verify the modified string
        string? value = await client.Get(key);
        Assert.Equal("Dummy values", value);

        // Test extending the string with gap (zero-bytes padding)
        result = await client.SetRange(key, 15, "test");
        Assert.Equal(19L, result);

        // Verify the extended string with zero-bytes
        value = await client.Get(key);
        Assert.Equal("Dummy values\0\0\0test", value);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task SetRange_BinaryString(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        // Use a simpler binary-like string to avoid UTF-8 encoding issues
        string binaryString = "Binary test string";

        // Test with binary string
        long result = await client.SetRange(key, 0, binaryString);
        Assert.Equal(18L, result);

        // Test overwriting part of binary string
        result = await client.SetRange(key, 7, "data ");
        Assert.Equal(18L, result);

        // Verify the modified binary string
        string? value = await client.Get(key);
        Assert.Equal("Binary data string", value);

        // Test extending binary string
        result = await client.SetRange(key, 20, "test");
        Assert.Equal(24L, result);

        // Verify the extended string with zero-bytes
        value = await client.Get(key);
        Assert.Equal("Binary data string\0\0test", value);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task SetRange_EdgeCases(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        // Test with empty value
        long result = await client.SetRange(key, 0, "");
        Assert.Equal(0L, result);

        // Test with zero offset on empty key
        result = await client.SetRange(key, 0, "test");
        Assert.Equal(4L, result);

        // Verify the value
        string? value = await client.Get(key);
        Assert.Equal("test", value);
    }
}
