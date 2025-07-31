// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Valkey.Glide.IntegrationTests;

public class ListCommandTests(TestConfiguration config)
{
    public TestConfiguration Config { get; } = config;

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestLPush_LPop(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        Assert.Equal(2, await client.ListLeftPushAsync(key, ["test1", "test2"]));
        Assert.Equal(3, await client.ListLeftPushAsync(key, ["test3"]));

        ValkeyValue lPopResult1 = await client.ListLeftPopAsync(key);
        Assert.Equal("test3", lPopResult1.ToGlideString());

        ValkeyValue lPopResult2 = await client.ListLeftPopAsync(key);
        Assert.Equal("test2", lPopResult2.ToGlideString());

        ValkeyValue lPopResult3 = await client.ListLeftPopAsync("non-exist-key");
        Assert.Equal(ValkeyValue.Null, lPopResult3);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestLPopWithCount(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        Assert.Equal(4, await client.ListLeftPushAsync(key, ["test1", "test2", "test3", "test4"]));

        ValkeyValue[]? lPopResultWithCount = await client.ListLeftPopAsync(key, 2);
        Assert.Equal(["test4", "test3"], lPopResultWithCount!.ToGlideStrings());

        ValkeyValue[]? lPopResultWithCount2 = await client.ListLeftPopAsync(key, 10);
        Assert.Equal(["test2", "test1"], lPopResultWithCount2!.ToGlideStrings());

        ValkeyValue[]? lPopResultWithCount3 = await client.ListLeftPopAsync("non-exist-key", 10);
        Assert.Null(lPopResultWithCount3);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestRPush_RPop(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        // Test RPUSH - elements should be added to the tail
        Assert.Equal(2, await client.ListRightPushAsync(key, ["test1", "test2"]));
        Assert.Equal(3, await client.ListRightPushAsync(key, ["test3"]));

        // Test RPOP - should remove from tail (last added)
        ValkeyValue rPopResult1 = await client.ListRightPopAsync(key);
        Assert.Equal("test3", rPopResult1.ToGlideString());

        ValkeyValue rPopResult2 = await client.ListRightPopAsync(key);
        Assert.Equal("test2", rPopResult2.ToGlideString());

        // Test RPOP on non-existent key
        ValkeyValue rPopResult3 = await client.ListRightPopAsync("non-exist-key");
        Assert.Equal(ValkeyValue.Null, rPopResult3);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestLPushSingleValue(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        // Test LPUSH with single value
        Assert.Equal(1, await client.ListLeftPushAsync(key, "test1"));
        Assert.Equal(2, await client.ListLeftPushAsync(key, "test2"));
        Assert.Equal(3, await client.ListLeftPushAsync(key, "test3"));

        // Verify order by popping from left (should be test3, test2, test1)
        ValkeyValue lPopResult1 = await client.ListLeftPopAsync(key);
        Assert.Equal("test3", lPopResult1.ToGlideString());

        ValkeyValue lPopResult2 = await client.ListLeftPopAsync(key);
        Assert.Equal("test2", lPopResult2.ToGlideString());

        ValkeyValue lPopResult3 = await client.ListLeftPopAsync(key);
        Assert.Equal("test1", lPopResult3.ToGlideString());
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestRPushSingleValue(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        // Test RPUSH with single value
        Assert.Equal(1, await client.ListRightPushAsync(key, "test1"));
        Assert.Equal(2, await client.ListRightPushAsync(key, "test2"));
        Assert.Equal(3, await client.ListRightPushAsync(key, "test3"));

        // Verify order by popping from right (should be test3, test2, test1)
        ValkeyValue rPopResult1 = await client.ListRightPopAsync(key);
        Assert.Equal("test3", rPopResult1.ToGlideString());

        ValkeyValue rPopResult2 = await client.ListRightPopAsync(key);
        Assert.Equal("test2", rPopResult2.ToGlideString());

        ValkeyValue rPopResult3 = await client.ListRightPopAsync(key);
        Assert.Equal("test1", rPopResult3.ToGlideString());
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestRPopWithCount(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        // Setup list: [test1, test2, test3, test4] (left to right)
        Assert.Equal(4, await client.ListRightPushAsync(key, ["test1", "test2", "test3", "test4"]));

        // Pop 2 elements from right (tail)
        ValkeyValue[]? rPopResultWithCount = await client.ListRightPopAsync(key, 2);
        Assert.Equal(["test4", "test3"], rPopResultWithCount!.ToGlideStrings());

        // Pop more elements than available
        ValkeyValue[]? rPopResultWithCount2 = await client.ListRightPopAsync(key, 10);
        Assert.Equal(["test2", "test1"], rPopResultWithCount2!.ToGlideStrings());

        // Pop from non-existent key
        ValkeyValue[]? rPopResultWithCount3 = await client.ListRightPopAsync("non-exist-key", 10);
        Assert.Null(rPopResultWithCount3);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestListLength(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        // Test length of non-existent list
        Assert.Equal(0, await client.ListLengthAsync("non-exist-key"));

        // Test length after adding elements
        Assert.Equal(3, await client.ListLeftPushAsync(key, ["test1", "test2", "test3"]));
        Assert.Equal(3, await client.ListLengthAsync(key));

        // Test length after adding more elements
        Assert.Equal(5, await client.ListRightPushAsync(key, ["test4", "test5"]));
        Assert.Equal(5, await client.ListLengthAsync(key));

        // Test length after removing elements
        await client.ListLeftPopAsync(key);
        Assert.Equal(4, await client.ListLengthAsync(key));

        await client.ListRightPopAsync(key);
        Assert.Equal(3, await client.ListLengthAsync(key));
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestListRemove(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        // Setup list with duplicate values: [a, b, a, c, a, b]
        await client.ListRightPushAsync(key, ["a", "b", "a", "c", "a", "b"]);

        // Test removing all occurrences (count = 0)
        Assert.Equal(3, await client.ListRemoveAsync(key, "a", 0));
        Assert.Equal(3, await client.ListLengthAsync(key)); // Should have [b, c, b]

        // Reset list
        await client.KeyDeleteAsync(key);
        await client.ListRightPushAsync(key, ["a", "b", "a", "c", "a", "b"]);

        // Test removing from head to tail (count > 0)
        Assert.Equal(2, await client.ListRemoveAsync(key, "a", 2));
        Assert.Equal(4, await client.ListLengthAsync(key)); // Should have [b, c, a, b]

        // Reset list
        await client.KeyDeleteAsync(key);
        await client.ListRightPushAsync(key, ["a", "b", "a", "c", "a", "b"]);

        // Test removing from tail to head (count < 0)
        Assert.Equal(2, await client.ListRemoveAsync(key, "a", -2));
        Assert.Equal(4, await client.ListLengthAsync(key)); // Should have [a, b, c, b]

        // Test removing non-existent value
        Assert.Equal(0, await client.ListRemoveAsync(key, "x", 0));

        // Test removing from non-existent key
        Assert.Equal(0, await client.ListRemoveAsync("non-exist-key", "a", 0));
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestListTrim(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        // Setup list: [0, 1, 2, 3, 4, 5]
        await client.ListRightPushAsync(key, ["0", "1", "2", "3", "4", "5"]);

        // Trim to keep elements from index 1 to 3
        await client.ListTrimAsync(key, 1, 3);
        Assert.Equal(3, await client.ListLengthAsync(key));

        // Verify remaining elements
        ValkeyValue[] remaining = await client.ListRangeAsync(key, 0, -1);
        Assert.Equal(["1", "2", "3"], remaining.ToGlideStrings());

        // Test trim with negative indices
        await client.KeyDeleteAsync(key);
        await client.ListRightPushAsync(key, ["0", "1", "2", "3", "4", "5"]);

        // Keep last 3 elements
        await client.ListTrimAsync(key, -3, -1);
        Assert.Equal(3, await client.ListLengthAsync(key));

        ValkeyValue[] lastThree = await client.ListRangeAsync(key, 0, -1);
        Assert.Equal(["3", "4", "5"], lastThree.ToGlideStrings());

        // Test trim on non-existent key (should not throw)
        await client.ListTrimAsync("non-exist-key", 0, 1);
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestListRange(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        // Test range on non-existent key
        ValkeyValue[] emptyResult = await client.ListRangeAsync("non-exist-key", 0, -1);
        Assert.Empty(emptyResult);

        // Setup list: [0, 1, 2, 3, 4, 5]
        await client.ListRightPushAsync(key, ["0", "1", "2", "3", "4", "5"]);

        // Test getting all elements (default parameters)
        ValkeyValue[] allElements = await client.ListRangeAsync(key);
        Assert.Equal(["0", "1", "2", "3", "4", "5"], allElements.ToGlideStrings());

        // Test getting all elements explicitly
        ValkeyValue[] allElementsExplicit = await client.ListRangeAsync(key, 0, -1);
        Assert.Equal(["0", "1", "2", "3", "4", "5"], allElementsExplicit.ToGlideStrings());

        // Test getting subset
        ValkeyValue[] subset = await client.ListRangeAsync(key, 1, 3);
        Assert.Equal(["1", "2", "3"], subset.ToGlideStrings());

        // Test with negative indices
        ValkeyValue[] lastTwo = await client.ListRangeAsync(key, -2, -1);
        Assert.Equal(["4", "5"], lastTwo.ToGlideStrings());

        // Test with start > stop (should return empty)
        ValkeyValue[] invalidRange = await client.ListRangeAsync(key, 3, 1);
        Assert.Empty(invalidRange);

        // Test with out-of-bounds indices
        ValkeyValue[] outOfBounds = await client.ListRangeAsync(key, 10, 20);
        Assert.Empty(outOfBounds);

        // Test single element
        ValkeyValue[] singleElement = await client.ListRangeAsync(key, 2, 2);
        Assert.Equal(["2"], singleElement.ToGlideStrings());
    }

    [Theory(DisableDiscoveryEnumeration = true)]
    [MemberData(nameof(Config.TestClients), MemberType = typeof(TestConfiguration))]
    public async Task TestListCommandsIntegration(BaseClient client)
    {
        string key = Guid.NewGuid().ToString();

        // Test comprehensive workflow combining all list commands

        // 1. Build list using both LPUSH and RPUSH
        Assert.Equal(2, await client.ListLeftPushAsync(key, ["left2", "left1"])); // [left1, left2]
        Assert.Equal(4, await client.ListRightPushAsync(key, ["right1", "right2"])); // [left1, left2, right1, right2]
        Assert.Equal(6, await client.ListLeftPushAsync(key, ["extra2", "extra1"])); // [extra1, extra2, left1, left2, right1, right2]

        // 2. Verify length
        Assert.Equal(6, await client.ListLengthAsync(key));

        // 3. Check full range
        ValkeyValue[] fullList = await client.ListRangeAsync(key, 0, -1);
        Assert.Equal(["extra1", "extra2", "left1", "left2", "right1", "right2"], fullList.ToGlideStrings());

        // 4. Add duplicates and test removal
        await client.ListRightPushAsync(key, ["left1", "duplicate", "left1"]); // [extra1, extra2, left1, left2, right1, right2, left1, duplicate, left1]
        Assert.Equal(9, await client.ListLengthAsync(key));

        // Remove first 2 occurrences of "left1"
        Assert.Equal(2, await client.ListRemoveAsync(key, "left1", 2));
        Assert.Equal(7, await client.ListLengthAsync(key)); // [extra1, extra2, left2, right1, right2, duplicate, left1]

        // 5. Trim to middle section
        await client.ListTrimAsync(key, 2, 4); // Keep [left2, right1, right2]
        Assert.Equal(3, await client.ListLengthAsync(key));

        // 6. Verify final state
        ValkeyValue[] finalList = await client.ListRangeAsync(key, 0, -1);
        Assert.Equal(["left2", "right1", "right2"], finalList.ToGlideStrings());

        // 7. Pop remaining elements
        ValkeyValue leftPop = await client.ListLeftPopAsync(key);
        Assert.Equal("left2", leftPop.ToGlideString());

        ValkeyValue rightPop = await client.ListRightPopAsync(key);
        Assert.Equal("right2", rightPop.ToGlideString());

        Assert.Equal(1, await client.ListLengthAsync(key));

        ValkeyValue[] lastElement = await client.ListRangeAsync(key, 0, -1);
        Assert.Equal(["right1"], lastElement.ToGlideStrings());
    }
}
