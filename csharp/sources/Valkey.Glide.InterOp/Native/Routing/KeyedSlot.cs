// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System.ComponentModel;
using System.Diagnostics.CodeAnalysis;
using System.Runtime.InteropServices;

namespace Valkey.Glide.InterOp.Native.Routing;

[EditorBrowsable(EditorBrowsableState.Advanced)]
[SuppressMessage("ReSharper", "InconsistentNaming")]
[StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
public unsafe struct KeyedSlot
{
    /// DNS hostname of the node
    public byte* slot;
    
    /// Length of slot
    public uint slot_length;

    /// port of the node
    public RouteSlotAddress slot_addr;
}
