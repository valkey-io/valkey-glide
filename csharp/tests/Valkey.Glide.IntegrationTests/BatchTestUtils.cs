// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Valkey.Glide.IntegrationTests;

internal class BatchTestUtils
{
    public static List<TestInfo> CreateStringTest(Pipeline.IBatch batch, bool isAtomic)
    {
        List<TestInfo> testData = [];
        string prefix = isAtomic ? "{stringKey}-" : "";
        string key1 = $"{prefix}1-{Guid.NewGuid()}";
        string key2 = $"{prefix}2-{Guid.NewGuid()}";
        string nonExistingKey = $"{prefix}nonexisting-{Guid.NewGuid()}";

        string value1 = $"value-1-{Guid.NewGuid()}";
        string value2 = "test-value";

        // Use IBatch interface directly - no casting needed
        _ = batch.StringSet(key1, value1);
        testData.Add(new(true, "StringSet(key1, value1)"));
        _ = batch.StringSet(key2, value2);
        testData.Add(new(true, "StringSet(key2, value2)"));
        _ = batch.StringGet(key1);
        testData.Add(new(new gs(value1), "StringGet(key1)"));
        _ = batch.StringGet(key2);
        testData.Add(new(new gs(value2), "StringGet(key2)"));
        _ = batch.StringLength(key1);
        testData.Add(new((long)value1.Length, "StringLength(key1)"));
        _ = batch.StringLength(key2);
        testData.Add(new((long)value2.Length, "StringLength(key2)"));
        _ = batch.StringLength(nonExistingKey);
        testData.Add(new(0L, "StringLength(nonExistingKey)"));

        // StringAppend tests
        string appendValue = "-appended";
        _ = batch.StringAppend(key1, appendValue);
        testData.Add(new((long)(value1.Length + appendValue.Length), "StringAppend(key1, appendValue)"));

        _ = batch.StringGet(key1);
        testData.Add(new(new gs(value1 + appendValue), "StringGet(key1) after append"));

        // Append to non-existing key (should create it)
        _ = batch.StringAppend(nonExistingKey, "new-value");
        testData.Add(new(9L, "StringAppend(nonExistingKey, new-value)"));

        _ = batch.StringGet(nonExistingKey);
        testData.Add(new(new gs("new-value"), "StringGet(nonExistingKey) after append"));

        // Append empty string
        _ = batch.StringAppend(key2, "");
        testData.Add(new((long)value2.Length, "StringAppend(key2, empty-string)"));

        _ = batch.StringGet(key2);
        testData.Add(new(new gs(value2), "StringGet(key2) after append empty string"));

        // Increment/Decrement tests
        string numKey1 = $"{prefix}num1-{Guid.NewGuid()}";
        string numKey2 = $"{prefix}num2-{Guid.NewGuid()}";
        string numKey3 = $"{prefix}num3-{Guid.NewGuid()}";
        string numKey4 = $"{prefix}num4-{Guid.NewGuid()}";
        string floatKey1 = $"{prefix}float1-{Guid.NewGuid()}";
        string floatKey2 = $"{prefix}float2-{Guid.NewGuid()}";

        // Set initial values
        _ = batch.StringSet(numKey1, "10");
        testData.Add(new(true, "StringSet(numKey1, 10)"));

        _ = batch.StringSet(numKey2, "20");
        testData.Add(new(true, "StringSet(numKey2, 20)"));

        _ = batch.StringSet(floatKey1, "10.5");
        testData.Add(new(true, "StringSet(floatKey1, 10.5)"));

        // Test StringIncrement (by 1)
        _ = batch.StringIncrement(numKey1);
        testData.Add(new(11L, "StringIncrement(numKey1)"));

        _ = batch.StringGet(numKey1);
        testData.Add(new(new gs("11"), "StringGet(numKey1) after increment"));

        // Test StringIncrement with amount
        _ = batch.StringIncrement(numKey2, 5);
        testData.Add(new(25L, "StringIncrement(numKey2, 5)"));

        _ = batch.StringGet(numKey2);
        testData.Add(new(new gs("25"), "StringGet(numKey2) after increment by 5"));

        // Test StringIncrement with negative amount
        _ = batch.StringIncrement(numKey2, -3);
        testData.Add(new(22L, "StringIncrement(numKey2, -3)"));

        _ = batch.StringGet(numKey2);
        testData.Add(new(new gs("22"), "StringGet(numKey2) after increment by -3"));

        // Test StringIncrement on non-existent key
        _ = batch.StringIncrement(numKey3);
        testData.Add(new(1L, "StringIncrement(numKey3) non-existent key"));

        _ = batch.StringGet(numKey3);
        testData.Add(new(new gs("1"), "StringGet(numKey3) after increment non-existent key"));

        // Test StringIncrement with float
        _ = batch.StringIncrement(floatKey1, 0.5);
        testData.Add(new(11.0, "StringIncrement(floatKey1, 0.5)"));

        _ = batch.StringGet(floatKey1);
        testData.Add(new(new gs("11"), "StringGet(floatKey1) after increment by 0.5"));

        // Test StringIncrement with float on non-existent key
        _ = batch.StringIncrement(floatKey2, 0.5);
        testData.Add(new(0.5, "StringIncrement(floatKey2, 0.5) non-existent key"));

        _ = batch.StringGet(floatKey2);
        testData.Add(new(new gs("0.5"), "StringGet(floatKey2) after increment non-existent key"));

        // Test StringDecrement (by 1)
        _ = batch.StringDecrement(numKey1);
        testData.Add(new(10L, "StringDecrement(numKey1)"));

        _ = batch.StringGet(numKey1);
        testData.Add(new(new gs("10"), "StringGet(numKey1) after decrement"));

        // Test StringDecrement with amount
        _ = batch.StringDecrement(numKey2, 2);
        testData.Add(new(20L, "StringDecrement(numKey2, 2)"));

        _ = batch.StringGet(numKey2);
        testData.Add(new(new gs("20"), "StringGet(numKey2) after decrement by 2"));

        // Test StringDecrement with negative amount
        _ = batch.StringDecrement(numKey2, -5);
        testData.Add(new(25L, "StringDecrement(numKey2, -5)"));

        _ = batch.StringGet(numKey2);
        testData.Add(new(new gs("25"), "StringGet(numKey2) after decrement by -5"));

        // Test StringDecrement on non-existent key
        _ = batch.StringDecrement(numKey4);
        testData.Add(new(-1L, "StringDecrement(numKey4) non-existent key"));

        _ = batch.StringGet(numKey4);
        testData.Add(new(new gs("-1"), "StringGet(numKey4) after decrement non-existent key"));

        return testData;
    }

    public static List<TestInfo> CreateSetTest(Pipeline.IBatch batch, bool isAtomic)
    {
        List<TestInfo> testData = [];
        string prefix = "{setKey}-";
        string atomicPrefix = isAtomic ? prefix : "";
        string key1 = $"{atomicPrefix}1-{Guid.NewGuid()}";
        string key2 = $"{atomicPrefix}2-{Guid.NewGuid()}";
        string key3 = $"{atomicPrefix}3-{Guid.NewGuid()}";
        string key4 = $"{atomicPrefix}4-{Guid.NewGuid()}";
        string key5 = $"{atomicPrefix}5-{Guid.NewGuid()}";
        string destKey = $"{atomicPrefix}dest-{Guid.NewGuid()}";

        _ = batch.SetAdd(key1, "a");
        testData.Add(new(true, "SetAdd(key1, a)"));

        _ = batch.SetAdd(key1, ["b", "c"]);
        testData.Add(new(2L, "SetAdd(key1, [b, c])"));

        _ = batch.SetLength(key1);
        testData.Add(new(3L, "SetLength(key1)"));

        _ = batch.SetMembers(key1);
        testData.Add(new(Array.Empty<ValkeyValue>(), "SetMembers(key1)", true));

        _ = batch.SetRemove(key1, "a");
        testData.Add(new(true, "SetRemove(key1, a)"));

        _ = batch.SetRemove(key1, "x");
        testData.Add(new(false, "SetRemove(key1, x)"));

        _ = batch.SetAdd(key1, ["d", "e"]);
        testData.Add(new(2L, "SetAdd(key1, [d, e])"));

        _ = batch.SetRemove(key1, ["b", "d", "nonexistent"]);
        testData.Add(new(2L, "SetRemove(key1, [b, d, nonexistent])"));

        _ = batch.SetLength(key1);
        testData.Add(new(2L, "SetLength(key1) after multiple remove"));

        _ = batch.SetAdd(key2, "c");
        testData.Add(new(true, "SetAdd(key2, c)"));

        _ = batch.SetAdd(key3, "z");
        testData.Add(new(true, "SetAdd(key3, z)"));

        _ = batch.SetAdd(key4, ["x", "y"]);
        testData.Add(new(2L, "SetAdd(key4, [x, y])"));

        _ = batch.SetAdd(key5, ["c", "f"]);
        testData.Add(new(2L, "SetAdd(key5, [c, f])"));

        // Add data to prefixed keys for multi-key operations
        _ = batch.SetAdd(prefix + key1, ["c", "e"]);
        testData.Add(new(2L, "SetAdd(prefix+key1, [c, e])"));

        _ = batch.SetAdd(prefix + key2, ["c"]);
        testData.Add(new(1L, "SetAdd(prefix+key2, [c])"));

        _ = batch.SetAdd(prefix + key5, ["c", "f"]);
        testData.Add(new(2L, "SetAdd(prefix+key5, [c, f])"));

        // Multi-key operations: always use prefix to ensure same hash slot
        _ = batch.SetUnion(prefix + key1, prefix + key2);
        testData.Add(new(Array.Empty<ValkeyValue>(), "SetUnion(prefix+key1, prefix+key2)", true));

        _ = batch.SetUnion([prefix + key1, prefix + key2, prefix + key5]);
        testData.Add(new(Array.Empty<ValkeyValue>(), "SetUnion([prefix+key1, prefix+key2, prefix+key5])", true));

        _ = batch.SetIntersect(prefix + key1, prefix + key2);
        testData.Add(new(Array.Empty<ValkeyValue>(), "SetIntersect(prefix+key1, prefix+key2)", true));

        _ = batch.SetIntersect([prefix + key1, prefix + key2]);
        testData.Add(new(Array.Empty<ValkeyValue>(), "SetIntersect([prefix+key1, prefix+key2])", true));

        _ = batch.SetDifference(prefix + key1, prefix + key2);
        testData.Add(new(Array.Empty<ValkeyValue>(), "SetDifference(prefix+key1, prefix+key2)", true));

        _ = batch.SetDifference([prefix + key1, prefix + key2]);
        testData.Add(new(Array.Empty<ValkeyValue>(), "SetDifference([prefix+key1, prefix+key2])", true));

        if (TestConfiguration.SERVER_VERSION >= new Version("7.0.0"))
        {
            _ = batch.SetIntersectionLength([prefix + key1, prefix + key2]);
            testData.Add(new(1L, "SetIntersectionLength([prefix+key1, prefix+key2])"));

            _ = batch.SetIntersectionLength([prefix + key1, prefix + key2], 5);
            testData.Add(new(1L, "SetIntersectionLength([prefix+key1, prefix+key2], limit=5)"));

            _ = batch.SetIntersectionLength([prefix + key1, prefix + key5], 1);
            testData.Add(new(1L, "SetIntersectionLength([prefix+key1, prefix+key5], limit=1)"));
        }

        _ = batch.SetUnionStore(prefix + destKey, prefix + key1, prefix + key2);
        testData.Add(new(2L, "SetUnionStore(prefix+destKey, prefix+key1, prefix+key2)"));

        _ = batch.SetUnionStore(prefix + destKey, [prefix + key1, prefix + key2]);
        testData.Add(new(2L, "SetUnionStore(prefix+destKey, [prefix+key1, prefix+key2])"));

        _ = batch.SetIntersectStore(prefix + destKey, prefix + key1, prefix + key2);
        testData.Add(new(1L, "SetIntersectStore(prefix+destKey, prefix+key1, prefix+key2)"));

        _ = batch.SetIntersectStore(prefix + destKey, [prefix + key1, prefix + key2]);
        testData.Add(new(1L, "SetIntersectStore(prefix+destKey, [prefix+key1, prefix+key2])"));

        _ = batch.SetDifferenceStore(prefix + destKey, prefix + key1, prefix + key2);
        testData.Add(new(1L, "SetDifferenceStore(prefix+destKey, prefix+key1, prefix+key2)"));

        _ = batch.SetDifferenceStore(prefix + destKey, [prefix + key1, prefix + key2]);
        testData.Add(new(1L, "SetDifferenceStore(prefix+destKey, [prefix+key1, prefix+key2])"));

        _ = batch.SetPop(key3);
        testData.Add(new(new gs("z"), "SetPop(key3)"));

        _ = batch.SetLength(key3);
        testData.Add(new(0L, "SetLength(key3) after pop"));

        _ = batch.SetPop(key4, 1);
        testData.Add(new(Array.Empty<ValkeyValue>(), "SetPop(key4, 1)", true));

        _ = batch.SetLength(key4);
        testData.Add(new(1L, "SetLength(key4) after pop with count"));

        return testData;
    }

    public static List<TestInfo> CreateGenericTest(Pipeline.IBatch batch, bool isAtomic)
    {
        List<TestInfo> testData = [];
        string prefix = "{genericKey}-";
        string atomicPrefix = isAtomic ? "{genericKey}-" : "";
        string genericKey1 = $"{atomicPrefix}generic1-{Guid.NewGuid()}";
        string genericKey2 = $"{atomicPrefix}generic2-{Guid.NewGuid()}";
        string genericKey3 = $"{atomicPrefix}generic3-{Guid.NewGuid()}";

        _ = batch.StringSet(genericKey1, "value1");
        testData.Add(new(true, "StringSet(genericKey1, value1)"));

        _ = batch.StringSet(genericKey2, "value2");
        testData.Add(new(true, "StringSet(genericKey2, value2)"));

        _ = batch.KeyExists(genericKey1);
        testData.Add(new(true, "KeyExists(genericKey1)"));

        _ = batch.KeyExists([genericKey1, genericKey2, genericKey3]);
        testData.Add(new(2L, "KeyExists([genericKey1, genericKey2, genericKey3])"));

        _ = batch.KeyType(genericKey1);
        testData.Add(new(ValkeyType.String, "KeyType(genericKey1)"));

        _ = batch.KeyExpire(genericKey1, TimeSpan.FromSeconds(60));
        testData.Add(new(true, "KeyExpire(genericKey1, 60s)"));

        _ = batch.KeyTimeToLive(genericKey1);
        testData.Add(new(TimeSpan.FromSeconds(60), "KeyTimeToLive(genericKey1)", true));

        _ = batch.KeyPersist(genericKey1);
        testData.Add(new(true, "KeyPersist(genericKey1)"));

        _ = batch.KeyTimeToLive(genericKey1);
        testData.Add(new(null, "KeyTimeToLive(genericKey1) after persist"));

        _ = batch.KeyTouch(genericKey1);
        testData.Add(new(true, "KeyTouch(genericKey1)"));

        _ = batch.KeyTouch([genericKey1, genericKey2, genericKey3]);
        testData.Add(new(2L, "KeyTouch([genericKey1, genericKey2, genericKey3])"));

        _ = batch.StringSet(prefix + genericKey2, "value2");
        testData.Add(new(true, "StringSet(prefix + genericKey2, value2)"));

        string renamedKey = $"{prefix}renamed-{Guid.NewGuid()}";
        _ = batch.KeyRename(prefix + genericKey2, renamedKey);
        testData.Add(new(true, "KeyRename(prefix + genericKey2, renamedKey)"));

        _ = batch.KeyExists(prefix + genericKey2);
        testData.Add(new(false, "KeyExists(prefix + genericKey2) after rename"));

        _ = batch.KeyExists(renamedKey);
        testData.Add(new(true, "KeyExists(renamedKey) after rename"));

        string renameNXKey = $"{prefix}renamenx-{Guid.NewGuid()}";
        _ = batch.KeyRenameNX(renamedKey, renameNXKey);
        testData.Add(new(true, "KeyRenameNX(renamedKey, renameNXKey)"));

        _ = batch.KeyExists(renamedKey);
        testData.Add(new(false, "KeyExists(renamedKey) after renamenx"));

        _ = batch.KeyExists(renameNXKey);
        testData.Add(new(true, "KeyExists(renameNXKey) after renamenx"));

        _ = batch.StringSet(prefix + genericKey1, "value1");
        testData.Add(new(true, "StringSet(prefix + genericKey1, value1)"));

        string copiedKey = $"{prefix}copied-{Guid.NewGuid()}";
        _ = batch.KeyCopy(prefix + genericKey1, copiedKey);
        testData.Add(new(true, "KeyCopy(genericKey1, copiedKey)"));

        _ = batch.KeyExists(copiedKey);
        testData.Add(new(true, "KeyExists(copiedKey) after copy"));

        _ = batch.KeyDelete(copiedKey);
        testData.Add(new(true, "KeyDelete(copiedKey)"));

        _ = batch.KeyUnlink([genericKey1, renamedKey, genericKey3]);
        testData.Add(new(1L, "KeyUnlink([genericKey1, renamedKey, genericKey3])"));

        return testData;
    }

    public static List<TestInfo> CreateSortedSetTest(Pipeline.IBatch batch, bool isAtomic)
    {
        List<TestInfo> testData = [];
        string prefix = isAtomic ? "{sortedSetKey}-" : "";
        string key1 = $"{prefix}1-{Guid.NewGuid()}";
        string key2 = $"{prefix}2-{Guid.NewGuid()}";

        // Test single member add
        _ = batch.SortedSetAdd(key1, "member1", 10.5);
        testData.Add(new(true, "SortedSetAdd(key1, member1, 10.5)"));

        // Test multiple members add
        SortedSetEntry[] entries =
        [
            new("member2", 8.2),
            new("member3", 15.0)
        ];
        _ = batch.SortedSetAdd(key1, entries);
        testData.Add(new(2L, "SortedSetAdd(key1, [member2:8.2, member3:15.0])"));

        // Test add with NX (should not add existing member)
        _ = batch.SortedSetAdd(key1, "member1", 20.0, SortedSetWhen.NotExists);
        testData.Add(new(false, "SortedSetAdd(key1, member1, 20.0, NotExists)"));

        // Test add with XX (should update existing member)
        _ = batch.SortedSetAdd(key1, "member2", 12.0, SortedSetWhen.Exists);
        testData.Add(new(false, "SortedSetAdd(key1, member2, 12.0, Exists)"));

        // Test add new member with NX
        _ = batch.SortedSetAdd(key2, "newMember", 7.5, SortedSetWhen.NotExists);
        testData.Add(new(true, "SortedSetAdd(key2, newMember, 7.5, NotExists)"));

        // Test single member remove
        _ = batch.SortedSetRemove(key1, "member1");
        testData.Add(new(true, "SortedSetRemove(key1, member1)"));

        // Test multiple member remove
        _ = batch.SortedSetRemove(key1, ["member2", "member3"]);
        testData.Add(new(2L, "SortedSetRemove(key1, [member2, member3])"));

        // Test remove non-existent member
        _ = batch.SortedSetRemove(key1, "nonexistent");
        testData.Add(new(false, "SortedSetRemove(key1, nonexistent)"));

        // Add some test data for length, count, and range operations
        _ = batch.SortedSetAdd(key1, "testMember1", 1.0);
        testData.Add(new(true, "SortedSetAdd(key1, testMember1, 1.0)"));

        _ = batch.SortedSetAdd(key1, "testMember2", 2.0);
        testData.Add(new(true, "SortedSetAdd(key1, testMember2, 2.0)"));

        _ = batch.SortedSetAdd(key1, "testMember3", 3.0);
        testData.Add(new(true, "SortedSetAdd(key1, testMember3, 3.0)"));

        // Test SortedSetLength
        _ = batch.SortedSetLength(key1);
        testData.Add(new(3L, "SortedSetLength(key1)"));

        _ = batch.SortedSetLength(key2);
        testData.Add(new(1L, "SortedSetLength(key2)"));

        // Test SortedSetCount
        _ = batch.SortedSetCount(key1);
        testData.Add(new(3L, "SortedSetCount(key1) - all elements"));

        _ = batch.SortedSetCount(key1, 1.5, 2.5);
        testData.Add(new(1L, "SortedSetCount(key1, 1.5, 2.5)"));

        _ = batch.SortedSetCount(key1, 1.0, 3.0, Exclude.Start);
        testData.Add(new(2L, "SortedSetCount(key1, 1.0, 3.0, Exclude.Start)"));

        // Test SortedSetRangeByRank
        _ = batch.SortedSetRangeByRank(key1);
        testData.Add(new(Array.Empty<ValkeyValue>(), "SortedSetRangeByRank(key1) - all elements", true));

        _ = batch.SortedSetRangeByRank(key1, 0, 1);
        testData.Add(new(Array.Empty<ValkeyValue>(), "SortedSetRangeByRank(key1, 0, 1)", true));

        _ = batch.SortedSetRangeByRank(key1, 0, 1, Order.Descending);
        testData.Add(new(Array.Empty<ValkeyValue>(), "SortedSetRangeByRank(key1, 0, 1, Descending)", true));

        // Test SortedSetRangeByRankWithScores
        _ = batch.SortedSetRangeByRankWithScores(key1);
        testData.Add(new(Array.Empty<SortedSetEntry>(), "SortedSetRangeByRankWithScores(key1) - all elements", true));

        _ = batch.SortedSetRangeByRankWithScores(key1, 0, 1);
        testData.Add(new(Array.Empty<SortedSetEntry>(), "SortedSetRangeByRankWithScores(key1, 0, 1)", true));

        // Test SortedSetRangeByScore
        _ = batch.SortedSetRangeByScore(key1, 1.0, 3.0);
        testData.Add(new(Array.Empty<ValkeyValue>(), "SortedSetRangeByScore(key1, 1.0, 3.0)", true));

        _ = batch.SortedSetRangeByScore(key1, 1.0, 3.0, Exclude.None, Order.Descending);
        testData.Add(new(Array.Empty<ValkeyValue>(), "SortedSetRangeByScore(key1, 1.0, 3.0, Descending)", true));

        // Test SortedSetRangeByScoreWithScores
        _ = batch.SortedSetRangeByScoreWithScores(key1, 1.0, 3.0);
        testData.Add(new(Array.Empty<SortedSetEntry>(), "SortedSetRangeByScoreWithScores(key1, 1.0, 3.0)", true));

        _ = batch.SortedSetRangeByScoreWithScores(key1, 1.0, 3.0, skip: 1, take: 1);
        testData.Add(new(Array.Empty<SortedSetEntry>(), "SortedSetRangeByScoreWithScores(key1, 1.0, 3.0, skip: 1, take: 1)", true));

        // Add members with same score for lexicographical ordering tests
        _ = batch.SortedSetAdd(key2, "apple", 0.0);
        testData.Add(new(false, "SortedSetAdd(key2, apple, 0.0)"));

        _ = batch.SortedSetAdd(key2, "banana", 0.0);
        testData.Add(new(true, "SortedSetAdd(key2, banana, 0.0)"));

        _ = batch.SortedSetAdd(key2, "cherry", 0.0);
        testData.Add(new(true, "SortedSetAdd(key2, cherry, 0.0)"));

        // Test SortedSetRangeByValue
        _ = batch.SortedSetRangeByValue(key2, "a", "c", Exclude.None, 0, -1);
        testData.Add(new(Array.Empty<ValkeyValue>(), "SortedSetRangeByValue(key2, 'a', 'c', Exclude.None, 0, -1)", true));

        _ = batch.SortedSetRangeByValue(key2, "b", "d", Exclude.None, skip: 1, take: 1);
        testData.Add(new(Array.Empty<ValkeyValue>(), "SortedSetRangeByValue(key2, 'b', 'd', Exclude.None, skip: 1, take: 1)", true));

        // Test SortedSetRangeByValue
        _ = batch.SortedSetRangeByValue(key2, order: Order.Descending);
        testData.Add(new(Array.Empty<ValkeyValue>(), "SortedSetRangeByValue(key2, order: Descending)", true));

        _ = batch.SortedSetRangeByValue(key2, "a", "c", order: Order.Ascending);
        testData.Add(new(Array.Empty<ValkeyValue>(), "SortedSetRangeByValue(key2, 'a', 'c', order: Ascending)", true));

        return testData;
    }

    public static List<TestInfo> CreateListTest(Pipeline.IBatch batch, bool isAtomic)
    {
        List<TestInfo> testData = [];
        string prefix = isAtomic ? "{listKey}-" : "";
        string key1 = $"{prefix}1-{Guid.NewGuid()}";
        string key2 = $"{prefix}2-{Guid.NewGuid()}";
        string key3 = $"{prefix}3-{Guid.NewGuid()}";

        string value1 = $"value-1-{Guid.NewGuid()}";
        string value2 = $"value-2-{Guid.NewGuid()}";
        string value3 = $"value-3-{Guid.NewGuid()}";
        string value4 = $"value-4-{Guid.NewGuid()}";

        _ = batch.ListLeftPush(key1, [value1, value2]);
        testData.Add(new(2L, "ListLeftPush(key1, [value1, value2])"));

        _ = batch.ListLeftPush(key1, [value3]);
        testData.Add(new(3L, "ListLeftPush(key1, [value3])"));

        _ = batch.ListLeftPop(key1);
        testData.Add(new(new ValkeyValue(value3), "ListLeftPop(key1)"));

        _ = batch.ListLeftPop(key1);
        testData.Add(new(new ValkeyValue(value2), "ListLeftPop(key1) second"));

        _ = batch.ListLeftPush(key2, [value1, value2, value3, value4]);
        testData.Add(new(4L, "ListLeftPush(key2, [value1, value2, value3, value4])"));

        _ = batch.ListLeftPop(key2, 2);
        testData.Add(new(ValkeyValue.EmptyArray, "ListLeftPop(key2, 2)", true));

        _ = batch.ListLeftPop(key2, 10);
        testData.Add(new(Array.Empty<ValkeyValue>(), "ListLeftPop(key2, 10)", true));

        _ = batch.ListLeftPop(key3);
        // TODO: switch expected back to `ValkeyValue.Null` after converter fix
        testData.Add(new(null, "ListLeftPop(key3) non-existent"));

        _ = batch.ListLeftPop(key3, 5);
        testData.Add(new(null, "ListLeftPop(key3, 5) non-existent"));

        return testData;
    }

    public static List<TestInfo> CreateConnectionManagementTest(Pipeline.IBatch batch, bool isAtomic)
    {
        List<TestInfo> testData = [];

        _ = batch.Ping();
        testData.Add(new(TimeSpan.Zero, "Ping()", true));

        ValkeyValue pingMessage = "Hello Valkey!";
        _ = batch.Ping(pingMessage);
        testData.Add(new(TimeSpan.Zero, "Ping(message)", true));

        ValkeyValue echoMessage = "Echo test message";
        _ = batch.Echo(echoMessage);
        testData.Add(new(echoMessage, "Echo(message)"));

        _ = batch.Echo("");
        testData.Add(new(new ValkeyValue(""), "Echo(empty)"));

        return testData;
    }

    public static TheoryData<BatchTestData> GetTestClientWithAtomic =>
        [.. TestConfiguration.TestClients.SelectMany(r => new[] { true, false }.SelectMany(isAtomic =>
            new BatchTestData[] {
                new("String commands", r.Data, CreateStringTest, isAtomic),
                new("Set commands", r.Data, CreateSetTest, isAtomic),
                new("Generic commands", r.Data, CreateGenericTest, isAtomic),
                new("List commands", r.Data, CreateListTest, isAtomic),
                new("Connection Management commands", r.Data, CreateConnectionManagementTest, isAtomic),
            }))];
}

internal delegate List<TestInfo> BatchTestDataProvider(Pipeline.IBatch batch, bool isAtomic);

internal record BatchTestData(string TestName, BaseClient Client, BatchTestDataProvider TestDataProvider, bool IsAtomic)
{
    public string TestName = TestName;
    public BaseClient Client = Client;
    public BatchTestDataProvider TestDataProvider = TestDataProvider;
    public bool IsAtomic = IsAtomic;

    public override string? ToString() => $"{TestName} {Client} IsAtomic = {IsAtomic}";
}

internal record TestInfo(object? Expected, string TestName, bool CheckTypeOnly = false)
{
    public object? ExpectedValue = Expected;
    public string TestName = TestName;
    public bool CheckTypeOnly = CheckTypeOnly;
}
