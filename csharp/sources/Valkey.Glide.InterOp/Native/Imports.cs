using System.Diagnostics.CodeAnalysis;
using System.Runtime.InteropServices;

namespace Valkey.Glide.InterOp.Native;

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
    public static extern unsafe void free_value([In] Value value);

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

[SuppressMessage("ReSharper", "InconsistentNaming")]
[StructLayout(LayoutKind.Explicit, CharSet = CharSet.Unicode)]
public struct ValueUnion
{
    [FieldOffset(0)]
    public long i;

    [FieldOffset(0)]
    public bool f;

    [FieldOffset(0)]
    public unsafe byte* ptr;
}

public enum EValueKind
{
    /// <summary>
    /// A nil response from the server.
    /// </summary>
    /// <remarks>
    /// Union value must be ignored.
    /// </remarks>
    Nil,


    /// <summary>
    /// An integer response.  Note that there are a few situations
    /// in which redis actually returns a string for an integer which
    /// is why this library generally treats integers and strings
    /// the same for all numeric responses.
    /// </summary>
    ///
    /// <remarks>
    /// Union value will be set as c_long.
    /// It can be safely consumed without freeing.
    /// </remarks>
    Int,


    /// <summary>
    /// An arbitrary binary data, usually represents a binary-safe string.
    /// </summary>
    ///
    /// <remarks>
    /// Union will, in ptr, contain an array of c_char (bytes).
    /// See CommandResult.length for the number of elements.
    /// ValueUnion.ptr MUST be freed.
    /// </remarks>
    BulkString,


    /// <summary>
    /// A response containing an array with more data.
    /// This is generally used by redis to express nested structures.
    /// </summary>
    ///
    /// <remarks>
    /// Union will, in ptr, contain an array of CommandResult's.
    /// See CommandResult.length for the number of elements.
    /// ValueUnion.ptr MUST be freed.
    /// </remarks>
    Array,


    /// <summary>
    /// A simple string response, without line breaks and not binary safe.
    /// </summary>
    ///
    /// <remarks>
    /// Union will, in ptr, contain a c_str.
    /// See CommandResult.length for the length of the string, excluding the zero byte.
    /// ValueUnion.ptr MUST be freed.
    /// </remarks>
    SimpleString,


    /// <summary>
    /// A status response which represents the string "OK".
    /// </summary>
    ///
    /// <remarks>
    /// Union value must be ignored.
    /// </remarks>
    Okay,


    /// <summary>
    /// Unordered key,value list from the server. Use `as_map_iter` function.
    /// </summary>
    ///
    /// <remarks>
    /// Union will, in ptr, contain an array of CommandResult's which are supposed to be interpreted as key-value pairs.
    /// See CommandResult.length for the number of pairs (aka: elements * 2).
    /// ValueUnion.ptr MUST be freed.
    /// </remarks>
    Map,

    /// Placeholder
    /// ToDo: Figure out a way to map this to C-Memory
    Attribute,


    /// <summary>
    /// Unordered set value from the server.
    /// </summary>
    ///
    /// <remarks>
    /// Union will, in ptr, contain an array of CommandResult's.
    /// See CommandResult.length for the number of elements.
    /// ValueUnion.ptr MUST be freed.
    /// </remarks>
    Set,


    /// <summary>
    /// A floating number response from the server.
    /// </summary>
    ///
    /// <remarks>
    /// Union value will be set as c_double.
    /// It can be safely consumed without freeing.
    /// </remarks>
    Double,


    /// <summary>
    /// A boolean response from the server.
    /// </summary>
    ///
    /// <remarks>
    /// Union value will be set as c_long.
    /// It can be safely consumed without freeing.
    /// </remarks>
    Boolean,


    /// <summary>
    /// First String is format and other is the string
    /// </summary>
    ///
    /// <remarks>
    /// Union will, in ptr, contain an array of CommandResult's.
    /// See CommandResult.length for the number of elements.
    /// ValueUnion.ptr MUST be freed.
    /// </remarks>
    VerbatimString,


    /// <summary>
    /// </summary>
    /// Very large number that out of the range of the signed 64 bit numbers
    ///
    /// <remarks>
    /// Union will, in ptr, contain a StringPair
    /// ValueUnion.ptr MUST be freed.
    /// </remarks>
    BigNumber,


    /// <summary>
    /// Push data from the server.
    /// First result will be push-kind
    /// Second will be array of results
    /// </summary>
    ///
    /// <remarks>
    /// Union will, in ptr, contain an array of CommandResult's.
    /// See CommandResult.length for the number of elements.
    /// ValueUnion.ptr MUST be freed.
    /// </remarks>
    Push,
}

[SuppressMessage("ReSharper", "InconsistentNaming")]
[StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
public struct Value
{
    public EValueKind kind;
    public ValueUnion data;
    public long       length;
}

[SuppressMessage("ReSharper", "InconsistentNaming")]
[StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
public unsafe struct StringPair
{
    public byte* a_start;
    public byte* a_end;
    public byte* b_start;
    public byte* b_end;
}
