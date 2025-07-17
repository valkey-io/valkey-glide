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

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringAppendAsync_ExistingKey(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        string value1 = "Hello";
        string value2 = " World";    // Set initial value
        await client.StringSetAsync(key, value1);

        // Append to existing key
        long newLength = await client.StringAppendAsync(key, value2);
        Assert.Equal(value1.Length + value2.Length, newLength);

        // Verify the final value
        ValkeyValue retrievedValue = await client.StringGetAsync(key);
        Assert.Equal(value1 + value2, retrievedValue.ToString());
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringAppendAsync_NonExistentKey(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        string value = "Hello World";

        // Append to non-existent key (should create it)
        long newLength = await client.StringAppendAsync(key, value);
        Assert.Equal(value.Length, newLength);

        // Verify the value was created
        ValkeyValue retrievedValue = await client.StringGetAsync(key);
        Assert.Equal(value, retrievedValue.ToString());
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringAppendAsync_MultipleAppends(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        string value1 = "Hello";
        string value2 = ";";
        string value3 = "World";
        string value4 = "!";

        // First append
        long length1 = await client.StringAppendAsync(key, value1);
        Assert.Equal(value1.Length, length1);

        // Second append
        long length2 = await client.StringAppendAsync(key, value2);
        Assert.Equal(value1.Length + value2.Length, length2);

        // Third append
        long length3 = await client.StringAppendAsync(key, value3);
        Assert.Equal(value1.Length + value2.Length + value3.Length, length3);

        // Fourth append
        long length4 = await client.StringAppendAsync(key, value4);
        Assert.Equal(value1.Length + value2.Length + value3.Length + value4.Length, length4);

        // Verify the final value
        ValkeyValue retrievedValue = await client.StringGetAsync(key);
        Assert.Equal(value1 + value2 + value3 + value4, retrievedValue.ToString());
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringAppendAsync_EmptyValue(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        string initialValue = "Hello";
        string emptyValue = "";    // Set initial value
        await client.StringSetAsync(key, initialValue);

        // Append empty value
        long newLength = await client.StringAppendAsync(key, emptyValue);
        Assert.Equal(initialValue.Length, newLength); // Length should remain the same

        // Verify the value hasnt changed
        ValkeyValue retrievedValue = await client.StringGetAsync(key);
        Assert.Equal(initialValue, retrievedValue.ToString());
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringAppendAsync_UnicodeValues(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        string value1 = "שלום";
        string value2 = " hello";
        string value3 = "汉字";

        // Get byte lengths
        int byteLength1 = System.Text.Encoding.UTF8.GetByteCount(value1);
        int byteLength2 = System.Text.Encoding.UTF8.GetByteCount(value2);
        int byteLength3 = System.Text.Encoding.UTF8.GetByteCount(value3);

        // First append
        long length1 = await client.StringAppendAsync(key, value1);
        Assert.Equal(byteLength1, length1);

        // Second append
        long length2 = await client.StringAppendAsync(key, value2);
        Assert.Equal(byteLength1 + byteLength2, length2);

        // Third append
        long length3 = await client.StringAppendAsync(key, value3);
        Assert.Equal(byteLength1 + byteLength2 + byteLength3, length3);

        // Verify the final value
        ValkeyValue retrievedValue = await client.StringGetAsync(key);
        Assert.Equal(value1 + value2 + value3, retrievedValue.ToString());
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

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringDecrAsync_ExistingKey(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        // Set initial value
        await client.StringSetAsync(key, "10");

        // Decrement by 1
        long result = await client.StringDecrAsync(key);
        Assert.Equal(9, result);

        // Verify the value was decremented
        ValkeyValue value = await client.StringGetAsync(key);
        Assert.Equal("9", value.ToString());
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringDecrAsync_NonExistentKey(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        // Decrement non-existent key (should create it with value -1)
        long result = await client.StringDecrAsync(key);
        Assert.Equal(-1, result);

        // Verify the key was created with value -1
        ValkeyValue value = await client.StringGetAsync(key);
        Assert.Equal("-1", value.ToString());
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringDecrByAsync_ExistingKey(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        // Set initial value
        await client.StringSetAsync(key, "10");

        // Decrement by 5
        long result = await client.StringDecrByAsync(key, 5);
        Assert.Equal(5, result);

        // Verify the value was decremented
        ValkeyValue value = await client.StringGetAsync(key);
        Assert.Equal("5", value.ToString());
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringDecrByAsync_NonExistentKey(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        // Decrement non-existent key by 5 (should create it with value -5)
        long result = await client.StringDecrByAsync(key, 5);
        Assert.Equal(-5, result);

        // Verify the key was created with value -5
        ValkeyValue value = await client.StringGetAsync(key);
        Assert.Equal("-5", value.ToString());
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringDecrByAsync_NegativeAmount(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        // Set initial value
        await client.StringSetAsync(key, "10");

        // Decrement by -5 (effectively incrementing by 5)
        long result = await client.StringDecrByAsync(key, -5);
        Assert.Equal(15, result);

        // Verify the value was incremented
        ValkeyValue value = await client.StringGetAsync(key);
        Assert.Equal("15", value.ToString());
    }
}
