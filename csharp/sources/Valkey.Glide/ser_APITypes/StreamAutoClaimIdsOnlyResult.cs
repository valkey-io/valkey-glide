using System;

namespace Valkey.Glide;

/// <summary>
/// Result of the <see href="https://redis.io/commands/xautoclaim/">XAUTOCLAIM</see> command with the <c>JUSTID</c> option.
/// </summary>
public readonly struct StreamAutoClaimIdsOnlyResult
{
    internal StreamAutoClaimIdsOnlyResult(ValkeyValue nextStartId, ValkeyValue[] claimedIds, ValkeyValue[] deletedIds)
    {
        NextStartId = nextStartId;
        ClaimedIds = claimedIds;
        DeletedIds = deletedIds;
    }

    /// <summary>
    /// A null <see cref="StreamAutoClaimIdsOnlyResult"/>, indicating no results.
    /// </summary>
    public static StreamAutoClaimIdsOnlyResult Null { get; } = new StreamAutoClaimIdsOnlyResult(ValkeyValue.Null, Array.Empty<ValkeyValue>(), Array.Empty<ValkeyValue>());

    /// <summary>
    /// Whether this object is null/empty.
    /// </summary>
    public bool IsNull => NextStartId.IsNull && ClaimedIds == Array.Empty<ValkeyValue>() && DeletedIds == Array.Empty<ValkeyValue>();

    /// <summary>
    /// The stream ID to be used in the next call to StreamAutoClaim.
    /// </summary>
    public ValkeyValue NextStartId { get; }

    /// <summary>
    /// Array of IDs claimed by the command.
    /// </summary>
    public ValkeyValue[] ClaimedIds { get; }

    /// <summary>
    /// Array of message IDs deleted from the stream.
    /// </summary>
    public ValkeyValue[] DeletedIds { get; }
}
