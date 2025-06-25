// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Commands.Options;

using static Valkey.Glide.Errors;

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

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task ZRemBasicTest(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        // First add some members
        var membersScoreMap = new Dictionary<GlideString, double>
        {
            { "member1", 1.0 },
            { "member2", 2.0 },
            { "member3", 3.0 }
        };

        await client.ZAdd(key, membersScoreMap);

        // Remove existing members
        long result1 = await client.ZRem(key, ["member1", "member2"]);
        Assert.Equal(2, result1);

        // Try to remove non-existing member
        long result2 = await client.ZRem(key, ["nonExistingMember"]);
        Assert.Equal(0, result2);

        // Remove remaining member
        long result3 = await client.ZRem(key, ["member3"]);
        Assert.Equal(1, result3);

        // Try to remove from non-existing key
        long result4 = await client.ZRem("nonExistingKey", ["member1"]);
        Assert.Equal(0, result4);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task ZRemErrorCasesTest(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        // Setup sorted set
        var membersScoreMap = new Dictionary<GlideString, double>
        {
            { "member1", 1.0 },
            { "member2", 2.0 }
        };
        await client.ZAdd(key, membersScoreMap);

        // Test empty members array - should throw error
        await Assert.ThrowsAsync<RequestException>(async () =>
            await client.ZRem(key, []));

        // Test wrong key type - should throw error
        string stringKey = Guid.NewGuid().ToString();
        await client.Set(stringKey, "test");
        await Assert.ThrowsAsync<RequestException>(async () =>
            await client.ZRem(stringKey, ["value"]));
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task ZRangeSimpleTest(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        // First add some members
        var membersScoreMap = new Dictionary<GlideString, double>
        {
            { "a", 1.0 },
            { "b", 2.0 },
            { "c", 3.0 }
        };

        await client.ZAdd(key, membersScoreMap);

        // Test simple range by index [0:-1] (all)
        GlideString[] result = await client.ZRange(key, new RangeByIndex(0, -1));
        Assert.Equal(new GlideString[] { "a", "b", "c" }, result);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task ZRangeBasicTest(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        // First add some members
        var membersScoreMap = new Dictionary<GlideString, double>
        {
            { "a", 1.0 },
            { "b", 2.0 },
            { "c", 3.0 }
        };

        await client.ZAdd(key, membersScoreMap);

        // Test range by index [0:1]
        GlideString[] result1 = await client.ZRange(key, new RangeByIndex(0, 1));
        Assert.Equal(new GlideString[] { "a", "b" }, result1);

        // Test range by index [0:-1] (all)
        GlideString[] result2 = await client.ZRange(key, new RangeByIndex(0, -1));
        Assert.Equal(new GlideString[] { "a", "b", "c" }, result2);

        // Test range by index [3:1] (none)
        GlideString[] result3 = await client.ZRange(key, new RangeByIndex(3, 1));
        Assert.Empty(result3);

        // Test range by score [-inf:3]
        GlideString[] result4 = await client.ZRange(key, new RangeByScore(ScoreBoundary.Infinite(InfBoundary.NegativeInfinity), ScoreBoundary.Inclusive(3.0)));
        Assert.Equal(new GlideString[] { "a", "b", "c" }, result4);

        // Test range by score [-inf:3)
        GlideString[] result5 = await client.ZRange(key, new RangeByScore(ScoreBoundary.Infinite(InfBoundary.NegativeInfinity), ScoreBoundary.Exclusive(3.0)));
        Assert.Equal(new GlideString[] { "a", "b" }, result5);

        // Test range by score with reverse
        GlideString[] result6 = await client.ZRange(key, new RangeByScore(ScoreBoundary.Exclusive(3.0), ScoreBoundary.Infinite(InfBoundary.NegativeInfinity)).SetReverse());
        Assert.Equal(new GlideString[] { "b", "a" }, result6);

        // Test range by score with limit
        GlideString[] result7 = await client.ZRange(key, new RangeByScore(ScoreBoundary.Infinite(InfBoundary.NegativeInfinity), ScoreBoundary.Infinite(InfBoundary.PositiveInfinity)).SetLimit(1, 2));
        Assert.Equal(new GlideString[] { "b", "c" }, result7);

        // Test range by lex [-:c)
        GlideString[] result8 = await client.ZRange(key, new RangeByLex(LexBoundary.Infinite(InfBoundary.NegativeInfinity), LexBoundary.Exclusive("c")));
        Assert.Equal(new GlideString[] { "a", "b" }, result8);

        // Test range by lex with reverse and limit
        GlideString[] result9 = await client.ZRange(key, new RangeByLex(LexBoundary.Infinite(InfBoundary.PositiveInfinity), LexBoundary.Infinite(InfBoundary.NegativeInfinity)).SetReverse().SetLimit(1, 2));
        Assert.Equal(new GlideString[] { "b", "a" }, result9);

        // Test non-existing key
        GlideString[] result10 = await client.ZRange("nonExistingKey", new RangeByIndex(0, -1));
        Assert.Empty(result10);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task ZRangeWithScoresBasicTest(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        // First add some members
        var membersScoreMap = new Dictionary<GlideString, double>
        {
            { "a", 2.0 },
            { "ab", 2.0 },
            { "b", 4.0 },
            { "c", 3.0 },
            { "d", 8.0 },
            { "e", 5.0 },
            { "f", 1.0 },
            { "ac", 2.0 },
            { "g", 2.0 }
        };

        await client.ZAdd(key, membersScoreMap);

        // Test simple range by index [0:1] first with debugging
        try
        {
            MemberAndScore[] result1 = await client.ZRangeWithScores(key, new RangeByIndex(0, 1));
            MemberAndScore[] expected1 = new MemberAndScore[]
            {
                new MemberAndScore("f", 1.0),
                new MemberAndScore("a", 2.0)
            };
            Assert.Equal(expected1, result1);
        }
        catch (Exception ex)
        {
            // Log the exception details for debugging
            throw new Exception($"ZRangeWithScores failed with: {ex.Message}", ex);
        }

        // Test range by index [0:-1] (all)
        MemberAndScore[] result2 = await client.ZRangeWithScores(key, new RangeByIndex(0, -1));
        MemberAndScore[] expected2 = new MemberAndScore[]
        {
            new MemberAndScore("f", 1.0),
            new MemberAndScore("a", 2.0),
            new MemberAndScore("ab", 2.0),
            new MemberAndScore("ac", 2.0),
            new MemberAndScore("g", 2.0),
            new MemberAndScore("c", 3.0),
            new MemberAndScore("b", 4.0),
            new MemberAndScore("e", 5.0),
            new MemberAndScore("d", 8.0)
        };
        Assert.Equal(expected2, result2);

        // Test range by index [3:1] (none)
        MemberAndScore[] result3 = await client.ZRangeWithScores(key, new RangeByIndex(3, 1));
        Assert.Empty(result3);

        // Test range by score [-inf:3]
        MemberAndScore[] result4 = await client.ZRangeWithScores(key, new RangeByScore(ScoreBoundary.Infinite(InfBoundary.NegativeInfinity), ScoreBoundary.Inclusive(3.0)));
        MemberAndScore[] expected4 = new MemberAndScore[]
        {
            new MemberAndScore("f", 1.0),
            new MemberAndScore("a", 2.0),
            new MemberAndScore("ab", 2.0),
            new MemberAndScore("ac", 2.0),
            new MemberAndScore("g", 2.0),
            new MemberAndScore("c", 3.0)
        };
        Assert.Equal(expected4, result4);

        // Test range by score [-inf:3)
        MemberAndScore[] result5 = await client.ZRangeWithScores(key, new RangeByScore(ScoreBoundary.Infinite(InfBoundary.NegativeInfinity), ScoreBoundary.Exclusive(3.0)));
        MemberAndScore[] expected5 = new MemberAndScore[]
        {
            new MemberAndScore("f", 1.0),
            new MemberAndScore("a", 2.0),
            new MemberAndScore("ab", 2.0),
            new MemberAndScore("ac", 2.0),
            new MemberAndScore("g", 2.0)
        };
        Assert.Equal(expected5, result5);

        // Test range by score with reverse
        MemberAndScore[] result6 = await client.ZRangeWithScores(key, new RangeByScore(ScoreBoundary.Exclusive(3.0), ScoreBoundary.Infinite(InfBoundary.NegativeInfinity)).SetReverse());
        MemberAndScore[] expected6 = new MemberAndScore[]
        {
            new MemberAndScore("g", 2.0),
            new MemberAndScore("ac", 2.0),
            new MemberAndScore("ab", 2.0),
            new MemberAndScore("a", 2.0),
            new MemberAndScore("f", 1.0)
        };
        Assert.Equal(expected6, result6);

        // Test range by score with limit
        MemberAndScore[] result7 = await client.ZRangeWithScores(key, new RangeByScore(ScoreBoundary.Infinite(InfBoundary.NegativeInfinity), ScoreBoundary.Infinite(InfBoundary.PositiveInfinity)).SetLimit(4, 2));
        MemberAndScore[] expected7 = new MemberAndScore[]
        {
            new MemberAndScore("g", 2.0),
            new MemberAndScore("c", 3.0)
        };
        Assert.Equal(expected7, result7);

        // Test non-existing key
        MemberAndScore[] result8 = await client.ZRangeWithScores("nonExistingKey", new RangeByIndex(0, -1));
        Assert.Empty(result8);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task ZRangeErrorCasesTest(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        // Setup sorted set
        var membersScoreMap = new Dictionary<GlideString, double>
        {
            { "member1", 1.0 },
            { "member2", 2.0 }
        };
        await client.ZAdd(key, membersScoreMap);

        // Test wrong key type - should throw error
        string stringKey = Guid.NewGuid().ToString();
        await client.Set(stringKey, "test");
        await Assert.ThrowsAsync<RequestException>(async () =>
            await client.ZRange(stringKey, new RangeByIndex(0, -1)));
        await Assert.ThrowsAsync<RequestException>(async () =>
            await client.ZRangeWithScores(stringKey, new RangeByIndex(0, -1)));
    }
}
