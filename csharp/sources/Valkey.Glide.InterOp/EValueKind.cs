namespace Valkey.Glide.InterOp;

/// <summary>
/// Defines various types or categories of values that can be represented
/// within the <see cref="Value"/> struct. Each enumeration member specifies
/// a distinct kind of data, enabling the interpretation and handling of the
/// contained value accordingly. This enumeration helps differentiate between
/// primitive data types, complex types, and placeholders.
/// </summary>
/// <seealso cref="Value"/>
public enum EValueKind
{
    /// <summary>
    /// Represents the absence of any specific value or type within the enumeration.
    /// This is the default state for the <see cref="EValueKind"/> enumeration
    /// and is typically used when no valid or meaningful data is assigned.
    /// When this enumeration member is set, all associated properties in the
    /// <see cref="Value"/> struct are expected to be null or uninitialized.
    /// This value can be used as a placeholder or to indicate that no valid
    /// operation or data is present.
    /// </summary>
    /// <remarks>
    /// This is different from <see cref="Okay"/> by being the "Empty" response type.
    /// </remarks>
    /// <seealso cref="Value"/>
    None,

    /// <summary>
    /// Represents the classification of data types that can be used within the application.
    /// This enumeration provides a means to categorize values, ensuring proper identification
    /// and processing based on their respective kinds.
    /// </summary>
    /// <remarks>
    /// This is different from <see cref="None"/> by being the "Okay" but not Emtpy type.
    /// </remarks>
    /// <seealso cref="Value"/>
    Okay,

    /// <summary>
    /// Represents a string value within the enumeration.
    /// This member is used when the associated <see cref="Value"/> struct
    /// contains a plain text value.
    /// The <see cref="Value.Data"/> property is expected to hold
    /// the <see cref="string"/> content when this member is set.
    /// This value is commonly used for individual string data or textual
    /// representations.
    /// </summary>
    /// <seealso cref="Value"/>
    /// <seealso cref="Value.Data"/>
    String,

    /// <summary>
    /// Represents a formatted string type within the enumeration.
    /// This value denotes that the associated data is expected to be a string
    /// containing format specifiers often used in templating or generating
    /// output dynamically.
    /// When this enumeration member is set, the associated <see cref="Value.Format"/> property
    /// in the <see cref="Value"/> struct contains the format specifier <see cref="string"/>, and the
    /// <see cref="Value.Data"/> property may contain the raw <see cref="string"/> data.
    /// This type is useful for scenarios where the string representation
    /// of the value is dynamic or depends on external inputs, such as when
    /// format strings are used to generate user-facing output.
    /// </summary>
    /// <seealso cref="Value"/>
    /// <seealso cref="Value.Data"/>
    /// <seealso cref="Value.Format"/>
    FormatString,

    /// <summary>
    /// Represents a boolean value within the <see cref="EValueKind"/> enumeration.
    /// This member is used to indicate that the associated <see cref="Value.Flag"/> in the
    /// <see cref="Value"/> struct represents a true or false value.
    /// When this enumeration member is set, corresponding properties in the
    /// <see cref="Value"/> struct should reflect a valid boolean state.
    /// This value is suitable for logical operations or binary decision contexts.
    /// </summary>
    /// <seealso cref="Value"/>
    /// <seealso cref="Value.Flag"/>
    Boolean,

    /// <summary>
    /// Represents a collection of key-value pairs within the enumeration, where each key
    /// is associated with a corresponding value. This member is typically used to
    /// encapsulate structured or hierarchical data in the form of mappings or dictionaries.
    /// When this enumeration member is set, the associated properties in the <see cref="Value"/>
    /// struct are expected to hold data relevant to key-value pair representations.
    /// </summary>
    /// <seealso cref="Value"/>
    /// <seealso cref="Value.Pairs"/>
    KeyValuePairs,

    /// <summary>
    /// Represents a 64-bit signed integer value within the enumeration.
    /// This member is used when the associated <see cref="Value"/>
    /// stores or manipulates long integral data.
    /// Suitable for scenarios requiring large range integer operations or values.
    /// </summary>
    /// <seealso cref="Value"/>
    /// <seealso cref="Value.Integer"/>
    Integer,

    /// <summary>
    /// Represents a floating-point numeric value. This enumeration member
    /// is used to denote values that are expressed as floating-point numbers,
    /// allowing representation of non-integer numbers with decimal points
    /// or scientific notation.
    /// </summary>
    /// <seealso cref="Value"/>
    /// <seealso cref="Value.FloatingPoint"/>
    FloatingPoint,

    /// <summary>
    /// Represents a collection of values within the <see cref="EValueKind"/> enumeration.
    /// This value is used to indicate that the associated data is structured as an
    /// array or sequence of elements, where each element may hold its own distinct type or value.
    /// It is typically used to encapsulate multiple related values in a single entity.
    /// </summary>
    /// <seealso cref="Value"/>
    /// <seealso cref="Value.Array"/>
    Array,
}
