using Valkey.Glide.InterOp.Exceptions;
using Valkey.Glide.InterOp.Native;

using Value = Valkey.Glide.InterOp.Value;

namespace Valkey.Glide.Commands;

/// <summary>
/// The <see langword="class"/> provides extensions for executing
/// <c>GET</c> commands using an instance of <see cref="IGlideClient"/>.
/// </summary>
/// <seealso href="https://valkey.io/commands/get/"/>
public static class GetCommands
{
    /// <summary>
    /// Retrieves the value associated with the specified <paramref name="key"/> from the <paramref name="client"/>.
    /// </summary>
    /// <param name="client">The Glide client instance used to perform the operation.</param>
    /// <param name="key">The key whose associated value is to be retrieved. Cannot be <see langword="null"/> or whitespace.</param>
    /// <returns>
    /// A <see cref="Task{TResult}"/> representing the asynchronous operation.
    /// The task result contains the value associated with the specified key,
    /// or <see langword="null"/> if the <paramref name="key"/> does not exist.
    /// </returns>
    /// <exception cref="System.ArgumentException">Thrown when the <paramref name="key"/> is <see langword="null"/> or whitespace.</exception>
    /// <exception cref="GlideException">Thrown when the <c>GET</c> operation fails for unexpected reasons.</exception>
    public static async Task<string?> GetAsync(this IGlideClient client, string key)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(key);

        Value result = await client.CommandAsync(ERequestType.Get, key.AsRedisCommandText());
        if (result.IsNone())
            return null;
        if (result.IsString(out string? text))
            return text;

        throw new GlideException("Get failed");
    }
}
