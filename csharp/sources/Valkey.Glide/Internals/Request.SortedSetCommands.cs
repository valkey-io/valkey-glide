// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Commands;
using static Valkey.Glide.Internals.FFI;

namespace Valkey.Glide.Internals;

internal partial class Request
{
    /// <summary>
    /// Creates a command for adding a single member to a sorted set.
    /// </summary>
    /// <param name="key">The key of the sorted set.</param>
    /// <param name="member">The member to add.</param>
    /// <param name="score">The score for the member.</param>
    /// <returns>A command that returns true if the member was added, false if it was updated.</returns>
    public static Cmd<long, bool> SortedSetAddAsync(ValkeyKey key, ValkeyValue member, double score)
        => SortedSetAddAsync(key, member, score, SortedSetWhen.Always);

    /// <summary>
    /// Creates a command for adding a single member to a sorted set with conditions.
    /// </summary>
    /// <param name="key">The key of the sorted set.</param>
    /// <param name="member">The member to add.</param>
    /// <param name="score">The score for the member.</param>
    /// <param name="when">The condition for adding the member.</param>
    /// <returns>A command that returns true if the member was added, false if it was updated.</returns>
    public static Cmd<long, bool> SortedSetAddAsync(ValkeyKey key, ValkeyValue member, double score, SortedSetWhen when)
    {
        List<GlideString> args = [key.ToGlideString()];
        
        // Add condition arguments
        var whenArgs = when.ToArgs();
        foreach (var arg in whenArgs)
        {
            args.Add(arg.ToGlideString());
        }
        
        // Add score and member
        args.Add(score.ToGlideString());
        args.Add(member.ToGlideString());
        
        return new(RequestType.ZAdd, [.. args], false, response => response == 1);
    }

    /// <summary>
    /// Creates a command for adding multiple members to a sorted set.
    /// </summary>
    /// <param name="key">The key of the sorted set.</param>
    /// <param name="values">The entries (member-score pairs) to add.</param>
    /// <returns>A command that returns the number of members added.</returns>
    public static Cmd<long, long> SortedSetAddAsync(ValkeyKey key, SortedSetEntry[] values)
        => SortedSetAddAsync(key, values, SortedSetWhen.Always);

    /// <summary>
    /// Creates a command for adding multiple members to a sorted set with conditions.
    /// </summary>
    /// <param name="key">The key of the sorted set.</param>
    /// <param name="values">The entries (member-score pairs) to add.</param>
    /// <param name="when">The condition for adding the members.</param>
    /// <returns>A command that returns the number of members added.</returns>
    public static Cmd<long, long> SortedSetAddAsync(ValkeyKey key, SortedSetEntry[] values, SortedSetWhen when)
    {
        List<GlideString> args = [key.ToGlideString()];
        
        // Add condition arguments
        var whenArgs = when.ToArgs();
        foreach (var arg in whenArgs)
        {
            args.Add(arg.ToGlideString());
        }
        
        // Add score-member pairs
        foreach (var entry in values)
        {
            args.Add(entry.Score.ToGlideString());
            args.Add(entry.Element.ToGlideString());
        }
        
        return Simple<long>(RequestType.ZAdd, [.. args]);
    }
}
