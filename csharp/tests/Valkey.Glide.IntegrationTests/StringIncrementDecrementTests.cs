// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Valkey.Glide.IntegrationTests;

public class StringIncrementDecrementTests
{
    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(TestConfiguration.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task StringDecrementAsync_ExistingKey(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        // Set initial value
        await client.StringSetAsync(key, "10");

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
        await client.StringSetAsync(key, "10");

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
        await client.StringSetAsync(key, "10");

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
        await client.StringSetAsync(key, "10");

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
        await client.StringSetAsync(key, "10");

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
        await client.StringSetAsync(key, "10");

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
        await client.StringSetAsync(key, "10.5");

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
        await client.StringSetAsync(key, "10.5");

        // Increment by -0.5 (effectively decrementing by 0.5)
        double result = await client.StringIncrementAsync(key, -0.5);
        Assert.Equal(10.0, result);

        // Verify the value was decremented
        ValkeyValue value = await client.StringGetAsync(key);
        Assert.Equal("10", value.ToString());
    }
}
