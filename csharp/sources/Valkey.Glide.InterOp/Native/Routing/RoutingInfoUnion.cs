using System.ComponentModel;
using System.Diagnostics.CodeAnalysis;
using System.Runtime.InteropServices;

namespace Valkey.Glide.InterOp.Native.Routing;

[EditorBrowsable(EditorBrowsableState.Advanced)]
[SuppressMessage("ReSharper", "InconsistentNaming")]
[StructLayout(LayoutKind.Explicit, CharSet = CharSet.Unicode)]
public struct RoutingInfoUnion
{
    /// Set if ERoutingInfo is ByAddress
    [FieldOffset(0)]
    public RoutingInfoByAddress by_address;

    /// Set if ERoutingInfo is SpecificNode
    [FieldOffset(0)]
    public Route specific_node;

    /// Set if ERoutingInfo is MultiAllNodes or MultiAllMasters
    [FieldOffset(0)]
    public ERoutingInfoMultiResponsePolicy multi;

    /// Set if ERoutingInfo is MultiMultiSlot
    [FieldOffset(0)]
    public RoutingInfoMultiSlot multi_slot;
}