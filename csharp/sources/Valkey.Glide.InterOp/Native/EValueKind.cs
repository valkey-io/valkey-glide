using System.ComponentModel;

namespace Valkey.Glide.InterOp.Native;

[EditorBrowsable(EditorBrowsableState.Advanced)]
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
