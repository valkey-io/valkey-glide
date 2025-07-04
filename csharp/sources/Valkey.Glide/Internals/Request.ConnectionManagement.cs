// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System.Diagnostics;
using static Valkey.Glide.Internals.FFI;

namespace Valkey.Glide.Internals;

internal partial class Request
{
    public static Cmd<GlideString, TimeSpan> PingAsync(CommandFlags ignored = CommandFlags.None)
    {
        if (ignored != CommandFlags.None)
        {
            throw new NotImplementedException("Command flags are not supported by GLIDE");
        }
        var stopwatch = Stopwatch.StartNew();
        return new(RequestType.Ping, [], false, _ =>
        {
            stopwatch.Stop();
            return stopwatch.Elapsed;
        });
    }

    public static Cmd<GlideString, TimeSpan> PingAsync(ValkeyValue message, CommandFlags ignored = CommandFlags.None)
    {
        if (ignored != CommandFlags.None)
        {
            throw new NotImplementedException("Command flags are not supported by GLIDE");
        }
        var stopwatch = Stopwatch.StartNew();
        GlideString[] args = [message.ToGlideString()];
        return new(RequestType.Ping, args, false, _ =>
        {
            stopwatch.Stop();
            return stopwatch.Elapsed;
        });
    }

    public static Cmd<GlideString, GlideString> EchoAsync(ValkeyValue message, CommandFlags ignored = CommandFlags.None)
    {
        if (ignored != CommandFlags.None)
        {
            throw new NotImplementedException("Command flags are not supported by GLIDE");
        }
        GlideString[] args = [message.ToGlideString()];
        return Simple<GlideString>(RequestType.Echo, args);
    }
}
