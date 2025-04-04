using System.ComponentModel;
using System.Diagnostics.CodeAnalysis;
using System.Runtime.InteropServices;

namespace Valkey.Glide.InterOp.Native.Routing;

[EditorBrowsable(EditorBrowsableState.Advanced)]
[SuppressMessage("ReSharper", "InconsistentNaming")]
[StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
public unsafe struct RoutingInfoMultiSlot
{
    public ERoutingInfoMultiResponsePolicy response_policy;
    public ERoutingInfoMultiSlotArgPattern arg_pattern;
    public RoutingInfoMultiSlotPair* routes;
    public uint routes_length;
}