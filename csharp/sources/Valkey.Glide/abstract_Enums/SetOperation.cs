using System;

namespace Valkey.Glide;

/// <summary>
/// Describes an algebraic set operation that can be performed to combine multiple sets.
/// </summary>
public enum SetOperation
{
    /// <summary>
    /// Returns the members of the set resulting from the union of all the given sets.
    /// </summary>
    Union,

    /// <summary>
    /// Returns the members of the set resulting from the intersection of all the given sets.
    /// </summary>
    Intersect,

    /// <summary>
    /// Returns the members of the set resulting from the difference between the first set and all the successive sets.
    /// </summary>
    Difference,
}

internal static class SetOperationExtensions
{
    internal static ValkeyCommand ToCommand(this SetOperation operation, bool store) => operation switch
    {
        SetOperation.Intersect when store => ValkeyCommand.ZINTERSTORE,
        SetOperation.Intersect => ValkeyCommand.ZINTER,
        SetOperation.Union when store => ValkeyCommand.ZUNIONSTORE,
        SetOperation.Union => ValkeyCommand.ZUNION,
        SetOperation.Difference when store => ValkeyCommand.ZDIFFSTORE,
        SetOperation.Difference => ValkeyCommand.ZDIFF,
        _ => throw new ArgumentOutOfRangeException(nameof(operation)),
    };
}
