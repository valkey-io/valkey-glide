// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using static Valkey.Glide.Internals.FFI;

namespace Valkey.Glide.Internals;

internal partial class Request
{
    private static void AddSortedSetWhenOptions(List<GlideString> args, SortedSetWhen when)
    {
        // Add conditional options
        if (when.HasFlag(SortedSetWhen.Exists))
        {
            args.Add("XX");
        }
        else if (when.HasFlag(SortedSetWhen.NotExists))
        {
            args.Add("NX");
        }

        if (when.HasFlag(SortedSetWhen.GreaterThan))
        {
            args.Add("GT");
        }
        else if (when.HasFlag(SortedSetWhen.LessThan))
        {
            args.Add("LT");
        }
    }

    public static Cmd<long, bool> SortedSetAddAsync(ValkeyKey key, ValkeyValue member, double score, SortedSetWhen when = SortedSetWhen.Always, CommandFlags flags = CommandFlags.None)
    {
        if (flags != CommandFlags.None)
        {
            throw new NotImplementedException("Command flags are not supported by GLIDE");
        }

        List<GlideString> args = [key.ToGlideString()];
        AddSortedSetWhenOptions(args, when);

        // Add score and member
        args.Add(score.ToGlideString());
        args.Add(member.ToGlideString());

        return new(RequestType.ZAdd, [.. args], false, response => response == 1);
    }

    public static Cmd<long, long> SortedSetAddAsync(ValkeyKey key, SortedSetEntry[] values, SortedSetWhen when = SortedSetWhen.Always, CommandFlags flags = CommandFlags.None)
    {
        if (flags != CommandFlags.None)
        {
            throw new NotImplementedException("Command flags are not supported by GLIDE");
        }

        List<GlideString> args = [key.ToGlideString()];
        AddSortedSetWhenOptions(args, when);

        // Add score-member pairs
        foreach (SortedSetEntry entry in values)
        {
            args.Add(entry.Score.ToGlideString());
            args.Add(entry.Element.ToGlideString());
        }

        return Simple<long>(RequestType.ZAdd, [.. args]);
    }
}
