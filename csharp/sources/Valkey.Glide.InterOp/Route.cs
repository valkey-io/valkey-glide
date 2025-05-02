// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Valkey.Glide.InterOp;

/// <summary>
/// Basic class. Please use one of the following implementations:
/// <list type="bullet">
/// <item><see cref="RandomRoute"/></item>
/// <item><see cref="AllNodesRoute"/></item>
/// <item><see cref="AllPrimariesRoute"/></item>
/// <item><see cref="SlotIdRoute"/></item>
/// <item><see cref="SlotKeyRoute"/></item>
/// <item><see cref="ByAddressRoute"/></item>
/// </list>
/// </summary>
public abstract class Route
{
    internal Route()
    {
    }

    /// <inheritdoc cref="RandomRoute"/>
    public static readonly RandomRoute Random = new();

    /// <inheritdoc cref="AllNodesRoute"/>
    public static readonly AllNodesRoute AllNodes = new();

    /// <inheritdoc cref="AllPrimariesRoute"/>
    public static readonly AllPrimariesRoute AllPrimaries = new();

    internal abstract Native.Route ToFfi();
}
