// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Valkey.Glide.IntegrationTests;

public class HashCommandTests(TestConfiguration config)
{
    public TestConfiguration Config { get; } = config;

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestHashSet_HashGet(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        // Test single field set and get
        Assert.True(await client.HashSetAsync(key, "field1", "value1"));
        Assert.Equal("value1", await client.HashGetAsync(key, "field1"));

        // Test multiple fields set and get
        HashEntry[] entries =
        [
            new HashEntry("field2", "value2"),
            new HashEntry("field3", "value3")
        ];
        await client.HashSetAsync(key, entries);

        ValkeyValue[] values = await client.HashGetAsync(key, ["field1", "field2", "field3"]);
        Assert.Equal(3, values.Length);
        Assert.Equal("value1", values[0]);
        Assert.Equal("value2", values[1]);
        Assert.Equal("value3", values[2]);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestHashGetAll(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        HashEntry[] entries =
        [
            new HashEntry("field1", "value1"),
            new HashEntry("field2", "value2"),
            new HashEntry("field3", "value3")
        ];
        await client.HashSetAsync(key, entries);

        HashEntry[] result = await client.HashGetAllAsync(key);
        Assert.Equal(3, result.Length);

        // Sort the results for consistent testing
        Array.Sort(result, (a, b) => string.Compare(a.Name.ToString(), b.Name.ToString()));

        Assert.Equal("field1", result[0].Name);
        Assert.Equal("value1", result[0].Value);
        Assert.Equal("field2", result[1].Name);
        Assert.Equal("value2", result[1].Value);
        Assert.Equal("field3", result[2].Name);
        Assert.Equal("value3", result[2].Value);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestHashDelete(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        // Set up test data
        HashEntry[] entries =
        [
            new HashEntry("field1", "value1"),
            new HashEntry("field2", "value2"),
            new HashEntry("field3", "value3"),
            new HashEntry("field4", "value4")
        ];
        await client.HashSetAsync(key, entries);

        // Test single field delete
        Assert.True(await client.HashDeleteAsync(key, "field1"));
        Assert.False(await client.HashExistsAsync(key, "field1"));

        // Test multiple fields delete
        Assert.Equal(2, await client.HashDeleteAsync(key, ["field2", "field3"]));
        Assert.False(await client.HashExistsAsync(key, "field2"));
        Assert.False(await client.HashExistsAsync(key, "field3"));
        Assert.True(await client.HashExistsAsync(key, "field4"));
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestHashExists(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        _ = await client.HashSetAsync(key, "field1", "value1");

        Assert.True(await client.HashExistsAsync(key, "field1"));
        Assert.False(await client.HashExistsAsync(key, "nonexistent"));
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestHashLength(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        Assert.Equal(0, await client.HashLengthAsync(key));

        HashEntry[] entries =
        [
            new HashEntry("field1", "value1"),
            new HashEntry("field2", "value2"),
            new HashEntry("field3", "value3")
        ];
        await client.HashSetAsync(key, entries);

        Assert.Equal(3, await client.HashLengthAsync(key));
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestHashStringLength(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        _ = await client.HashSetAsync(key, "field1", "value1");
        _ = await client.HashSetAsync(key, "field2", "value-with-longer-content");

        Assert.Equal(6, await client.HashStringLengthAsync(key, "field1"));
        Assert.Equal(23, await client.HashStringLengthAsync(key, "field2"));
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestHashValues(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        HashEntry[] entries =
        [
            new HashEntry("field1", "value1"),
            new HashEntry("field2", "value2"),
            new HashEntry("field3", "value3")
        ];
        await client.HashSetAsync(key, entries);

        ValkeyValue[] values = await client.HashValuesAsync(key);
        Assert.Equal(3, values.Length);

        // Sort the values for consistent testing
        Array.Sort(values, (a, b) => string.Compare(a.ToString(), b.ToString()));

        Assert.Equal("value1", values[0]);
        Assert.Equal("value2", values[1]);
        Assert.Equal("value3", values[2]);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestHashRandomField(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        HashEntry[] entries =
        [
            new HashEntry("field1", "value1"),
            new HashEntry("field2", "value2"),
            new HashEntry("field3", "value3")
        ];
        await client.HashSetAsync(key, entries);

        // Test single random field
        ValkeyValue randomField = await client.HashRandomFieldAsync(key);
        Assert.Contains(randomField.ToString(), new[] { "field1", "field2", "field3" });

        // Test multiple random fields
        ValkeyValue[] randomFields = await client.HashRandomFieldsAsync(key, 2);
        Assert.Equal(2, randomFields.Length);
        foreach (ValkeyValue field in randomFields)
        {
            Assert.Contains(field.ToString(), new[] { "field1", "field2", "field3" });
        }
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestHashRandomFieldsWithValues(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        HashEntry[] entries =
        [
            new HashEntry("field1", "value1"),
            new HashEntry("field2", "value2"),
            new HashEntry("field3", "value3")
        ];
        await client.HashSetAsync(key, entries);

        HashEntry[] randomEntries = await client.HashRandomFieldsWithValuesAsync(key, 2);
        Assert.Equal(2, randomEntries.Length);

        foreach (HashEntry entry in randomEntries)
        {
            string fieldName = entry.Name.ToString();
            string fieldValue = entry.Value.ToString();

            Assert.Contains(fieldName, new[] { "field1", "field2", "field3" });
            Assert.Equal("value" + fieldName[5..], fieldValue);
        }
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestHashSetWithWhen(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        // Initial set should succeed
        Assert.True(await client.HashSetAsync(key, "field1", "value1"));

        // Set with When.Always should always succeed and overwrite
        Assert.True(await client.HashSetAsync(key, "field1", "value1-updated", When.Always));
        Assert.Equal("value1-updated", await client.HashGetAsync(key, "field1"));

        // Set with When.Exists should succeed for existing field
        Assert.True(await client.HashSetAsync(key, "field1", "value1-updated-again", When.Exists));
        Assert.Equal("value1-updated-again", await client.HashGetAsync(key, "field1"));

        // Set with When.Exists should fail for non-existing field
        Assert.False(await client.HashSetAsync(key, "field2", "value2", When.Exists));
        Assert.Equal(ValkeyValue.Null, await client.HashGetAsync(key, "field2"));

        // Set with When.NotExists should fail for existing field
        Assert.False(await client.HashSetAsync(key, "field1", "should-not-update", When.NotExists));
        Assert.Equal("value1-updated-again", await client.HashGetAsync(key, "field1"));

        // Set with When.NotExists should succeed for non-existing field
        Assert.True(await client.HashSetAsync(key, "field2", "value2", When.NotExists));
        Assert.Equal("value2", await client.HashGetAsync(key, "field2"));
    }
}
