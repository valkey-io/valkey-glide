using System.ComponentModel;
using System.Globalization;
using Valkey.Glide.InterOp.Native;
using Valkey.Glide.Properties;

namespace Valkey.Glide.Commands;

/// <summary>
/// Hosts the different <c>set</c> command in an implementation-friendly manner
/// </summary>
/// <remarks>
/// We do this to ease the implementation of <see cref="IGlideClient"/>
/// </remarks>
public static class SetCommand
{
    /// <summary>
    /// Set <paramref name="key"/> to hold the <paramref name="value"/>.
    /// </summary>
    /// <remarks>
    /// If <paramref name="key"/> already holds a value, it is overwritten, regardless of its type.
    /// Any previous time to live associated with the <paramref name="key"/> is discarded on successful <c>SET</c> operation.
    /// </remarks>
    /// <param name="client">The Glide client instance used to send the command.</param>
    /// <param name="key">The key to set in the Glide client.</param>
    /// <param name="value">The value to associate with the specified <paramref name="key"/>.</param>
    /// <returns>A <see cref="Task"/> representing the asynchronous operation.</returns>
    /// <exception cref="OperationFailedException">
    /// Thrown when the command execution does not return a successful result.
    /// </exception>
    /// <seealso href="https://valkey.io/commands/set/"/>
    /// <seealso cref="SetAsync(Valkey.Glide.IGlideClient,string,string)"/>
    /// <seealso cref="SetIfNotExistsAsync(Valkey.Glide.IGlideClient,string,string)"/>
    /// <seealso cref="SetGetAsync(Valkey.Glide.IGlideClient,string,string)"/>
    public static async Task SetAsync(this IGlideClient client, string key, string value)
    {
        var result = await client.CommandAsync(ERequestType.Set, key, value);
        if (result != "OK")
            throw new OperationFailedException(Language.Set_CommandExecutionWasNotSuccessfull);
    }

    /// <summary>
    /// Set <paramref name="key"/> to hold the <paramref name="value"/>.
    /// </summary>
    /// <remarks>
    /// Won't do anything if the key already exists.
    /// Make sure to check the return value.
    /// </remarks>
    /// <param name="client">The Glide client instance used to send the command.</param>
    /// <param name="key">The key to set in the Glide client.</param>
    /// <param name="value">The value to associate with the specified <paramref name="key"/>.</param>
    /// <returns>A <see cref="Task"/> representing the asynchronous operation.</returns>
    /// <exception cref="OperationFailedException">
    /// Thrown when the command execution does not return a successful result.
    /// </exception>
    /// <seealso href="https://valkey.io/commands/set/"/>
    public static async Task<bool> SetIfNotExistsAsync(this IGlideClient client, string key, string value)
    {
        var result = await client.CommandAsync(ERequestType.Set, key, value, "NX");
        return result switch
        {
            "OK"   => true,
            "NIL"  => false,
            "NULL" => false,
            _      => throw new OperationFailedException(Language.Set_CommandExecutionWasNotSuccessfull),
        };
    }

    /// <summary>
    /// Change the value of <paramref name="key"/> to hold the <paramref name="value"/>.
    /// </summary>
    /// <remarks>
    /// Won't do anything if the key does not exist.
    /// If <paramref name="key"/> already holds a value, it is overwritten, regardless of its type.
    /// Any previous time to live associated with the <paramref name="key"/> is discarded on successful <c>SET</c> operation.
    /// </remarks>
    /// <param name="client">The Glide client instance used to send the command.</param>
    /// <param name="key">The key to set in the Glide client.</param>
    /// <param name="value">The value to associate with the specified <paramref name="key"/>.</param>
    /// <returns>A <see cref="Task"/> representing the asynchronous operation.</returns>
    /// <exception cref="OperationFailedException">
    /// Thrown when the command execution does not return a successful result.
    /// </exception>
    /// <seealso href="https://valkey.io/commands/set/"/>
    public static async Task<bool> SetIfExistsAsync(this IGlideClient client, string key, string value)
    {
        var result = await client.CommandAsync(ERequestType.Set, key, value, "XX");
        return result switch
        {
            "OK"   => true,
            "NIL"  => false,
            "NULL" => false,
            _      => throw new OperationFailedException(Language.Set_CommandExecutionWasNotSuccessfull),
        };
    }

    /// <summary>
    /// Change the value of <paramref name="key"/> to hold the <paramref name="value"/>.
    /// </summary>
    /// <remarks>
    /// Won't do anything if the key does not exist.
    /// If <paramref name="key"/> already holds a value, it is overwritten, regardless of its type.
    /// Any previous time to live associated with the <paramref name="key"/> is discarded on successful <c>SET</c> operation.
    /// </remarks>
    /// <param name="client">The Glide client instance used to send the command.</param>
    /// <param name="key">The key to set in the Glide client.</param>
    /// <param name="value">The value to associate with the specified <paramref name="key"/>.</param>
    /// <param name="expectedValue">The value the <paramref name="key"/> must currently hold.</param>
    /// <returns>A <see cref="Task"/> representing the asynchronous operation.</returns>
    /// <exception cref="OperationFailedException">
    /// Thrown when the command execution does not return a successful result.
    /// </exception>
    /// <seealso href="https://valkey.io/commands/set/"/>
    public static async Task<bool> SetIfEValueAsync(
        this IGlideClient client,
        string key,
        string value,
        string expectedValue
    )
    {
        var result = await client.CommandAsync(ERequestType.Set, key, value, "IFEQ", expectedValue);
        return result switch
        {
            "OK"   => true,
            "NIL"  => false,
            "NULL" => false,
            _      => throw new OperationFailedException(Language.Set_CommandExecutionWasNotSuccessfull),
        };
    }

    // ToDo: SET GET
    // ToDo: EX/PX
    // ToDo: EX/PX GET
    // ToDo: EXAT/PXAT
    // ToDo: EXAT/PXAT GET
    // ToDo: KEEPTTL
    // ToDo: KEEPTTL GET
}
