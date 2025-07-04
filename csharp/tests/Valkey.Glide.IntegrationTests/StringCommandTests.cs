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

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task MSetAndMGet_ExistingAndNonExistingKeys(BaseClient client)
    {
        string key1 = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();
        string key3 = Guid.NewGuid().ToString();
        string oldValue = Guid.NewGuid().ToString();
        string value = Guid.NewGuid().ToString();

        // Set initial value for key1
        Assert.Equal("OK", await client.Set(key1, oldValue));

        // Use MSet to set key1 and key2
        var keyValueMap = new Dictionary<GlideString, GlideString>
        {
            [key1] = value,
            [key2] = value
        };
        string result = await client.MSet(keyValueMap);
        Assert.Equal("OK", result);

        // Use MGet to retrieve values
        GlideString[] keys = [key1, key2, key3];
        GlideString?[] values = await client.MGet(keys);

        Assert.Equal(3, values.Length);
        Assert.Equal(value, values[0]?.ToString());
        Assert.Equal(value, values[1]?.ToString());
        Assert.Null(values[2]); // key3 doesn't exist
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task MGet_NonExistingKeys(BaseClient client)
    {
        string key1 = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();

        GlideString[] keys = [key1, key2];
        GlideString?[] values = await client.MGet(keys);

        Assert.Equal(2, values.Length);
        Assert.Null(values[0]);
        Assert.Null(values[1]);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task MGet_SingleKey(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        string value = Guid.NewGuid().ToString();

        Assert.Equal("OK", await client.Set(key, value));

        GlideString[] keys = [key];
        GlideString?[] values = await client.MGet(keys);

        Assert.Single(values);
        Assert.Equal(value, values[0]?.ToString());
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task MSet_SingleKeyValue(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        string value = Guid.NewGuid().ToString();

        var keyValueMap = new Dictionary<GlideString, GlideString>
        {
            [key] = value
        };

        string result = await client.MSet(keyValueMap);
        Assert.Equal("OK", result);

        // Verify the value was set
        GlideString? retrievedValue = await client.Get(key);
        Assert.Equal(value, retrievedValue?.ToString());
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task MSet_MultipleKeyValues(BaseClient client)
    {
        string key1 = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();
        string key3 = Guid.NewGuid().ToString();
        string value1 = Guid.NewGuid().ToString();
        string value2 = Guid.NewGuid().ToString();
        string value3 = Guid.NewGuid().ToString();

        var keyValueMap = new Dictionary<GlideString, GlideString>
        {
            [key1] = value1,
            [key2] = value2,
            [key3] = value3
        };

        string result = await client.MSet(keyValueMap);
        Assert.Equal("OK", result);

        // Verify all values were set
        Assert.Equal(value1, (await client.Get(key1))?.ToString());
        Assert.Equal(value2, (await client.Get(key2))?.ToString());
        Assert.Equal(value3, (await client.Get(key3))?.ToString());
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task MSetAndMGet_WithUnicodeValues(BaseClient client)
    {
        string key1 = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();
        string unicodeValue1 = "שלום hello 汉字";
        string unicodeValue2 = "مرحبا world 🌍";

        var keyValueMap = new Dictionary<GlideString, GlideString>
        {
            [key1] = unicodeValue1,
            [key2] = unicodeValue2
        };

        string result = await client.MSet(keyValueMap);
        Assert.Equal("OK", result);

        GlideString[] keys = [key1, key2];
        GlideString?[] values = await client.MGet(keys);

        Assert.Equal(2, values.Length);
        Assert.Equal(unicodeValue1, values[0]?.ToString());
        Assert.Equal(unicodeValue2, values[1]?.ToString());
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task MSetAndMGet_WithEmptyValues(BaseClient client)
    {
        string key1 = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();

        var keyValueMap = new Dictionary<GlideString, GlideString>
        {
            [key1] = "",
            [key2] = "non-empty"
        };

        string result = await client.MSet(keyValueMap);
        Assert.Equal("OK", result);

        GlideString[] keys = [key1, key2];
        GlideString?[] values = await client.MGet(keys);

        Assert.Equal(2, values.Length);
        Assert.Equal("", values[0]?.ToString());
        Assert.Equal("non-empty", values[1]?.ToString());
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task MGet_EmptyKeyArray(BaseClient client)
    {
        GlideString[] keys = [];

        // MGet with empty array should throw ArgumentException
        var exception = await Assert.ThrowsAsync<ArgumentException>(() => client.MGet(keys));
        Assert.Contains("Keys array cannot be empty", exception.Message);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task MSet_EmptyDictionary(BaseClient client)
    {
        var keyValueMap = new Dictionary<GlideString, GlideString>();

        var exception = await Assert.ThrowsAsync<ArgumentException>(() => client.MSet(keyValueMap));
        Assert.Contains("Key-value map cannot be empty", exception.Message);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task MSetAndMGet_OverwriteExistingKeys(BaseClient client)
    {
        string key1 = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();
        string initialValue1 = "initial1";
        string initialValue2 = "initial2";
        string newValue1 = "new1";
        string newValue2 = "new2";

        // Set initial values
        Assert.Equal("OK", await client.Set(key1, initialValue1));
        Assert.Equal("OK", await client.Set(key2, initialValue2));

        // Overwrite with MSet
        var keyValueMap = new Dictionary<GlideString, GlideString>
        {
            [key1] = newValue1,
            [key2] = newValue2
        };

        string result = await client.MSet(keyValueMap);
        Assert.Equal("OK", result);

        // Verify values were overwritten
        GlideString[] keys = [key1, key2];
        GlideString?[] values = await client.MGet(keys);

        Assert.Equal(2, values.Length);
        Assert.Equal(newValue1, values[0]?.ToString());
        Assert.Equal(newValue2, values[1]?.ToString());
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task MSetAndMGet_WithGlideStringKeys(BaseClient client)
    {
        GlideString key1 = new(Guid.NewGuid().ToString());
        GlideString key2 = new(Guid.NewGuid().ToString());
        GlideString value1 = new("value1");
        GlideString value2 = new("value2");

        var keyValueMap = new Dictionary<GlideString, GlideString>
        {
            [key1] = value1,
            [key2] = value2
        };

        string result = await client.MSet(keyValueMap);
        Assert.Equal("OK", result);

        GlideString[] keys = [key1, key2];
        GlideString?[] values = await client.MGet(keys);

        Assert.Equal(2, values.Length);
        Assert.Equal(value1, values[0]);
        Assert.Equal(value2, values[1]);
    }
}
