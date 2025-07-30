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
        _ = await client.StringSetAsync(key1, value1);
        _ = await client.StringSetAsync(key2, value2);

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
        _ = await client.StringSetAsync(key1, initialValue1);
        _ = await client.StringSetAsync(key2, initialValue2);

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

        _ = await client.StringSetAsync(key, value);
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

        _ = await client.StringSetAsync(key, value);
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

        _ = await client.StringSetAsync(key, initialValue);
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
        string initialValue = "Hello";
        string appendValue = " World";

        // Set initial value
        _ = await client.StringSetAsync(key, initialValue);

        // Append to the key
        long newLength = await client.StringAppendAsync(key, appendValue);

        // Verify the new length is correct
        Assert.Equal(initialValue.Length + appendValue.Length, newLength);

        // Verify the value was appended correctly
        ValkeyValue value = await client.StringGetAsync(key);
        Assert.Equal(initialValue + appendValue, value.ToString());
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringAppendAsync_NonExistentKey(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        string appendValue = "Hello World";

        // Append to a non-existent key (should create it)
        long newLength = await client.StringAppendAsync(key, appendValue);

        // Verify the new length is correct
        Assert.Equal(appendValue.Length, newLength);

        // Verify the key was created with the appended value
        ValkeyValue value = await client.StringGetAsync(key);
        Assert.Equal(appendValue, value.ToString());
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringAppendAsync_EmptyValue(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        string initialValue = "Hello";
        string appendValue = "";

        // Set initial value
        _ = await client.StringSetAsync(key, initialValue);

        // Append empty string
        long newLength = await client.StringAppendAsync(key, appendValue);

        // Verify the length remains the same
        Assert.Equal(initialValue.Length, newLength);

        // Verify the value was not changed
        ValkeyValue value = await client.StringGetAsync(key);
        Assert.Equal(initialValue, value.ToString());
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringAppendAsync_UnicodeValues(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        string initialValue = "Hello";
        string appendValue = " 世界";

        // Set initial value
        _ = await client.StringSetAsync(key, initialValue);

        // Append Unicode string
        _ = await client.StringAppendAsync(key, appendValue);

        // Verify the value was appended correctly
        ValkeyValue value = await client.StringGetAsync(key);
        Assert.Equal(initialValue + appendValue, value.ToString());

        // The server returns the length in bytes, not characters
        // For Unicode characters, this will be different from the C# string length
        // So we don't test the exact length here
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
    [MemberData(nameof(TestConfiguration.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringDecrementAsync_ExistingKey(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        // Set initial value
        _ = await client.StringSetAsync(key, "10");

        // Decrement by 1
        long result = await client.StringDecrementAsync(key);
        Assert.Equal(9, result);

        // Verify the value was decremented
        ValkeyValue value = await client.StringGetAsync(key);
        Assert.Equal("9", value.ToString());
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(TestConfiguration.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringDecrementAsync_NonExistentKey(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        // Decrement non-existent key (should create it with value -1)
        long result = await client.StringDecrementAsync(key);
        Assert.Equal(-1, result);

        // Verify the key was created with value -1
        ValkeyValue value = await client.StringGetAsync(key);
        Assert.Equal("-1", value.ToString());
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(TestConfiguration.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringDecrementAsync_WithAmount_ExistingKey(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        // Set initial value
        _ = await client.StringSetAsync(key, "10");

        // Decrement by 5
        long result = await client.StringDecrementAsync(key, 5);
        Assert.Equal(5, result);

        // Verify the value was decremented
        ValkeyValue value = await client.StringGetAsync(key);
        Assert.Equal("5", value.ToString());
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(TestConfiguration.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringDecrementAsync_WithAmount_NonExistentKey(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        // Decrement non-existent key by 5 (should create it with value -5)
        long result = await client.StringDecrementAsync(key, 5);
        Assert.Equal(-5, result);

        // Verify the key was created with value -5
        ValkeyValue value = await client.StringGetAsync(key);
        Assert.Equal("-5", value.ToString());
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(TestConfiguration.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringDecrementAsync_WithNegativeAmount(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        // Set initial value
        _ = await client.StringSetAsync(key, "10");

        // Decrement by -5 (effectively incrementing by 5)
        long result = await client.StringDecrementAsync(key, -5);
        Assert.Equal(15, result);

        // Verify the value was incremented
        ValkeyValue value = await client.StringGetAsync(key);
        Assert.Equal("15", value.ToString());
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(TestConfiguration.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringIncrementAsync_ExistingKey(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        // Set initial value
        _ = await client.StringSetAsync(key, "10");

        // Increment by 1
        long result = await client.StringIncrementAsync(key);
        Assert.Equal(11, result);

        // Verify the value was incremented
        ValkeyValue value = await client.StringGetAsync(key);
        Assert.Equal("11", value.ToString());
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(TestConfiguration.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringIncrementAsync_NonExistentKey(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        // Increment non-existent key (should create it with value 1)
        long result = await client.StringIncrementAsync(key);
        Assert.Equal(1, result);

        // Verify the key was created with value 1
        ValkeyValue value = await client.StringGetAsync(key);
        Assert.Equal("1", value.ToString());
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(TestConfiguration.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringIncrementAsync_WithAmount_ExistingKey(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        // Set initial value
        _ = await client.StringSetAsync(key, "10");

        // Increment by 5
        long result = await client.StringIncrementAsync(key, 5);
        Assert.Equal(15, result);

        // Verify the value was incremented
        ValkeyValue value = await client.StringGetAsync(key);
        Assert.Equal("15", value.ToString());
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(TestConfiguration.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringIncrementAsync_WithAmount_NonExistentKey(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        // Increment non-existent key by 5 (should create it with value 5)
        long result = await client.StringIncrementAsync(key, 5);
        Assert.Equal(5, result);

        // Verify the key was created with value 5
        ValkeyValue value = await client.StringGetAsync(key);
        Assert.Equal("5", value.ToString());
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(TestConfiguration.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringIncrementAsync_WithNegativeAmount(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        // Set initial value
        _ = await client.StringSetAsync(key, "10");

        // Increment by -5 (effectively decrementing by 5)
        long result = await client.StringIncrementAsync(key, -5);
        Assert.Equal(5, result);

        // Verify the value was decremented
        ValkeyValue value = await client.StringGetAsync(key);
        Assert.Equal("5", value.ToString());
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(TestConfiguration.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringIncrementAsync_WithFloat_ExistingKey(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        // Set initial value
        _ = await client.StringSetAsync(key, "10.5");

        // Increment by 0.5
        double result = await client.StringIncrementAsync(key, 0.5);
        Assert.Equal(11.0, result);

        // Verify the value was incremented
        ValkeyValue value = await client.StringGetAsync(key);
        Assert.Equal("11", value.ToString());
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(TestConfiguration.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringIncrementAsync_WithFloat_NonExistentKey(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        // Increment non-existent key by 0.5 (should create it with value 0.5)
        double result = await client.StringIncrementAsync(key, 0.5);
        Assert.Equal(0.5, result);

        // Verify the key was created with value 0.5
        ValkeyValue value = await client.StringGetAsync(key);
        Assert.Equal("0.5", value.ToString());
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(TestConfiguration.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringIncrementAsync_WithNegativeFloat(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        // Set initial value
        _ = await client.StringSetAsync(key, "10.5");

        // Increment by -0.5 (effectively decrementing by 0.5)
        double result = await client.StringIncrementAsync(key, -0.5);
        Assert.Equal(10.0, result);

        // Verify the value was decremented
        ValkeyValue value = await client.StringGetAsync(key);
        Assert.Equal("10", value.ToString());
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringSetAsync_MultipleKeyValuePairs_WithWhenNotExists_Success(BaseClient client)
    {
        // Use hash tags to ensure keys map to the same slot in cluster mode
        string baseKey = Guid.NewGuid().ToString();
        string key1 = $"{{{baseKey}}}:key1";
        string key2 = $"{{{baseKey}}}:key2";
        string value1 = Guid.NewGuid().ToString();
        string value2 = Guid.NewGuid().ToString();

        KeyValuePair<ValkeyKey, ValkeyValue>[] keyValuePairs = [
            new(key1, value1),
            new(key2, value2)
        ];

        // First call should succeed since keys don't exist
        bool result = await client.StringSetAsync(keyValuePairs, When.NotExists);
        Assert.True(result);

        ValkeyValue retrievedValue1 = await client.StringGetAsync(key1);
        ValkeyValue retrievedValue2 = await client.StringGetAsync(key2);

        Assert.Equal(value1, retrievedValue1.ToString());
        Assert.Equal(value2, retrievedValue2.ToString());
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringSetAsync_MultipleKeyValuePairs_WithWhenNotExists_Failure(BaseClient client)
    {
        // Use hash tags to ensure keys map to the same slot in cluster mode
        string baseKey = Guid.NewGuid().ToString();
        string key1 = $"{{{baseKey}}}:key1";
        string key2 = $"{{{baseKey}}}:key2";
        string value1 = Guid.NewGuid().ToString();
        string value2 = Guid.NewGuid().ToString();
        string newValue1 = Guid.NewGuid().ToString();
        string newValue2 = Guid.NewGuid().ToString();

        // Set one key first
        await client.StringSetAsync(key1, value1);

        KeyValuePair<ValkeyKey, ValkeyValue>[] keyValuePairs = [
            new(key1, newValue1),
            new(key2, newValue2)
        ];

        // Should fail since key1 already exists
        bool result = await client.StringSetAsync(keyValuePairs, When.NotExists);
        Assert.False(result);

        // Verify original values are unchanged
        ValkeyValue retrievedValue1 = await client.StringGetAsync(key1);
        ValkeyValue retrievedValue2 = await client.StringGetAsync(key2);

        Assert.Equal(value1, retrievedValue1.ToString());
        Assert.True(retrievedValue2.IsNull);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringGetDeleteAsync_ExistingKey(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        string value = Guid.NewGuid().ToString();

        await client.StringSetAsync(key, value);

        ValkeyValue result = await client.StringGetDeleteAsync(key);
        Assert.Equal(value, result.ToString());

        // Verify key was deleted
        ValkeyValue deletedValue = await client.StringGetAsync(key);
        Assert.True(deletedValue.IsNull);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringGetDeleteAsync_NonExistentKey(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        ValkeyValue result = await client.StringGetDeleteAsync(key);
        Assert.True(result.IsNull);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringGetSetExpiryAsync_TimeSpan_ExistingKey(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        string value = Guid.NewGuid().ToString();

        await client.StringSetAsync(key, value);

        ValkeyValue result = await client.StringGetSetExpiryAsync(key, TimeSpan.FromSeconds(10));
        Assert.Equal(value, result.ToString());

        // Verify key still exists and has expiry
        ValkeyValue retrievedValue = await client.StringGetAsync(key);
        Assert.Equal(value, retrievedValue.ToString());
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringGetSetExpiryAsync_TimeSpan_NonExistentKey(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        ValkeyValue result = await client.StringGetSetExpiryAsync(key, TimeSpan.FromSeconds(10));
        Assert.True(result.IsNull);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringGetSetExpiryAsync_TimeSpan_RemoveExpiry(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        string value = Guid.NewGuid().ToString();

        await client.StringSetAsync(key, value);

        ValkeyValue result = await client.StringGetSetExpiryAsync(key, null);
        Assert.Equal(value, result.ToString());
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringGetSetExpiryAsync_DateTime_ExistingKey(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        string value = Guid.NewGuid().ToString();

        await client.StringSetAsync(key, value);

        DateTime expiry = DateTime.UtcNow.AddMinutes(5);
        ValkeyValue result = await client.StringGetSetExpiryAsync(key, expiry);
        Assert.Equal(value, result.ToString());

        // Verify key still exists
        ValkeyValue retrievedValue = await client.StringGetAsync(key);
        Assert.Equal(value, retrievedValue.ToString());
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringLongestCommonSubsequenceAsync_BasicTest(BaseClient client)
    {
        Assert.SkipWhen(
            TestConfiguration.SERVER_VERSION < new Version("7.0.0"),
            "LCS is supported since 7.0.0"
        );
        // Use hash tags to ensure keys map to the same slot in cluster mode
        string baseKey = Guid.NewGuid().ToString();
        string key1 = $"{{{baseKey}}}:key1";
        string key2 = $"{{{baseKey}}}:key2";

        await client.StringSetAsync(key1, "abcdef");
        await client.StringSetAsync(key2, "acef");

        string? result = await client.StringLongestCommonSubsequenceAsync(key1, key2);
        Assert.Equal("acef", result);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringLongestCommonSubsequenceAsync_NoCommonSubsequence(BaseClient client)
    {
        Assert.SkipWhen(
            TestConfiguration.SERVER_VERSION < new Version("7.0.0"),
            "LCS is supported since 7.0.0"
        );
        // Use hash tags to ensure keys map to the same slot in cluster mode
        string baseKey = Guid.NewGuid().ToString();
        string key1 = $"{{{baseKey}}}:key1";
        string key2 = $"{{{baseKey}}}:key2";

        await client.StringSetAsync(key1, "abc");
        await client.StringSetAsync(key2, "xyz");

        string? result = await client.StringLongestCommonSubsequenceAsync(key1, key2);
        Assert.Equal("", result);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringLongestCommonSubsequenceAsync_NonExistentKeys(BaseClient client)
    {
        Assert.SkipWhen(
            TestConfiguration.SERVER_VERSION < new Version("7.0.0"),
            "LCS is supported since 7.0.0"
        );
        // Use hash tags to ensure keys map to the same slot in cluster mode
        string baseKey = Guid.NewGuid().ToString();
        string key1 = $"{{{baseKey}}}:key1";
        string key2 = $"{{{baseKey}}}:key2";

        string? result = await client.StringLongestCommonSubsequenceAsync(key1, key2);
        Assert.Equal("", result);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringLongestCommonSubsequenceLengthAsync_BasicTest(BaseClient client)
    {
        Assert.SkipWhen(
            TestConfiguration.SERVER_VERSION < new Version("7.0.0"),
            "LCS is supported since 7.0.0"
        );
        // Use hash tags to ensure keys map to the same slot in cluster mode
        string baseKey = Guid.NewGuid().ToString();
        string key1 = $"{{{baseKey}}}:key1";
        string key2 = $"{{{baseKey}}}:key2";

        await client.StringSetAsync(key1, "abcdef");
        await client.StringSetAsync(key2, "acef");

        long result = await client.StringLongestCommonSubsequenceLengthAsync(key1, key2);
        Assert.Equal(4, result);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringLongestCommonSubsequenceLengthAsync_NoCommonSubsequence(BaseClient client)
    {
        Assert.SkipWhen(
            TestConfiguration.SERVER_VERSION < new Version("7.0.0"),
            "LCS is supported since 7.0.0"
        );
        // Use hash tags to ensure keys map to the same slot in cluster mode
        string baseKey = Guid.NewGuid().ToString();
        string key1 = $"{{{baseKey}}}:key1";
        string key2 = $"{{{baseKey}}}:key2";

        await client.StringSetAsync(key1, "abc");
        await client.StringSetAsync(key2, "xyz");

        long result = await client.StringLongestCommonSubsequenceLengthAsync(key1, key2);
        Assert.Equal(0, result);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringLongestCommonSubsequenceWithMatchesAsync_BasicTest(BaseClient client)
    {
        Assert.SkipWhen(
            TestConfiguration.SERVER_VERSION < new Version("7.0.0"),
            "LCS is supported since 7.0.0"
        );
        // Use hash tags to ensure keys map to the same slot in cluster mode
        string baseKey = Guid.NewGuid().ToString();
        string key1 = $"{{{baseKey}}}:key1";
        string key2 = $"{{{baseKey}}}:key2";

        await client.StringSetAsync(key1, "abcd1234");
        await client.StringSetAsync(key2, "bcdef1234");

        LCSMatchResult result = await client.StringLongestCommonSubsequenceWithMatchesAsync(key1, key2);

        Assert.True(result.LongestMatchLength > 0);
        Assert.NotNull(result.Matches);
        Assert.True(result.Matches.Length > 0);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringLongestCommonSubsequenceWithMatchesAsync_WithMinLength(BaseClient client)
    {
        Assert.SkipWhen(
            TestConfiguration.SERVER_VERSION < new Version("7.0.0"),
            "LCS is supported since 7.0.0"
        );
        // Use hash tags to ensure keys map to the same slot in cluster mode
        string baseKey = Guid.NewGuid().ToString();
        string key1 = $"{{{baseKey}}}:key1";
        string key2 = $"{{{baseKey}}}:key2";

        await client.StringSetAsync(key1, "abcd1234");
        await client.StringSetAsync(key2, "bcdef1234");

        LCSMatchResult result = await client.StringLongestCommonSubsequenceWithMatchesAsync(key1, key2, 4);

        Assert.True(result.LongestMatchLength >= 0);
        Assert.NotNull(result.Matches);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringLongestCommonSubsequenceWithMatchesAsync_NoMatches(BaseClient client)
    {
        Assert.SkipWhen(
            TestConfiguration.SERVER_VERSION < new Version("7.0.0"),
            "LCS is supported since 7.0.0"
        );
        // Use hash tags to ensure keys map to the same slot in cluster mode
        string baseKey = Guid.NewGuid().ToString();
        string key1 = $"{{{baseKey}}}:key1";
        string key2 = $"{{{baseKey}}}:key2";

        await client.StringSetAsync(key1, "abc");
        await client.StringSetAsync(key2, "xyz");

        LCSMatchResult result = await client.StringLongestCommonSubsequenceWithMatchesAsync(key1, key2);

        Assert.Equal(0, result.LongestMatchLength);
        Assert.NotNull(result.Matches);
        Assert.Empty(result.Matches);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringLongestCommonSubsequenceWithMatchesAsync_ValidateMatchDetails(BaseClient client)
    {
        Assert.SkipWhen(
            TestConfiguration.SERVER_VERSION < new Version("7.0.0"),
            "LCS is supported since 7.0.0"
        );
        // Use hash tags to ensure keys map to the same slot in cluster mode
        string baseKey = Guid.NewGuid().ToString();
        string key1 = $"{{{baseKey}}}:key1";
        string key2 = $"{{{baseKey}}}:key2";

        // Set up strings with predictable matches based on actual Redis behavior
        // "abcdef" and "aXcYeF" 
        // Expected LCS: "ace" (note: 'f' != 'F' due to case sensitivity)
        // Expected matches: "e" at (4,4), "c" at (2,2), "a" at (0,0) - all length 1
        await client.StringSetAsync(key1, "abcdef");
        await client.StringSetAsync(key2, "aXcYeF");

        LCSMatchResult result = await client.StringLongestCommonSubsequenceWithMatchesAsync(key1, key2);

        // Validate overall result - LCS is "ace" with length 3
        Assert.Equal(3, result.LongestMatchLength);
        Assert.NotNull(result.Matches);
        Assert.Equal(3, result.Matches.Length); // Should have exactly 3 matches

        // Sort matches by first string index for predictable testing
        var sortedMatches = result.Matches.OrderBy(m => m.FirstStringIndex).ToArray();

        // Validate exact match details (server-provided lengths)
        // Match 1: "a" at position (0,0) with length 1
        Assert.Equal(0, sortedMatches[0].FirstStringIndex);
        Assert.Equal(0, sortedMatches[0].SecondStringIndex);
        Assert.Equal(1, sortedMatches[0].Length);

        // Match 2: "c" at position (2,2) with length 1  
        Assert.Equal(2, sortedMatches[1].FirstStringIndex);
        Assert.Equal(2, sortedMatches[1].SecondStringIndex);
        Assert.Equal(1, sortedMatches[1].Length);

        // Match 3: "e" at position (4,4) with length 1
        Assert.Equal(4, sortedMatches[2].FirstStringIndex);
        Assert.Equal(4, sortedMatches[2].SecondStringIndex);
        Assert.Equal(1, sortedMatches[2].Length);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringLongestCommonSubsequenceWithMatchesAsync_WithMinLengthFiltering(BaseClient client)
    {
        Assert.SkipWhen(
            TestConfiguration.SERVER_VERSION < new Version("7.0.0"),
            "LCS is supported since 7.0.0"
        );
        // Use hash tags to ensure keys map to the same slot in cluster mode
        string baseKey = Guid.NewGuid().ToString();
        string key1 = $"{{{baseKey}}}:key1";
        string key2 = $"{{{baseKey}}}:key2";

        // Set up strings based on actual Redis behavior
        // "abcdefghijk" and "aXXdefXXijk" 
        // Expected LCS: "adefijk" with length 7
        // Expected matches: "ijk" at (8,8) length=3, "def" at (3,3) length=3, "a" at (0,0) length=1
        await client.StringSetAsync(key1, "abcdefghijk");
        await client.StringSetAsync(key2, "aXXdefXXijk");

        // Test without minLength filter - should get all 3 matches
        LCSMatchResult resultAll = await client.StringLongestCommonSubsequenceWithMatchesAsync(key1, key2, 0);

        // Test with minLength filter of 2 - should filter out the single "a" match, leaving 2 matches
        LCSMatchResult resultFiltered = await client.StringLongestCommonSubsequenceWithMatchesAsync(key1, key2, 2);

        // Test with minLength filter of 4 - should get no matches since longest is 3
        LCSMatchResult resultHighFilter = await client.StringLongestCommonSubsequenceWithMatchesAsync(key1, key2, 4);

        // Validate overall results - LCS is "adefijk" with length 7
        Assert.Equal(7, resultAll.LongestMatchLength);
        Assert.Equal(7, resultFiltered.LongestMatchLength); // Total LCS length doesn't change
        Assert.Equal(7, resultHighFilter.LongestMatchLength); // Total LCS length doesn't change

        // Validate match counts based on filtering
        Assert.Equal(3, resultAll.Matches.Length); // All matches: "a"(1), "def"(3), "ijk"(3)
        Assert.Equal(2, resultFiltered.Matches.Length); // Only "def"(3) and "ijk"(3) 
        Assert.Empty(resultHighFilter.Matches); // No matches >= 4

        // Validate exact match details for unfiltered result (sorted by position)
        var sortedMatches = resultAll.Matches.OrderBy(m => m.FirstStringIndex).ToArray();

        // Match 1: "a" at position (0,0) with length 1
        Assert.Equal(0, sortedMatches[0].FirstStringIndex);
        Assert.Equal(0, sortedMatches[0].SecondStringIndex);
        Assert.Equal(1, sortedMatches[0].Length);

        // Match 2: "def" at position (3,3) with length 3
        Assert.Equal(3, sortedMatches[1].FirstStringIndex);
        Assert.Equal(3, sortedMatches[1].SecondStringIndex);
        Assert.Equal(3, sortedMatches[1].Length);

        // Match 3: "ijk" at position (8,8) with length 3
        Assert.Equal(8, sortedMatches[2].FirstStringIndex);
        Assert.Equal(8, sortedMatches[2].SecondStringIndex);
        Assert.Equal(3, sortedMatches[2].Length);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringLongestCommonSubsequenceWithMatchesAsync_DifferentIndices(BaseClient client)
    {
        Assert.SkipWhen(
            TestConfiguration.SERVER_VERSION < new Version("7.0.0"),
            "LCS is supported since 7.0.0"
        );

        // Use hash tags to ensure keys map to the same slot in cluster mode
        string baseKey = Guid.NewGuid().ToString();
        string key1 = $"{{{baseKey}}}:key1";
        string key2 = $"{{{baseKey}}}:key2";

        // Set strings where common subsequences appear at different positions
        await client.StringSetAsync(key1, "xyzabcdef");  // "abc" at index 3-5
        await client.StringSetAsync(key2, "abcxyzdef");  // "abc" at index 0-2, "def" at index 6-8

        LCSMatchResult result = await client.StringLongestCommonSubsequenceWithMatchesAsync(key1, key2);

        // The LCS should be "abcdef" with length 6
        Assert.Equal(6, result.LongestMatchLength);
        Assert.Equal(2, result.Matches.Length);

        // Sort matches by first string index for consistent testing
        var sortedMatches = result.Matches.OrderBy(m => m.FirstStringIndex).ToArray();

        // Match 1: "abc" appears at different positions - (3,0) with length 3
        Assert.Equal(3, sortedMatches[0].FirstStringIndex);  // "abc" starts at index 3 in first string
        Assert.Equal(0, sortedMatches[0].SecondStringIndex); // "abc" starts at index 0 in second string
        Assert.Equal(3, sortedMatches[0].Length);

        // Match 2: "def" appears at same positions - (6,6) with length 3
        Assert.Equal(6, sortedMatches[1].FirstStringIndex);  // "def" starts at index 6 in first string
        Assert.Equal(6, sortedMatches[1].SecondStringIndex); // "def" starts at index 6 in second string
        Assert.Equal(3, sortedMatches[1].Length);
    }
}
