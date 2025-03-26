// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System.ComponentModel;
using System.Diagnostics.CodeAnalysis;
using System.Runtime.InteropServices;

namespace Valkey.Glide.InterOp.Native.Logging;

[EditorBrowsable(EditorBrowsableState.Advanced)]
[SuppressMessage("ReSharper", "InconsistentNaming")]
[StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
public unsafe struct KeyValuePair
{
    public char* key;
    public int key_length;
    public char* value;
    public int value_length;
}
