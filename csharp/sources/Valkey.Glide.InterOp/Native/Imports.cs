// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System.Runtime.InteropServices;

namespace Valkey.Glide.InterOp.Native;

internal sealed class Imports
{

    #region Client

    internal  delegate void SuccessAction(ulong index, IntPtr ptr);

    internal  delegate void FailureAction(ulong index, IntPtr strPtr, RequestErrorType err);

    [DllImport("libglide_rs", CallingConvention = CallingConvention.Cdecl, EntryPoint = "command")]
    internal  static extern void CommandFfi(IntPtr client, ulong index, IntPtr cmdInfo, IntPtr routeInfo);

    [DllImport("libglide_rs", CallingConvention = CallingConvention.Cdecl, EntryPoint = "free_respose")]
    internal  static extern void FreeResponse(IntPtr response);

    [DllImport("libglide_rs", CallingConvention = CallingConvention.Cdecl, EntryPoint = "create_client")]
    internal  static extern void CreateClientFfi(IntPtr config, IntPtr successCallback, IntPtr failureCallback);

    [DllImport("libglide_rs", CallingConvention = CallingConvention.Cdecl, EntryPoint = "close_client")]
    internal  static extern void CloseClientFfi(IntPtr client);

    #endregion


    #region Logger

    [DllImport("libglide_rs", CallingConvention = CallingConvention.Cdecl, EntryPoint = "log")]
    internal  static extern void log(int logLevel, byte[] logIdentifier, byte[] message);

    [DllImport("libglide_rs", CallingConvention = CallingConvention.Cdecl, EntryPoint = "init")]
    internal static extern Level InitInternalLogger(int level, byte[]? filename);

    #endregion
}
