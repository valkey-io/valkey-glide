using System.Globalization;

namespace Valkey.Glide;

internal static class ResultHelpers
{
    /// <summary>
    /// Parses the result of a command asynchronously and attempts to convert it to a specified type.
    /// </summary>
    /// <typeparam name="T">
    /// The type to which the result is parsed. This type must implement <see cref="ISpanParsable{T}"/>.
    /// </typeparam>
    /// <param name="commandAsync">
    /// A task representing the asynchronous execution of a command that returns a string. The result of this task
    /// represents the raw command output to be parsed.
    /// </param>
    /// <returns>
    /// A <see cref="Result{T}"/> object containing the parsed value and a flag indicating whether the result was empty.
    /// </returns>
    /// <exception cref="FormatException">
    /// Thrown when the command result cannot be parsed into the specified type.
    /// </exception>
    internal static async Task<Result<T>> ParseResultAsync<T>(Task<string?> commandAsync)
        where T : ISpanParsable<T>
    {
        string? result = await commandAsync.ConfigureAwait(false);
        if (result is null)
            return new Result<T>(default, true);
        if (T.TryParse(result, CultureInfo.InvariantCulture, out T? value))
            return new Result<T>(value, false);
        throw new FormatException($"The result '{result}' could not be parsed as an integer.");
    }
}
