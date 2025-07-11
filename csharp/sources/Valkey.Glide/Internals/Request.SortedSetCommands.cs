// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Commands.Constants;

using static Valkey.Glide.Internals.FFI;

namespace Valkey.Glide.Internals;

internal partial class Request
{
    public static Cmd<long, bool> SortedSetAddAsync(ValkeyKey key, ValkeyValue member, double score)
    {
        GlideString[] args = [key.ToGlideString(), score.ToGlideString(), member.ToGlideString()];
        return new(RequestType.ZAdd, args, false, response => response == 1);
    }

    public static Cmd<long, long> SortedSetAddAsync(ValkeyKey key, SortedSetEntry[] entries)
    {
        List<GlideString> args = [key.ToGlideString()];
        foreach (var entry in entries)
        {
            args.Add(entry.Score.ToGlideString());
            args.Add(entry.Element.ToGlideString());
        }
        return Simple<long>(RequestType.ZAdd, [.. args]);
    }
}
