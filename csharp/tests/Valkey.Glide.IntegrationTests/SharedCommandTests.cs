// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Commands.Options;

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
    public async Task ZAddBasicTest(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        var membersScoreMap = new Dictionary<GlideString, double>
        {
            { "member1", 1.0 },
            { "member2", 2.0 },
            { "member3", 3.5 }
        };

        long result = await client.ZAdd(key, membersScoreMap);
        Assert.Equal(3, result);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task ZAddWithOptionsTest(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        var membersScoreMap = new Dictionary<GlideString, double>
        {
            { "member1", 1.0 },
            { "member2", 2.0 }
        };

        // First add
        long result1 = await client.ZAdd(key, membersScoreMap);
        Assert.Equal(2, result1);

        // Try to add existing members with NX option (should not add)
        var options = new ZAddOptions().SetConditionalChange(ConditionalSet.OnlyIfDoesNotExist);
        long result2 = await client.ZAdd(key, membersScoreMap, options);
        Assert.Equal(0, result2);

        // Update existing members with XX option
        var updateOptions = new ZAddOptions().SetConditionalChange(ConditionalSet.OnlyIfExists);
        var updateMap = new Dictionary<GlideString, double> { { "member1", 10.0 } };
        long result3 = await client.ZAdd(key, updateMap, updateOptions);
        Assert.Equal(0, result3); // 0 new members added, but existing member updated
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task ZAddWithChangedOptionTest(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();
        var membersScoreMap = new Dictionary<GlideString, double>
        {
            { "member1", 1.0 },
            { "member2", 2.0 }
        };

        // First add
        await client.ZAdd(key, membersScoreMap);

        // Update with CH option to get count of changed elements
        var options = new ZAddOptions().SetChanged(true);
        var updateMap = new Dictionary<GlideString, double>
        {
            { "member1", 10.0 }, // Update existing
            { "member3", 3.0 }   // Add new
        };
        long result = await client.ZAdd(key, updateMap, options);
        Assert.Equal(2, result); // 1 updated + 1 added = 2 changed
    }
}
