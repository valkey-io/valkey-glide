// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Valkey.Glide.IntegrationTests;

[Collection("GlideTests")]
public class StringCommandTests(TestConfiguration config)
{
    public TestConfiguration Config { get; } = config;

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringSet_StringGet_SingleKey(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        string value = Guid.NewGuid().ToString();

        string result = await client.StringSet(key, value);
        Assert.Equal("OK", result);

        GlideString? retrievedValue = await client.StringGet(key);
        Assert.Equal(value, retrievedValue?.ToString());
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringGet_NonExistentKey(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        GlideString? value = await client.StringGet(key);
        Assert.Null(value);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringGet_MultipleKeys(BaseClient client)
    {
        string key1 = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();
        string key3 = Guid.NewGuid().ToString();
        string value1 = Guid.NewGuid().ToString();
        string value2 = Guid.NewGuid().ToString();

        // Set key1 and key2, leave key3 unset
        await client.StringSet(key1, value1);
        await client.StringSet(key2, value2);

        GlideString[] keys = [key1, key2, key3];
        GlideString?[] values = await client.StringGet(keys);

        Assert.Equal(3, values.Length);
        Assert.Equal(value1, values[0]?.ToString());
        Assert.Equal(value2, values[1]?.ToString());
        Assert.Null(values[2]);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringGet_NonExistentKeys(BaseClient client)
    {
        string key1 = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();

        GlideString[] keys = [key1, key2];
        GlideString?[] values = await client.StringGet(keys);

        Assert.Equal(2, values.Length);
        Assert.Null(values[0]);
        Assert.Null(values[1]);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringSet_KeyValuePairs(BaseClient client)
    {
        string key1 = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();
        string value1 = Guid.NewGuid().ToString();
        string value2 = Guid.NewGuid().ToString();

        KeyValuePair<GlideString, GlideString>[] values = [
            new(key1, value1),
            new(key2, value2)
        ];

        string result = await client.StringSet(values);
        Assert.Equal("OK", result);

        // Verify values were set
        Assert.Equal(value1, (await client.StringGet(key1))?.ToString());
        Assert.Equal(value2, (await client.StringGet(key2))?.ToString());
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringSet_KeyValuePairs_WithUnicodeValues(BaseClient client)
    {
        string key1 = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();
        string unicodeValue1 = "שלום hello 汉字";
        string unicodeValue2 = "مرحبا world 🌍";

        KeyValuePair<GlideString, GlideString>[] values = [
            new(key1, unicodeValue1),
            new(key2, unicodeValue2)
        ];

        string result = await client.StringSet(values);
        Assert.Equal("OK", result);

        GlideString[] keys = [key1, key2];
        GlideString?[] retrievedValues = await client.StringGet(keys);

        Assert.Equal(2, retrievedValues.Length);
        Assert.Equal(unicodeValue1, retrievedValues[0]?.ToString());
        Assert.Equal(unicodeValue2, retrievedValues[1]?.ToString());
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringSet_KeyValuePairs_WithEmptyValues(BaseClient client)
    {
        string key1 = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();

        KeyValuePair<GlideString, GlideString>[] values = [
            new(key1, ""),
            new(key2, "non-empty")
        ];

        string result = await client.StringSet(values);
        Assert.Equal("OK", result);

        GlideString[] keys = [key1, key2];
        GlideString?[] retrievedValues = await client.StringGet(keys);

        Assert.Equal(2, retrievedValues.Length);
        Assert.Equal("", retrievedValues[0]?.ToString());
        Assert.Equal("non-empty", retrievedValues[1]?.ToString());
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringGet_EmptyKeyArray(BaseClient client)
    {
        GlideString[] keys = [];

        // StringGet with empty array should throw ArgumentException
        var exception = await Assert.ThrowsAsync<ArgumentException>(() => client.StringGet(keys));
        Assert.Contains("Keys array cannot be empty", exception.Message);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringSet_KeyValuePairs_OverwriteExistingKeys(BaseClient client)
    {
        string key1 = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();
        string initialValue1 = "initial1";
        string initialValue2 = "initial2";
        string newValue1 = "new1";
        string newValue2 = "new2";

        // Set initial values
        await client.StringSet(key1, initialValue1);
        await client.StringSet(key2, initialValue2);

        // Overwrite with StringSet
        KeyValuePair<GlideString, GlideString>[] values = [
            new(key1, newValue1),
            new(key2, newValue2)
        ];

        string result = await client.StringSet(values);
        Assert.Equal("OK", result);

        // Verify values were overwritten
        GlideString[] keys = [key1, key2];
        GlideString?[] retrievedValues = await client.StringGet(keys);

        Assert.Equal(2, retrievedValues.Length);
        Assert.Equal(newValue1, retrievedValues[0]?.ToString());
        Assert.Equal(newValue2, retrievedValues[1]?.ToString());
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringSet_KeyValuePairs_WithGlideStringKeys(BaseClient client)
    {
        GlideString key1 = new("key1");
        GlideString key2 = new("key2");
        GlideString value1 = new("value1");
        GlideString value2 = new("value2");

        KeyValuePair<GlideString, GlideString>[] values = [
            new(key1, value1),
            new(key2, value2)
        ];

        string result = await client.StringSet(values);
        Assert.Equal("OK", result);

        GlideString[] keys = [key1, key2];
        GlideString?[] retrievedValues = await client.StringGet(keys);

        Assert.Equal(2, retrievedValues.Length);
        Assert.Equal(value1, retrievedValues[0]);
        Assert.Equal(value2, retrievedValues[1]);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringLength_ExistingKey(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        string value = "Hello World";

        await client.StringSet(key, value);
        long length = await client.StringLength(key);
        Assert.Equal(value.Length, length);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringLength_NonExistentKey(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        long length = await client.StringLength(key);
        Assert.Equal(0, length);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringGetRange_ExistingKey(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        string value = "Hello World";

        await client.StringSet(key, value);
        GlideString result = await client.StringGetRange(key, 0, 4);
        Assert.Equal("Hello", result.ToString());
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringGetRange_NonExistentKey(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        GlideString result = await client.StringGetRange(key, 0, 4);
        Assert.Equal("", result.ToString());
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringSetRange_ExistingKey(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        string initialValue = "Hello World";

        await client.StringSet(key, initialValue);
        long newLength = await client.StringSetRange(key, 6, "Valkey");
        Assert.Equal(12, newLength);

        GlideString? value = await client.StringGet(key);
        Assert.Equal("Hello Valkey", value?.ToString());
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringSetRange_NonExistentKey(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        long newLength = await client.StringSetRange(key, 0, "Hello");
        Assert.Equal(5, newLength);

        GlideString? value = await client.StringGet(key);
        Assert.Equal("Hello", value?.ToString());
    }
}
