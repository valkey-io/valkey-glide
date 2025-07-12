// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Valkey.Glide.IntegrationTests;

using Valkey.Glide.Commands.Options;

public class GenericCommandTests(TestConfiguration config)
{
    public TestConfiguration Config { get; } = config;

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestKeyDelete_KeyExists(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        string value = "test_value";

        // Set a key first
        await client.StringSetAsync(key, value);
        Assert.True(await client.KeyExistsAsync(key));

        // Delete the key
        Assert.True(await client.KeyDeleteAsync(key));
        Assert.False(await client.KeyExistsAsync(key));

        // Try to delete non-existent key
        Assert.False(await client.KeyDeleteAsync(key));
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestKeyDelete_MultipleKeys(BaseClient client)
    {
        string key1 = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();
        string key3 = Guid.NewGuid().ToString();

        // Set keys
        await client.StringSetAsync(key1, "value1");
        await client.StringSetAsync(key2, "value2");

        // Delete multiple keys (including non-existent)
        long deletedCount = await client.KeyDeleteAsync([key1, key2, key3]);
        Assert.Equal(2, deletedCount);

        Assert.False(await client.KeyExistsAsync(key1));
        Assert.False(await client.KeyExistsAsync(key2));
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestKeyUnlink(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        string value = "test_value";

        // Set a key first
        await client.StringSetAsync(key, value);
        Assert.True(await client.KeyExistsAsync(key));

        // Unlink the key
        Assert.True(await client.KeyUnlinkAsync(key));
        Assert.False(await client.KeyExistsAsync(key));

        // Try to unlink non-existent key
        Assert.False(await client.KeyUnlinkAsync(key));
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestKeyExists_MultipleKeys(BaseClient client)
    {
        string key1 = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();
        string key3 = Guid.NewGuid().ToString();

        // Set some keys
        await client.StringSetAsync(key1, "value1");
        await client.StringSetAsync(key2, "value2");

        // Check existence
        long existingCount = await client.KeyExistsAsync([key1, key2, key3]);
        Assert.Equal(2, existingCount);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestKeyExpire_KeyTimeToLive(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        string value = "test_value";

        // Set a key
        await client.StringSetAsync(key, value);

        // Set expiry
        Assert.True(await client.KeyExpireAsync(key, TimeSpan.FromSeconds(10)));

        // Check TTL
        TimeSpan? ttl = await client.KeyTimeToLiveAsync(key);
        Assert.NotNull(ttl);
        Assert.True(ttl.Value.TotalSeconds > 0 && ttl.Value.TotalSeconds <= 10);

        // Test with DateTime
        DateTime expireTime = DateTime.UtcNow.AddSeconds(15);
        Assert.True(await client.KeyExpireAsync(key, expireTime));

        ttl = await client.KeyTimeToLiveAsync(key);
        Assert.NotNull(ttl);
        Assert.True(ttl.Value.TotalSeconds > 10);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestKeyType(BaseClient client)
    {
        string stringKey = Guid.NewGuid().ToString();
        string setKey = Guid.NewGuid().ToString();

        // Test string type
        await client.StringSetAsync(stringKey, "value");
        ValkeyType stringType = await client.KeyTypeAsync(stringKey);
        Assert.Equal(ValkeyType.String, stringType);

        // Test set type
        await client.SetAddAsync(setKey, "member");
        ValkeyType setType = await client.KeyTypeAsync(setKey);
        Assert.Equal(ValkeyType.Set, setType);

        // Test non-existent key
        string nonExistentKey = Guid.NewGuid().ToString();
        ValkeyType noneType = await client.KeyTypeAsync(nonExistentKey);
        Assert.Equal(ValkeyType.None, noneType);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestKeyRename(BaseClient client)
    {
        string oldKey = "{prefix}-" + Guid.NewGuid().ToString();
        string newKey = "{prefix}-" + Guid.NewGuid().ToString();
        string value = "test_value";

        // Set a key
        await client.StringSetAsync(oldKey, value);

        // Rename the key
        Assert.True(await client.KeyRenameAsync(oldKey, newKey));

        // Check that old key doesn't exist and new key exists
        Assert.False(await client.KeyExistsAsync(oldKey));
        Assert.True(await client.KeyExistsAsync(newKey));
        Assert.Equal(value, await client.StringGetAsync(newKey));
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestKeyRenameNX(BaseClient client)
    {
        string oldKey = "{prefix}-" + Guid.NewGuid().ToString();
        string newKey = "{prefix}-" + Guid.NewGuid().ToString();
        string existingKey = "{prefix}-" + Guid.NewGuid().ToString();
        string value = "test_value";
        string existingValue = "existing_value";

        // Set keys
        await client.StringSetAsync(oldKey, value);
        await client.StringSetAsync(existingKey, existingValue);

        // Rename to non-existing key should succeed
        Assert.True(await client.KeyRenameNXAsync(oldKey, newKey));

        // Check that old key doesn't exist and new key exists
        Assert.False(await client.KeyExistsAsync(oldKey));
        Assert.True(await client.KeyExistsAsync(newKey));
        Assert.Equal(value, await client.StringGetAsync(newKey));

        // Try to rename to existing key should fail
        Assert.False(await client.KeyRenameNXAsync(newKey, existingKey));

        // Both keys should still exist with original values
        Assert.True(await client.KeyExistsAsync(newKey));
        Assert.True(await client.KeyExistsAsync(existingKey));
        Assert.Equal(value, await client.StringGetAsync(newKey));
        Assert.Equal(existingValue, await client.StringGetAsync(existingKey));
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestKeyPersist(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        string value = "test_value";

        // Set a key with expiry
        await client.StringSetAsync(key, value);
        await client.KeyExpireAsync(key, TimeSpan.FromSeconds(10));

        // Verify it has TTL
        TimeSpan? ttl = await client.KeyTimeToLiveAsync(key);
        Assert.NotNull(ttl);

        // Persist the key
        Assert.True(await client.KeyPersistAsync(key));

        // Verify TTL is removed
        ttl = await client.KeyTimeToLiveAsync(key);
        Assert.Null(ttl);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestKeyDump_KeyRestore(BaseClient client)
    {
        string sourceKey = Guid.NewGuid().ToString();
        string destKey = Guid.NewGuid().ToString();
        string replaceKey = Guid.NewGuid().ToString();
        string replaceDateTimeKey = Guid.NewGuid().ToString();
        string value = "test_value";

        // Set a key
        await client.StringSetAsync(sourceKey, value);

        // Dump the key
        byte[]? dumpData = await client.KeyDumpAsync(sourceKey);
        Assert.NotNull(dumpData);
        Assert.NotEmpty(dumpData);

        // Restore to a new key
        await client.KeyRestoreAsync(destKey, dumpData);

        // Verify the restored key
        Assert.Equal(value, await client.StringGetAsync(destKey));

        // Test RestoreOptions with Replace
        await client.StringSetAsync(replaceKey, "old_value");
        await client.StringSetAsync(replaceDateTimeKey, "old_value");
        RestoreOptions replaceOptions = new RestoreOptions().Replace();
        await client.KeyRestoreAsync(replaceKey, dumpData, restoreOptions: replaceOptions);
        Assert.Equal(value, await client.StringGetAsync(replaceKey));
        await client.KeyRestoreDateTimeAsync(replaceDateTimeKey, dumpData, restoreOptions: replaceOptions);
        Assert.Equal(value, await client.StringGetAsync(replaceDateTimeKey));

        // Test RestoreOptions with TTL
        string ttlKey = Guid.NewGuid().ToString();
        string ttlDateTimeKey = Guid.NewGuid().ToString();
        TimeSpan ts = TimeSpan.FromSeconds(30);
        DateTime dt = new DateTime(2042, 12, 31);
        await client.KeyRestoreAsync(ttlKey, dumpData, expiry: ts);
        await client.KeyRestoreDateTimeAsync(ttlDateTimeKey, dumpData, expiry: dt);

        // Verify key exists and has TTL
        Assert.True(await client.KeyExistsAsync(ttlKey));
        Assert.True(await client.KeyExistsAsync(ttlDateTimeKey));
        TimeSpan? ttl = await client.KeyTimeToLiveAsync(ttlKey);
        TimeSpan? ttlDateTime = await client.KeyTimeToLiveAsync(ttlDateTimeKey);
        Assert.NotNull(ttl);
        Assert.NotNull(ttlDateTime);
        Assert.True(ttl.Value.TotalSeconds > 0);
        Assert.True(ttlDateTime.Value.TotalSeconds > 0);

        // Test RestoreOptions with IDLETIME
        string idletimeKey = Guid.NewGuid().ToString();
        string idletimeDateTimeKey = Guid.NewGuid().ToString();
        RestoreOptions idletimeOptions = new RestoreOptions().SetIdletime(1000);
        await client.KeyRestoreAsync(idletimeKey, dumpData, restoreOptions: idletimeOptions);
        await client.KeyRestoreDateTimeAsync(idletimeDateTimeKey, dumpData, restoreOptions: idletimeOptions);
        Assert.Equal(value, await client.StringGetAsync(idletimeKey));
        Assert.Equal(value, await client.StringGetAsync(idletimeDateTimeKey));

        // Test RestoreOptions with FREQ
        string freqKey = Guid.NewGuid().ToString();
        string freqDateTimeKey = Guid.NewGuid().ToString();
        RestoreOptions freqOptions = new RestoreOptions().SetFrequency(5);
        await client.KeyRestoreAsync(freqKey, dumpData, restoreOptions: freqOptions);
        await client.KeyRestoreDateTimeAsync(freqDateTimeKey, dumpData, restoreOptions: freqOptions);
        Assert.Equal(value, await client.StringGetAsync(freqKey));
        Assert.Equal(value, await client.StringGetAsync(freqDateTimeKey));
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestKeyTouch(BaseClient client)
    {
        string key1 = Guid.NewGuid().ToString();
        string key2 = Guid.NewGuid().ToString();
        string key3 = Guid.NewGuid().ToString();

        // Set some keys
        await client.StringSetAsync(key1, "value1");
        await client.StringSetAsync(key2, "value2");

        // Touch single key
        Assert.True(await client.KeyTouchAsync(key1));

        // Touch multiple keys (including non-existent)
        long touchedCount = await client.KeyTouchAsync([key1, key2, key3]);
        Assert.Equal(2, touchedCount);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestKeyCopy(BaseClient client)
    {
        string sourceKey = "{prefix}-" + Guid.NewGuid().ToString();
        string destKey = "{prefix}-" + Guid.NewGuid().ToString();
        string value = "test_value";

        // Set a key
        await client.StringSetAsync(sourceKey, value);

        // Copy the key
        Assert.True(await client.KeyCopyAsync(sourceKey, destKey));

        // Verify both keys exist with same value
        Assert.Equal(value, await client.StringGetAsync(sourceKey));
        Assert.Equal(value, await client.StringGetAsync(destKey));

        // Test copy with replace
        await client.StringSetAsync(destKey, "new_value");
        Assert.True(await client.KeyCopyAsync(sourceKey, destKey, replace: true));
        Assert.Equal(value, await client.StringGetAsync(destKey));
    }

}
