// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Commands;
using Valkey.Glide.Internals;

namespace Valkey.Glide;

public abstract partial class BaseClient : ISetCommands
{
    public async Task<bool> SetAddAsync(ValkeyKey key, ValkeyValue value, CommandFlags flags = CommandFlags.None)
    {
        Utils.Requires<NotImplementedException>(flags == CommandFlags.None, "Command flags are not supported by GLIDE");
        return await Command(Request.SetAddAsync(key, value));
    }

    public async Task<long> SetAddAsync(ValkeyKey key, ValkeyValue[] values, CommandFlags flags = CommandFlags.None)
    {
        Utils.Requires<NotImplementedException>(flags == CommandFlags.None, "Command flags are not supported by GLIDE");
        return await Command(Request.SetAddAsync(key, values));
    }

    public async Task<bool> SetRemoveAsync(ValkeyKey key, ValkeyValue value, CommandFlags flags = CommandFlags.None)
    {
        Utils.Requires<NotImplementedException>(flags == CommandFlags.None, "Command flags are not supported by GLIDE");
        return await Command(Request.SetRemoveAsync(key, value));
    }

    public async Task<long> SetRemoveAsync(ValkeyKey key, ValkeyValue[] values, CommandFlags flags = CommandFlags.None)
    {
        Utils.Requires<NotImplementedException>(flags == CommandFlags.None, "Command flags are not supported by GLIDE");
        return await Command(Request.SetRemoveAsync(key, values));
    }

    public async Task<ValkeyValue[]> SetMembersAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None)
    {
        Utils.Requires<NotImplementedException>(flags == CommandFlags.None, "Command flags are not supported by GLIDE");
        return await Command(Request.SetMembersAsync(key));
    }

    public async Task<long> SetLengthAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None)
    {
        Utils.Requires<NotImplementedException>(flags == CommandFlags.None, "Command flags are not supported by GLIDE");
        return await Command(Request.SetLengthAsync(key));
    }

    public async Task<long> SetIntersectionLengthAsync(ValkeyKey[] keys, long limit = 0, CommandFlags flags = CommandFlags.None)
    {
        Utils.Requires<NotImplementedException>(flags == CommandFlags.None, "Command flags are not supported by GLIDE");
        return await Command(Request.SetIntersectionLengthAsync(keys, limit));
    }

    public async Task<ValkeyValue> SetPopAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None)
    {
        Utils.Requires<NotImplementedException>(flags == CommandFlags.None, "Command flags are not supported by GLIDE");
        return await Command(Request.SetPopAsync(key));
    }

    public async Task<ValkeyValue[]> SetPopAsync(ValkeyKey key, long count, CommandFlags flags = CommandFlags.None)
    {
        Utils.Requires<NotImplementedException>(flags == CommandFlags.None, "Command flags are not supported by GLIDE");
        return await Command(Request.SetPopAsync(key, count));
    }

    public async Task<ValkeyValue[]> SetUnionAsync(ValkeyKey first, ValkeyKey second, CommandFlags flags = CommandFlags.None)
        => await SetUnionAsync([first, second], flags);

    public async Task<ValkeyValue[]> SetUnionAsync(ValkeyKey[] keys, CommandFlags flags = CommandFlags.None)
    {
        Utils.Requires<NotImplementedException>(flags == CommandFlags.None, "Command flags are not supported by GLIDE");
        return await Command(Request.SetUnionAsync(keys));
    }

    public async Task<ValkeyValue[]> SetIntersectAsync(ValkeyKey first, ValkeyKey second, CommandFlags flags = CommandFlags.None)
        => await SetIntersectAsync([first, second], flags);

    public async Task<ValkeyValue[]> SetIntersectAsync(ValkeyKey[] keys, CommandFlags flags = CommandFlags.None)
    {
        Utils.Requires<NotImplementedException>(flags == CommandFlags.None, "Command flags are not supported by GLIDE");
        return await Command(Request.SetIntersectAsync(keys));
    }

    public async Task<ValkeyValue[]> SetDifferenceAsync(ValkeyKey first, ValkeyKey second, CommandFlags flags = CommandFlags.None)
        => await SetDifferenceAsync([first, second], flags);

    public async Task<ValkeyValue[]> SetDifferenceAsync(ValkeyKey[] keys, CommandFlags flags = CommandFlags.None)
    {
        Utils.Requires<NotImplementedException>(flags == CommandFlags.None, "Command flags are not supported by GLIDE");
        return await Command(Request.SetDifferenceAsync(keys));
    }

    public async Task<long> SetUnionStoreAsync(ValkeyKey destination, ValkeyKey first, ValkeyKey second, CommandFlags flags = CommandFlags.None)
        => await SetUnionStoreAsync(destination, [first, second], flags);

    public async Task<long> SetUnionStoreAsync(ValkeyKey destination, ValkeyKey[] keys, CommandFlags flags = CommandFlags.None)
    {
        Utils.Requires<NotImplementedException>(flags == CommandFlags.None, "Command flags are not supported by GLIDE");
        return await Command(Request.SetUnionStoreAsync(destination, keys));
    }

    public async Task<long> SetIntersectStoreAsync(ValkeyKey destination, ValkeyKey first, ValkeyKey second, CommandFlags flags = CommandFlags.None)
        => await SetIntersectStoreAsync(destination, [first, second], flags);

    public async Task<long> SetIntersectStoreAsync(ValkeyKey destination, ValkeyKey[] keys, CommandFlags flags = CommandFlags.None)
    {
        Utils.Requires<NotImplementedException>(flags == CommandFlags.None, "Command flags are not supported by GLIDE");
        return await Command(Request.SetIntersectStoreAsync(destination, keys));
    }

    public async Task<long> SetDifferenceStoreAsync(ValkeyKey destination, ValkeyKey first, ValkeyKey second, CommandFlags flags = CommandFlags.None)
        => await SetDifferenceStoreAsync(destination, [first, second], flags);

    public async Task<long> SetDifferenceStoreAsync(ValkeyKey destination, ValkeyKey[] keys, CommandFlags flags = CommandFlags.None)
    {
        Utils.Requires<NotImplementedException>(flags == CommandFlags.None, "Command flags are not supported by GLIDE");
        return await Command(Request.SetDifferenceStoreAsync(destination, keys));
    }

    public async Task<bool> SetContainsAsync(ValkeyKey key, ValkeyValue value, CommandFlags flags = CommandFlags.None)
    {
        Utils.Requires<NotImplementedException>(flags == CommandFlags.None, "Command flags are not supported by GLIDE");
        return await Command(Request.SetContainsAsync(key, value));
    }

    public async Task<bool[]> SetContainsAsync(ValkeyKey key, ValkeyValue[] values, CommandFlags flags = CommandFlags.None)
    {
        Utils.Requires<NotImplementedException>(flags == CommandFlags.None, "Command flags are not supported by GLIDE");
        return await Command(Request.SetContainsAsync(key, values));
    }

    public async Task<ValkeyValue> SetRandomMemberAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None)
    {
        Utils.Requires<NotImplementedException>(flags == CommandFlags.None, "Command flags are not supported by GLIDE");
        return await Command(Request.SetRandomMemberAsync(key));
    }

    public async Task<ValkeyValue[]> SetRandomMembersAsync(ValkeyKey key, long count, CommandFlags flags = CommandFlags.None)
    {
        Utils.Requires<NotImplementedException>(flags == CommandFlags.None, "Command flags are not supported by GLIDE");
        return await Command(Request.SetRandomMembersAsync(key, count));
    }

    public async Task<bool> SetMoveAsync(ValkeyKey source, ValkeyKey destination, ValkeyValue value, CommandFlags flags = CommandFlags.None)
    {
        Utils.Requires<NotImplementedException>(flags == CommandFlags.None, "Command flags are not supported by GLIDE");
        return await Command(Request.SetMoveAsync(source, destination, value));
    }

    public async IAsyncEnumerable<ValkeyValue> SetScanAsync(ValkeyKey key, ValkeyValue pattern = default, int pageSize = 250, long cursor = 0, int pageOffset = 0, CommandFlags flags = CommandFlags.None)
    {
        Utils.Requires<NotImplementedException>(flags == CommandFlags.None, "Command flags are not supported by GLIDE");

        long currentCursor = cursor;
        int currentOffset = pageOffset;

        do
        {
            (long nextCursor, ValkeyValue[] elements) = await Command(Request.SetScanAsync(key, currentCursor, pattern, pageSize));

            IEnumerable<ValkeyValue> elementsToYield = currentOffset > 0 ? elements.Skip(currentOffset) : elements;

            foreach (ValkeyValue element in elementsToYield)
            {
                yield return element;
            }

            currentCursor = nextCursor;
        } while (currentCursor != 0);
    }
}
