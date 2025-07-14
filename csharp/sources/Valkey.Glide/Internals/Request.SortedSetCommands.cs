// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Commands.Constants;

using static Valkey.Glide.Internals.FFI;

namespace Valkey.Glide.Internals;

internal partial class Request
{
    public static Cmd<long, bool> SortedSetAddAsync(ValkeyKey key, ValkeyValue member, double score, SortedSetWhen when = SortedSetWhen.Always, CommandFlags flags = CommandFlags.None)
    {
        if (flags != CommandFlags.None)
        {
            throw new NotImplementedException("Command flags are not supported by GLIDE");
        }

        List<GlideString> args = [key.ToGlideString()];
        
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

        // Add score-member pairs
        foreach (var entry in values)
        {
            args.Add(entry.Score.ToGlideString());
            args.Add(entry.Element.ToGlideString());
        }

        return Simple<long>(RequestType.ZAdd, [.. args]);
    }

    public static Cmd<double?, double?> SortedSetIncrementAsync(ValkeyKey key, ValkeyValue member, double increment, SortedSetWhen when = SortedSetWhen.Always, CommandFlags flags = CommandFlags.None)
    {
        if (flags != CommandFlags.None)
        {
            throw new NotImplementedException("Command flags are not supported by GLIDE");
        }

        List<GlideString> args = [key.ToGlideString()];
        
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

        // Add INCR flag
        args.Add("INCR");
        
        // Add increment and member
        args.Add(increment.ToGlideString());
        args.Add(member.ToGlideString());

        return Simple<double?>(RequestType.ZAdd, [.. args], true);
    }
}
