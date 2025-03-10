using System.Diagnostics;
using Valkey.Glide.InterOp.Exceptions;
using Valkey.Glide.InterOp.Native;

namespace Valkey.Glide.Commands;

/// <summary>
///
/// </summary>
public static class SetCommands
{
    /*
     * SET key value [ NX | XX | IFEQ comparison-value ] [ GET ] [ EX seconds | PX milliseconds | EXAT unix-time-seconds | PXAT unix-time-milliseconds | KEEPTTL ]
     * RESP2 Reply
     *      If GET not given, any of the following:
     *          Nil reply: Operation was aborted (conflict with one of the XX/NX options).
     *          Simple string reply: OK: The key was set.
     *      If GET given, any of the following:
     *          Nil reply: The key didn't exist before the SET.
     *          Bulk string reply: The previous value of the key.
     *      Note that when using GET together with XX/NX/IFEQ, the reply indirectly indicates whether the key was set:
     *          GET and XX given: Non-Nil reply indicates the key was set.
     *          GET and NX given: Nil reply indicates the key was set.
     *          GET and IFEQ given: The key was set if the reply is equal to comparison-value.
     * RESP3 Reply
     *      If GET not given, any of the following:
     *          Null reply: Operation was aborted (conflict with one of the XX/NX options).
     *          Simple string reply: OK: The key was set.
     *      If GET given, any of the following:
     *          Null reply: The key didn't exist before the SET.
     *          Bulk string reply: The previous value of the key.
     *      Note that when using GET together with XX/NX/IFEQ, the reply indirectly indicates whether the key was set:
     *          GET and XX given: Non-Null reply indicates the key was set.
     *          GET and NX given: Null reply indicates the key was set.
     *          GET and IFEQ given: The key was set if the reply is equal to comparison-value.
     */

    /// <seealso href="https://valkey.io/commands/set/"/>
    /// <exception cref="GlideException">The set operation was not successful</exception>
    public static async Task SetAsync(this IGlideClient client, string key, string value)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(key);

        var result = await client.CommandAsync(ERequestType.Set, key.AsRedisCommandText(), value.AsRedisString());
        if (result.IsOk())
            return;

        if (result.IsNone())
            throw new GlideException("Set failed");
    }
}
