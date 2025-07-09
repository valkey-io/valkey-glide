// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Valkey.Glide.IntegrationTests;

[Collection("GlideTests")]
public class StringCommandTests(TestConfiguration config)
{
    public TestConfiguration Config { get; } = config;

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringSetAsync_StringGetAsync_SingleKey(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        string value = Guid.NewGuid().ToString();

        bool result = await client.StringSetAsync(key, value);
        Assert.True(result);

        ValkeyValue retrievedValue = await client.StringGetAsync(key);
        Assert.Equal(value, retrievedValue.ToString());
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringGetAsync_NonExistentKey(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        ValkeyValue value = await client.StringGetAsync(key);
        Assert.True(value.IsNull);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringGetAsync_MultipleKeys(BaseClient client)
    {
        string key1 = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();
        string key3 = Guid.NewGuid().ToString();
        string value1 = Guid.NewGuid().ToString();
        string value2 = Guid.NewGuid().ToString();

        // Set key1 and key2, leave key3 unset
        await client.StringSetAsync(key1, value1);
        await client.StringSetAsync(key2, value2);

        ValkeyKey[] keys = [key1, key2, key3];
        ValkeyValue[] values = await client.StringGetAsync(keys);

        Assert.Equal(3, values.Length);
        Assert.Equal(value1, values[0].ToString());
        Assert.Equal(value2, values[1].ToString());
        Assert.True(values[2].IsNull);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringGetAsync_NonExistentKeys(BaseClient client)
    {
        string key1 = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();

        ValkeyKey[] keys = [key1, key2];
        ValkeyValue[] values = await client.StringGetAsync(keys);

        Assert.Equal(2, values.Length);
        Assert.True(values[0].IsNull);
        Assert.True(values[1].IsNull);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringSetAsync_MultipleKeyValuePairs(BaseClient client)
    {
        string key1 = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();
        string value1 = Guid.NewGuid().ToString();
        string value2 = Guid.NewGuid().ToString();

        KeyValuePair<ValkeyKey, ValkeyValue>[] keyValuePairs = [
            new(key1, value1),
            new(key2, value2)
        ];

        bool result = await client.StringSetAsync(keyValuePairs);
        Assert.True(result);

        ValkeyValue retrievedValue1 = await client.StringGetAsync(key1);
        ValkeyValue retrievedValue2 = await client.StringGetAsync(key2);

        Assert.Equal(value1, retrievedValue1.ToString());
        Assert.Equal(value2, retrievedValue2.ToString());
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringSetAsync_KeyValuePairs_WithUnicodeValues(BaseClient client)
    {
        string key1 = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();
        string unicodeValue1 = "שלום hello 汉字";
        string unicodeValue2 = "مرحبا world 🌍";

        KeyValuePair<ValkeyKey, ValkeyValue>[] values = [
            new(key1, unicodeValue1),
            new(key2, unicodeValue2)
        ];

        bool result = await client.StringSetAsync(values);
        Assert.True(result);

        ValkeyKey[] keys = [key1, key2];
        ValkeyValue[] retrievedValues = await client.StringGetAsync(keys);

        Assert.Equal(2, retrievedValues.Length);
        Assert.Equal(unicodeValue1, retrievedValues[0].ToString());
        Assert.Equal(unicodeValue2, retrievedValues[1].ToString());
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringSetAsync_KeyValuePairs_WithEmptyValues(BaseClient client)
    {
        string key1 = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();

        KeyValuePair<ValkeyKey, ValkeyValue>[] values = [
            new(key1, ""),
            new(key2, "non-empty")
        ];

        bool result = await client.StringSetAsync(values);
        Assert.True(result);

        ValkeyKey[] keys = [key1, key2];
        ValkeyValue[] retrievedValues = await client.StringGetAsync(keys);

        Assert.Equal(2, retrievedValues.Length);
        Assert.Equal("", retrievedValues[0].ToString());
        Assert.Equal("non-empty", retrievedValues[1].ToString());
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringSetAsync_KeyValuePairs_OverwriteExistingKeys(BaseClient client)
    {
        string key1 = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();
        string initialValue1 = "initial1";
        string initialValue2 = "initial2";
        string newValue1 = "new1";
        string newValue2 = "new2";

        // Set initial values
        await client.StringSetAsync(key1, initialValue1);
        await client.StringSetAsync(key2, initialValue2);

        // Overwrite with StringSetAsync
        KeyValuePair<ValkeyKey, ValkeyValue>[] values = [
            new(key1, newValue1),
            new(key2, newValue2)
        ];

        bool result = await client.StringSetAsync(values);
        Assert.True(result);

        // Verify values were overwritten
        ValkeyKey[] keys = [key1, key2];
        ValkeyValue[] retrievedValues = await client.StringGetAsync(keys);

        Assert.Equal(2, retrievedValues.Length);
        Assert.Equal(newValue1, retrievedValues[0].ToString());
        Assert.Equal(newValue2, retrievedValues[1].ToString());
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringLengthAsync_ExistingKey(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        string value = "Hello World";

        await client.StringSetAsync(key, value);
        long length = await client.StringLengthAsync(key);
        Assert.Equal(value.Length, length);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringLengthAsync_NonExistentKey(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        long length = await client.StringLengthAsync(key);
        Assert.Equal(0, length);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringGetRangeAsync_ExistingKey(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        string value = "Hello World";

        await client.StringSetAsync(key, value);
        ValkeyValue result = await client.StringGetRangeAsync(key, 0, 4);
        Assert.Equal("Hello", result.ToString());
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringGetRangeAsync_NonExistentKey(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        ValkeyValue result = await client.StringGetRangeAsync(key, 0, 4);
        Assert.Equal("", result.ToString());
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringSetRangeAsync_ExistingKey(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        string initialValue = "Hello World";

        await client.StringSetAsync(key, initialValue);
        ValkeyValue newLength = await client.StringSetRangeAsync(key, 6, "Valkey");
        Assert.Equal(12, (long)newLength);

        ValkeyValue value = await client.StringGetAsync(key);
        Assert.Equal("Hello Valkey", value.ToString());
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringSetRangeAsync_NonExistentKey(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        ValkeyValue newLength = await client.StringSetRangeAsync(key, 0, "Hello");
        Assert.Equal(5, (long)newLength);

        ValkeyValue value = await client.StringGetAsync(key);
        Assert.Equal("Hello", value.ToString());
    }

    // Utility methods for other tests
    internal static async Task GetAndSetValuesAsync(BaseClient client, string key, string value)
    {
        bool result = await client.StringSetAsync(key, value);
        Assert.True(result);

        ValkeyValue retrievedValue = await client.StringGetAsync(key);
        Assert.Equal(value, retrievedValue.ToString());
    }

    internal static async Task GetAndSetRandomValuesAsync(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        string value = Guid.NewGuid().ToString();

        await GetAndSetValuesAsync(client, key, value);
    }
}
