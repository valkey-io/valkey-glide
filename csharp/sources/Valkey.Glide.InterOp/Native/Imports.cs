using System.Diagnostics.CodeAnalysis;
using System.Runtime.InteropServices;

namespace Valkey.Glide.InterOp.Native;

[SuppressMessage("ReSharper", "InconsistentNaming")]
[SuppressMessage("ReSharper", "BuiltInTypeReferenceStyle")]
public static class Imports
{
    /// <summary>
    ///
    /// </summary>
    /// <param name="handle"></param>
    [DllImport(
        "glide_rs",
        EntryPoint = "csharp_free_string",
        CharSet = CharSet.Unicode,
        CallingConvention = CallingConvention.Cdecl,
        SetLastError = false
    )]
    public static extern unsafe void free_string([In] byte* handle);

    /// <summary>
    /// Initializes essential parts of the system.
    /// Supposed to be called once only.
    /// </summary>
    /// <param name="in_minimal_level">The minimum file log level</param>
    /// <param name="in_file_name">The file name to log to</param>
    /// <remarks>
    /// <para>
    /// <b>Input Safety (in_...)</b>
    /// The data passed in is considered "caller responsibility".
    /// Any pointers hence will be left unreleased after leaving.
    /// </para>
    /// <para>
    /// <b>Output Safety (out_... / return ...)</b>
    /// The data returned is considered "caller responsibility".
    /// The caller must release any non-null pointers.
    /// </para>
    /// <para>
    /// <b>Reference Safety (ref_...)</b>
    /// Any reference data is considered "caller owned".
    /// </para>
    /// <para>
    /// <b>Freeing data allocated by the API</b>
    /// To free data returned by the API, use the corresponding <c>free_...</c> methods of the API.
    /// It is <i>not optional</i> to call them to free data allocated by the API!
    /// </para>
    /// </remarks>
    [DllImport(
        "glide_rs",
        EntryPoint = "csharp_system_init",
        CharSet = CharSet.Unicode,
        CallingConvention = CallingConvention.Cdecl,
        SetLastError = false
    )]
    public static extern unsafe InitResult system_init([In] ELoggerLevel in_minimal_level, [In] byte* in_file_name);


    /// <summary>
    /// Creates a new client to the given address.
    /// </summary>
    /// <param name="in_host"></param>
    /// <param name="in_host_count"></param>
    /// <param name="in_use_tls"></param>
    /// <remarks>
    /// <para>
    /// <b>Input Safety (in_...)</b>
    /// The data passed in is considered "caller responsibility".
    /// Any pointers hence will be left unreleased after leaving.
    /// </para>
    /// <para>
    /// <b>Output Safety (out_... / return ...)</b>
    /// The data returned is considered "caller responsibility".
    /// The caller must release any non-null pointers.
    /// </para>
    /// <para>
    /// <b>Reference Safety (ref_...)</b>
    /// Any reference data is considered "caller owned".
    /// </para>
    /// <para>
    /// <b>Freeing data allocated by the API</b>
    /// To free data returned by the API, use the corresponding <c>free_...</c> methods of the API.
    /// It is <i>not optional</i> to call them to free data allocated by the API!
    /// </para>
    /// </remarks>
    [DllImport(
        "glide_rs",
        EntryPoint = "csharp_create_client_handle",
        CallingConvention = CallingConvention.Cdecl,
        SetLastError = false
    )]
    public static extern unsafe CreateClientHandleResult create_client_handle(
        [In] NodeAddress* in_host,
        [In] ushort in_host_count,
        [In] bool in_use_tls
    );


    /// <summary>
    /// Frees the previously created client_handle, making it unusable.
    /// </summary>
    /// <param name="handle">The active handle to free.</param>
    /// <remarks>
    /// <para>
    /// <b>Input Safety (in_...)</b>
    /// The data passed in is considered "caller responsibility".
    /// Any pointers hence will be left unreleased after leaving.
    /// </para>
    /// <para>
    /// <b>Output Safety (out_... / return ...)</b>
    /// The data returned is considered "caller responsibility".
    /// The caller must release any non-null pointers.
    /// </para>
    /// <para>
    /// <b>Reference Safety (ref_...)</b>
    /// Any reference data is considered "caller owned".
    /// </para>
    /// <para>
    /// <b>Freeing data allocated by the API</b>
    /// To free data returned by the API, use the corresponding <c>free_...</c> methods of the API.
    /// It is <i>not optional</i> to call them to free data allocated by the API!
    /// </para>
    /// </remarks>
    [DllImport(
        "glide_rs",
        EntryPoint = "csharp_free_client_handle",
        CharSet = CharSet.Unicode,
        CallingConvention = CallingConvention.Cdecl,
        SetLastError = false
    )]
    public static extern void free_client_handle([In] nint handle);

    /// <summary>
    /// Creates a new client to the given address.
    /// </summary>
    /// <param name="in_client_ptr">
    /// A callback method with the signature:
    /// <c>void Callback(void * in_data, int out_success, const char * ref_output)</c>.
    /// The first arg contains the data of the parameter <paramref name="in_callback_data"/>;
    /// the second arg indicates whether the third parameter contains the error or result;
    /// the third arg contains either the result, the error or null and is freed by the API,
    /// not the calling code.
    /// </param>
    /// <param name="in_callback">
    /// The data to be passed in to <i>in_callback</i>
    /// </param>
    /// <param name="in_callback_data">
    /// The data to be passed in to <paramref name="in_callback"/>.
    /// </param>
    /// <param name="in_request_type">
    /// The type of command to issue.
    /// </param>
    /// <param name="in_args">
    /// A C-String array of arguments to be passed, with the size of <paramref name="in_args_count"/> and zero terminated.
    /// </param>
    /// <param name="in_args_count">
    /// The number of arguments in <paramref name="in_args"/>.
    /// </param>
    /// <remarks>
    /// <para>
    /// <b>Input Safety (in_...)</b>
    /// The data passed in is considered "caller responsibility".
    /// Any pointers hence will be left unreleased after leaving.
    /// </para>
    /// <para>
    /// <b>Output Safety (out_... / return ...)</b>
    /// The data returned is considered "caller responsibility".
    /// The caller must release any non-null pointers.
    /// </para>
    /// <para>
    /// <b>Reference Safety (ref_...)</b>
    /// Any reference data is considered "caller owned".
    /// </para>
    /// <para>
    /// <b>Freeing data allocated by the API</b>
    /// To free data returned by the API, use the corresponding <c>free_...</c> methods of the API.
    /// It is <i>not optional</i> to call them to free data allocated by the API!
    /// </para>
    /// </remarks>
    [DllImport(
        "glide_rs",
        EntryPoint = "csharp_command",
        CharSet = CharSet.Unicode,
        CallingConvention = CallingConvention.Cdecl,
        SetLastError = false
    )]
    public static extern unsafe CommandResult command(
        [In] nint in_client_ptr,
        [In] delegate*<nint, int, byte*, void> in_callback,
        [In] nint in_callback_data,
        [In] ERequestType in_request_type,
        [In] byte** in_args,
        [In] int in_args_count
    );
}
