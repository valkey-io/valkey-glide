using System;

namespace Valkey.Glide;

/// <summary>
/// Specifies what side of the list to refer to.
/// </summary>
public enum ListSide
{
    /// <summary>
    /// The head of the list.
    /// </summary>
    Left,

    /// <summary>
    /// The tail of the list.
    /// </summary>
    Right,
}

internal static class ListSideExtensions
{
    internal static ValkeyValue ToLiteral(this ListSide side) => side switch
    {
        ListSide.Left => ValkeyLiterals.LEFT,
        ListSide.Right => ValkeyLiterals.RIGHT,
        _ => throw new ArgumentOutOfRangeException(nameof(side)),
    };
}
