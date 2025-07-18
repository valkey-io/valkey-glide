// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Valkey.Glide.IntegrationTests;

public class StringCommandTests
{
    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(TestConfiguration.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringGetAsync_ExistingKey(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        string value = "value";

        await client.StringSetAsync(key, value);
        ValkeyValue result = await client.StringGetAsync(key);

        Assert.Equal(value, result.ToString());
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(TestConfiguration.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringGetAsync_NonExistentKey(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        ValkeyValue result = await client.StringGetAsync(key);

        Assert.True(result.IsNull);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(TestConfiguration.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringGetRangeAsync_ExistingKey(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        string value = "Hello World";

        await client.StringSetAsync(key, value);
        ValkeyValue result = await client.StringGetRangeAsync(key, 0, 4);

        Assert.Equal("Hello", result.ToString());
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(TestConfiguration.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringGetRangeAsync_NonExistentKey(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        ValkeyValue result = await client.StringGetRangeAsync(key, 0, 4);

        Assert.Equal("", result.ToString());
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(TestConfiguration.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringSetRangeAsync_ExistingKey(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        string value = "Hello World";

        await client.StringSetAsync(key, value);
        long result = (long)await client.StringSetRangeAsync(key, 6, "Redis");

        Assert.Equal(11, result);

        ValkeyValue newValue = await client.StringGetAsync(key);
        Assert.Equal("Hello Redis", newValue.ToString());
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(TestConfiguration.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringSetRangeAsync_NonExistentKey(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        long result = (long)await client.StringSetRangeAsync(key, 6, "Redis");

        Assert.Equal(11, result);

        ValkeyValue newValue = await client.StringGetAsync(key);
        Assert.Equal("\0\0\0\0\0\0Redis", newValue.ToString());
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(TestConfiguration.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringLengthAsync_ExistingKey(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        string value = "Hello World";

        await client.StringSetAsync(key, value);
        long result = await client.StringLengthAsync(key);

        Assert.Equal(11, result);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(TestConfiguration.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringLengthAsync_NonExistentKey(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        long result = await client.StringLengthAsync(key);

        Assert.Equal(0, result);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(TestConfiguration.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringAppendAsync_ExistingKey(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        string value = "Hello";

        await client.StringSetAsync(key, value);
        long result = await client.StringAppendAsync(key, " World");

        Assert.Equal(11, result);

        ValkeyValue newValue = await client.StringGetAsync(key);
        Assert.Equal("Hello World", newValue.ToString());
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(TestConfiguration.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringAppendAsync_NonExistentKey(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        long result = await client.StringAppendAsync(key, "Hello World");

        Assert.Equal(11, result);

        ValkeyValue newValue = await client.StringGetAsync(key);
        Assert.Equal("Hello World", newValue.ToString());
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(TestConfiguration.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringGetAsync_Multiple_ExistingKeys(BaseClient client)
    {
        string key1 = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();
        string key3 = Guid.NewGuid().ToString();
        string value1 = "value1";
        string value2 = "value2";

        await client.StringSetAsync(key1, value1);
        await client.StringSetAsync(key2, value2);

        ValkeyValue[] results = await client.StringGetAsync([key1, key2, key3]);

        Assert.Equal(3, results.Length);
        Assert.Equal(value1, results[0].ToString());
        Assert.Equal(value2, results[1].ToString());
        Assert.True(results[2].IsNull);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(TestConfiguration.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringSetAsync_Multiple(BaseClient client)
    {
        string key1 = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();
        string value1 = "value1";
        string value2 = "value2";

        bool result = await client.StringSetAsync([
            new KeyValuePair<ValkeyKey, ValkeyValue>(key1, value1),
            new KeyValuePair<ValkeyKey, ValkeyValue>(key2, value2)
        ]);

        Assert.True(result);

        ValkeyValue[] values = await client.StringGetAsync([key1, key2]);
        Assert.Equal(value1, values[0].ToString());
        Assert.Equal(value2, values[1].ToString());
    }

    internal static async Task GetAndSetValuesAsync(BaseClient client, string key, string value)
    {
        await client.StringSetAsync(key, value);
        ValkeyValue result = await client.StringGetAsync(key);
        Assert.Equal(value, result.ToString());
    }

    internal static async Task GetAndSetRandomValuesAsync(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        string value = Guid.NewGuid().ToString();
        await GetAndSetValuesAsync(client, key, value);
    }
}
