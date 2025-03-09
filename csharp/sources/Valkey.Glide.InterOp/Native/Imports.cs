using System.ComponentModel;
using System.Diagnostics.CodeAnalysis;
using System.Runtime.InteropServices;

namespace Valkey.Glide.InterOp.Native;

[EditorBrowsable(EditorBrowsableState.Advanced)]
[SuppressMessage("ReSharper", "InconsistentNaming")]
[SuppressMessage("ReSharper", "BuiltInTypeReferenceStyle")]
public static class Imports
{
    /// <summary>
    /// Special method to free the returned <see cref="Value"/>s.
    /// MUST be used!
    /// </summary>
    /// <remarks>
    /// Only ever call this on the root-Value instance!
    /// </remarks>
    [DllImport(
        "glide_rs",
        EntryPoint = "csharp_free_value",
        CharSet = CharSet.Unicode,
        CallingConvention = CallingConvention.Cdecl,
        SetLastError = false
    )]
    public static extern void free_value([In] Value value);

    /// <summary>
    /// Special method to free the returned strings.
    /// MUST be used instead of calling c-free!
    /// </summary>
    /// <remarks>
    /// If you got a string via <see cref="Value"/>, free the root-Value instance instead!
    /// </remarks>
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
    /// <param name="in_connection_request"></param>
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
    public static extern CreateClientHandleResult create_client_handle(
        [In] ConnectionRequest in_connection_request
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
        CallingConvention = CallingConvention.Cdecl,
        SetLastError = false
    )]
    public static extern void free_client_handle([In] nint handle);

    /// <summary>
    /// Creates a new client to the given address.
    /// </summary>
    /// <param name="in_client_ptr">
    /// The client handle created by <see cref="create_client_handle"/>.
    /// </param>
    /// <param name="in_callback">
    /// A callback method with the signature:
    /// <c>void Callback(void * in_data, int out_success, const Value value)</c>.
    /// The first arg contains the data of the parameter <paramref name="in_callback_data"/>;
    /// the second arg indicates whether the third parameter contains the error or result;
    /// the third arg contains either the result and MUST be freed by the callback.
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
        CallingConvention = CallingConvention.Cdecl,
        SetLastError = false
    )]
    public static extern unsafe CommandResult command(
        [In] nint in_client_ptr,
        [In] nint in_callback,
        [In] nint in_callback_data,
        [In] ERequestType in_request_type,
        [In] byte** in_args,
        [In] int in_args_count
    );

    /// <summary>
    /// Creates a new client to the given address.
    /// </summary>
    /// <param name="in_client_ptr">
    /// The client handle created by <see cref="create_client_handle"/>.
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
        EntryPoint = "csharp_command_blocking",
        CallingConvention = CallingConvention.Cdecl,
        SetLastError = false
    )]
    public static extern unsafe BlockingCommandResult command_blocking(
        [In] nint in_client_ptr,
        [In] ERequestType in_request_type,
        [In] byte** in_args,
        [In] int in_args_count
    );
}
