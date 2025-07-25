﻿namespace Valkey.Glide;

/// <summary>
/// The intrinsic data-types supported by server.
/// </summary>
/// <remarks><seealso href="https://valkey.io/topics/data-types"/></remarks>
public enum ValkeyType
{
    /// <summary>
    /// The specified key does not exist.
    /// </summary>
    None,

    /// <summary>
    /// Strings are the most basic kind of Valkey value. Valkey Strings are binary safe, this means that
    /// a Valkey string can contain any kind of data, for instance a JPEG image or a serialized Ruby object.
    /// A String value can be at max 512 Megabytes in length.
    /// </summary>
    /// <remarks><seealso href="https://valkey.io/commands#string"/></remarks>
    String,

    /// <summary>
    /// Valkey Lists are simply lists of strings, sorted by insertion order.
    /// It is possible to add elements to a Valkey List pushing new elements on the head (on the left) or
    /// on the tail (on the right) of the list.
    /// </summary>
    /// <remarks><seealso href="https://valkey.io/commands#list"/></remarks>
    List,

    /// <summary>
    /// Valkey Sets are an unordered collection of Strings. It is possible to add, remove, and test for
    /// existence of members in O(1) (constant time regardless of the number of elements contained inside the Set).
    /// Valkey Sets have the desirable property of not allowing repeated members.
    /// Adding the same element multiple times will result in a set having a single copy of this element.
    /// Practically speaking this means that adding a member does not require a check if exists then add operation.
    /// </summary>
    /// <remarks><seealso href="https://valkey.io/commands#set"/></remarks>
    Set,

    /// <summary>
    /// Valkey Sorted Sets are, similarly to Valkey Sets, non repeating collections of Strings.
    /// The difference is that every member of a Sorted Set is associated with score, that is used
    /// in order to take the sorted set ordered, from the smallest to the greatest score.
    /// While members are unique, scores may be repeated.
    /// </summary>
    /// <remarks><seealso href="https://valkey.io/commands#sorted-set"/></remarks>
    SortedSet,

    /// <summary>
    /// Valkey Hashes are maps between string fields and string values, so they are the perfect data type
    /// to represent objects (e.g. A User with a number of fields like name, surname, age, and so forth).
    /// </summary>
    /// <remarks><seealso href="https://valkey.io/commands#hash"/></remarks>
    Hash,

    /// <summary>
    /// A Valkey Stream is a data structure which models the behavior of an append only log but it has more
    /// advanced features for manipulating the data contained within the stream. Each entry in a
    /// stream contains a unique message ID and a list of name/value pairs containing the entry's data.
    /// </summary>
    /// <remarks><seealso href="https://valkey.io/commands#stream"/></remarks>
    Stream,

    /// <summary>
    /// The data-type was not recognised by the client library.
    /// </summary>
    Unknown,
}
