// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System.Diagnostics;

using Valkey.Glide.Commands.Options;

using static Valkey.Glide.Internals.FFI;

namespace Valkey.Glide.Internals;

internal partial class Request
{
    public static Cmd<GlideString, string> Info(InfoOptions.Section[] sections)
        => new(RequestType.Info, sections.ToGlideStrings(), false, gs => gs.ToString());

    public static Cmd<GlideString, TimeSpan> Ping(CommandFlags flags = CommandFlags.None)
    {
        Utils.Requires<NotImplementedException>(flags == CommandFlags.None, "Command flags are not supported by GLIDE");
        Stopwatch stopwatch = Stopwatch.StartNew();
        return new(RequestType.Ping, [], false, _ =>
        {
            stopwatch.Stop();
            return stopwatch.Elapsed;
        });
    }

    public static Cmd<GlideString, TimeSpan> Ping(ValkeyValue message, CommandFlags flags = CommandFlags.None)
    {
        Utils.Requires<NotImplementedException>(flags == CommandFlags.None, "Command flags are not supported by GLIDE");
        Stopwatch stopwatch = Stopwatch.StartNew();
        GlideString[] args = [message.ToGlideString()];
        return new(RequestType.Ping, args, false, _ =>
        {
            stopwatch.Stop();
            return stopwatch.Elapsed;
        });
    }

    public static Cmd<GlideString, ValkeyValue> Echo(ValkeyValue message, CommandFlags flags = CommandFlags.None)
    {
        Utils.Requires<NotImplementedException>(flags == CommandFlags.None, "Command flags are not supported by GLIDE");
        GlideString[] args = [message.ToGlideString()];
        return new(RequestType.Echo, args, false, gs => (ValkeyValue)gs);
    }
}
