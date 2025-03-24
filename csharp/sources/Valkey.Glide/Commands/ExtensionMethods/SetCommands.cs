using Valkey.Glide.Exceptions;
using Valkey.Glide.InterOp;

namespace Valkey.Glide.Commands.ExtensionMethods;

/// <summary>
/// Contains a collection of extension methods for executing various Redis SET commands
/// in a Glide-compatible Redis client. These methods support functionality such as
/// standard set operation, conditional set operations, and retrieval of previous values.
/// </summary>
public static class SetCommands
{
    /// <inheritdoc cref="SetCommand.ExecuteAsync"/>
    /// <remarks>
    /// Equivalent to calling <c>SET key value</c>
    /// </remarks>
    /// <exception cref="GlideSetCommandFailedException">Thrown if the operation failed</exception>
    public static async Task SetAsync<T>(
        this IGlideClient client,
        string key,
        T value)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(key);
        Value result = await new SetCommand<T>().WithKey(key)
            .WithValue(value)
            .ExecuteAsync(client);
        if (result.IsOk())
            return;
        throw new GlideSetCommandFailedException(key, result);
    }

    /// <inheritdoc cref="SetCommand.ExecuteAsync"/>
    /// <remarks>
    /// Equivalent to calling <c>SET key value GET</c>
    /// </remarks>
    /// <exception cref="GlideSetCommandFailedException">Thrown if the operation failed</exception>
    public static async Task<string?> SetAndGetAsync<T>(
        this IGlideClient client,
        string key,
        T value)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(key);
        Value result = await new SetCommand<T>().WithKey(key)
            .WithValue(value)
            .WithGet()
            .ExecuteAsync(client);
        if (result.IsString(out string? text))
            return text;
        if (result.IsNone())
            return null;
        throw new GlideSetCommandFailedException(key, result);
    }

    /// <inheritdoc cref="SetCommand.ExecuteAsync"/>
    /// <remarks>Executes <c>SET key value NX</c></remarks>
    /// <exception cref="GlideSetCommandFailedException">Thrown if the operation failed</exception>
    public static async Task SetIfNotExistAsync<T>(
        this IGlideClient client,
        string key,
        T value)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(key);
        Value result = await new SetCommand<T>().WithKey(key)
            .WithValue(value)
            .WithSetIfNotExists()
            .ExecuteAsync(client);
        if (result.IsOk())
            return;
        throw new GlideSetCommandFailedException(key, result);
    }

    /// <inheritdoc cref="SetCommand.ExecuteAsync"/>
    /// <remarks>Executes <c>SET key value NX GET</c></remarks>
    /// <exception cref="GlideSetCommandFailedException">Thrown if the operation failed</exception>
    public static async Task<string?> SetAndGetIfNotExistsAsync<T>(
        this IGlideClient client,
        string key,
        T value)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(key);
        Value result = await new SetCommand<T>().WithKey(key)
            .WithValue(value)
            .WithSetIfNotExists()
            .WithGet()
            .ExecuteAsync(client);
        if (result.IsString(out string? text))
            return text;
        if (result.IsNone())
            return null;
        throw new GlideSetCommandFailedException(key, result);
    }

    /// <inheritdoc cref="SetCommand.ExecuteAsync"/>
    /// <remarks>Executes <c>SET key value XX</c></remarks>
    /// <exception cref="GlideSetCommandFailedException">Thrown if the operation failed</exception>
    public static async Task SetIfExistsAsync<T>(
        this IGlideClient client,
        string key,
        T value)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(key);
        Value result = await new SetCommand<T>().WithKey(key)
            .WithValue(value)
            .WithSetIfExists()
            .ExecuteAsync(client);
        if (result.IsOk())
            return;
        throw new GlideSetCommandFailedException(key, result);
    }

    /// <inheritdoc cref="SetCommand.ExecuteAsync"/>
    /// <remarks>Executes <c>SET key value XX GET</c></remarks>
    /// <exception cref="GlideSetCommandFailedException">Thrown if the operation failed</exception>
    public static async Task<string?> SetAndGetIfExistsAsync<T>(
        this IGlideClient client,
        string key,
        T value)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(key);
        Value result = await new SetCommand<T>().WithKey(key)
            .WithValue(value)
            .WithSetIfExists()
            .WithGet()
            .ExecuteAsync(client);
        if (result.IsString(out string? text))
            return text;
        if (result.IsNone())
            return null;
        throw new GlideSetCommandFailedException(key, result);
    }

    /// <inheritdoc cref="SetCommand.ExecuteAsync"/>
    /// <remarks>Executes <c>SET key value IFEQ comparison-value</c></remarks>
    /// <exception cref="GlideSetCommandFailedException">Thrown if the operation failed</exception>
    public static async Task SetIfEqualAsync<T>(
        this IGlideClient client,
        string key,
        T value,
        string comparisonValue)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(key);
        Value result = await new SetCommand<T>().WithKey(key)
            .WithValue(value)
            .WithSetIfEquals(comparisonValue)
            .ExecuteAsync(client);
        if (result.IsOk())
            return;
        throw new GlideSetCommandFailedException(key, result);
    }

    /// <inheritdoc cref="SetCommand.ExecuteAsync"/>
    /// <remarks>Executes <c>SET key value IFEQ comparison-value GET</c></remarks>
    /// <exception cref="GlideSetCommandFailedException">Thrown if the operation failed</exception>
    public static async Task<string?> SetAndGetIfEqualAsync<T>(
        this IGlideClient client,
        string key,
        T value,
        string comparisonValue
    )
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(key);
        Value result = await new SetCommand<T>().WithKey(key)
            .WithValue(value)
            .WithSetIfEquals(comparisonValue)
            .WithGet()
            .ExecuteAsync(client);
        if (result.IsString(out string? text))
            return text;
        if (result.IsNone())
            return null;
        throw new GlideSetCommandFailedException(key, result);
    }

    /// <inheritdoc cref="SetCommand.ExecuteAsync"/>
    /// <remarks>Executes <c>SET key value EX seconds</c> or <c>SET key value PX seconds</c></remarks>
    /// <exception cref="GlideSetCommandFailedException">Thrown if the operation failed</exception>
    public static async Task SetWithExpirationAsync<T>(
        this IGlideClient client,
        string key,
        T value,
        TimeSpan expiration
    )
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(key);
        Value result = await new SetCommand<T>().WithKey(key)
            .WithValue(value)
            .WithExpiresIn(expiration)
            .ExecuteAsync(client);
        if (result.IsOk())
            return;
        throw new GlideSetCommandFailedException(key, result);
    }

    /// <inheritdoc cref="SetCommand.ExecuteAsync"/>
    /// <remarks>Executes <c>SET key value GET EX seconds</c> or <c>SET key value GET PX seconds</c></remarks>
    /// <exception cref="GlideSetCommandFailedException">Thrown if the operation failed</exception>
    public static async Task<string?> SetAndGetWithExpirationAsync<T>(
        this IGlideClient client,
        string key,
        T value,
        TimeSpan expiration
    )
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(key);
        Value result = await new SetCommand<T>().WithKey(key)
            .WithValue(value)
            .WithExpiresIn(expiration)
            .WithGet()
            .ExecuteAsync(client);
        if (result.IsString(out string? text))
            return text;
        if (result.IsNone())
            return null;
        throw new GlideSetCommandFailedException(key, result);
    }

    /// <inheritdoc cref="SetCommand.ExecuteAsync"/>
    /// <remarks>Executes <c>SET key value NX EX seconds</c> or <c>SET key value NX PX seconds</c></remarks>
    /// <exception cref="GlideSetCommandFailedException">Thrown if the operation failed</exception>
    public static async Task SetIfNotExistsWithExpirationAsync<T>(
        this IGlideClient client,
        string key,
        T value,
        TimeSpan expiration
    )
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(key);
        Value result = await new SetCommand<T>().WithKey(key)
            .WithValue(value)
            .WithSetIfNotExists()
            .WithExpiresIn(expiration)
            .ExecuteAsync(client);
        if (result.IsOk())
            return;
        throw new GlideSetCommandFailedException(key, result);
    }

    /// <inheritdoc cref="SetCommand.ExecuteAsync"/>
    /// <remarks>Executes <c>SET key value NX GET EX seconds</c> <c>SET key value NX GET PX seconds</c></remarks>
    /// <exception cref="GlideSetCommandFailedException">Thrown if the operation failed</exception>
    public static async Task<string?> SetAndGetIfNotExistsWithExpirationAsync<T>(
        this IGlideClient client,
        string key,
        T value,
        TimeSpan expiration
    )
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(key);
        Value result = await new SetCommand<T>().WithKey(key)
            .WithValue(value)
            .WithSetIfNotExists()
            .WithExpiresIn(expiration)
            .WithGet()
            .ExecuteAsync(client);
        if (result.IsString(out string? text))
            return text;
        if (result.IsNone())
            return null;
        throw new GlideSetCommandFailedException(key, result);
    }

    /// <inheritdoc cref="SetCommand.ExecuteAsync"/>
    /// <remarks>Executes <c>SET key value XX EX seconds</c> or <c>SET key value XX PX seconds</c></remarks>
    /// <exception cref="GlideSetCommandFailedException">Thrown if the operation failed</exception>
    public static async Task SetIfExistsWithExpirationAsync<T>(
        this IGlideClient client,
        string key,
        T value,
        TimeSpan expiration
    )
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(key);
        Value result = await new SetCommand<T>().WithKey(key)
            .WithValue(value)
            .WithSetIfExists()
            .WithExpiresIn(expiration)
            .ExecuteAsync(client);
        if (result.IsOk())
            return;
        throw new GlideSetCommandFailedException(key, result);
    }

    /// <inheritdoc cref="SetCommand.ExecuteAsync"/>
    /// <remarks>Executes <c>SET key value XX GET EX seconds</c> or <c>SET key value XX GET PX seconds</c></remarks>
    /// <exception cref="GlideSetCommandFailedException">Thrown if the operation failed</exception>
    public static async Task<string?> SetAndGetIfExistsWithExpirationAsync<T>(
        this IGlideClient client,
        string key,
        T value,
        TimeSpan expiration
    )
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(key);
        Value result = await new SetCommand<T>().WithKey(key)
            .WithValue(value)
            .WithSetIfExists()
            .WithExpiresIn(expiration)
            .WithGet()
            .ExecuteAsync(client);
        if (result.IsString(out string? text))
            return text;
        if (result.IsNone())
            return null;
        throw new GlideSetCommandFailedException(key, result);
    }

    /// <inheritdoc cref="SetCommand.ExecuteAsync"/>
    /// <remarks>Executes <c>SET key value IFEQ comparison-value EX seconds</c> or <c>SET key value IFEQ comparison-value PX seconds</c></remarks>
    /// <exception cref="GlideSetCommandFailedException">Thrown if the operation failed</exception>
    public static async Task SetIfEqualWithExpirationAsync<T>(
        this IGlideClient client,
        string key,
        T value,
        string comparisonValue,
        TimeSpan expiration
    )
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(key);
        Value result = await new SetCommand<T>().WithKey(key)
            .WithValue(value)
            .WithSetIfEquals(comparisonValue)
            .WithExpiresIn(expiration)
            .ExecuteAsync(client);
        if (result.IsOk())
            return;
        throw new GlideSetCommandFailedException(key, result);
    }

    /// <inheritdoc cref="SetCommand.ExecuteAsync"/>
    /// <remarks>Executes <c>SET key value IFEQ comparison-value GET EX seconds</c></remarks>
    /// <exception cref="GlideSetCommandFailedException">Thrown if the operation failed</exception>
    public static async Task<string?> SetAndGetIfEqualWithExpirationAsync<T>(
        this IGlideClient client,
        string key,
        T value,
        string comparisonValue,
        TimeSpan expiration
    )
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(key);
        Value result = await new SetCommand<T>().WithKey(key)
            .WithValue(value)
            .WithSetIfEquals(comparisonValue)
            .WithExpiresIn(expiration)
            .WithGet()
            .ExecuteAsync(client);
        if (result.IsString(out string? text))
            return text;
        if (result.IsNone())
            return null;
        throw new GlideSetCommandFailedException(key, result);
    }


    /// <inheritdoc cref="SetCommand.ExecuteAsync"/>
    /// <remarks>Executes <c>SET key value EXAT unix-time-seconds</c> or <c>SET key value PXAT unix-time-seconds</c></remarks>
    /// <exception cref="GlideSetCommandFailedException">Thrown if the operation failed</exception>
    public static async Task SetWithExpirationAsync<T>(
        this IGlideClient client,
        string key,
        T value,
        DateTime expiration
    )
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(key);
        Value result = await new SetCommand<T>().WithKey(key)
            .WithValue(value)
            .WithExpiresAt(expiration)
            .ExecuteAsync(client);
        if (result.IsOk())
            return;
        throw new GlideSetCommandFailedException(key, result);
    }

    /// <inheritdoc cref="SetCommand.ExecuteAsync"/>
    /// <remarks>Executes <c>SET key value GET EXAT unix-time-seconds</c> or <c>SET key value GET PXAT unix-time-seconds</c></remarks>
    /// <exception cref="GlideSetCommandFailedException">Thrown if the operation failed</exception>
    public static async Task<string?> SetAndGetWithExpirationAsync<T>(
        this IGlideClient client,
        string key,
        T value,
        DateTime expiration
    )
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(key);
        Value result = await new SetCommand<T>().WithKey(key)
            .WithValue(value)
            .WithExpiresAt(expiration)
            .WithGet()
            .ExecuteAsync(client);
        if (result.IsString(out string? text))
            return text;
        if (result.IsNone())
            return null;
        throw new GlideSetCommandFailedException(key, result);
    }

    /// <inheritdoc cref="SetCommand.ExecuteAsync"/>
    /// <remarks>Executes <c>SET key value NX EXAT unix-time-seconds</c> or <c>SET key value NX PXAT unix-time-seconds</c></remarks>
    /// <exception cref="GlideSetCommandFailedException">Thrown if the operation failed</exception>
    public static async Task SetIfExistsWithExpirationAsync<T>(
        this IGlideClient client,
        string key,
        T value,
        DateTime expiration
    )
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(key);
        Value result = await new SetCommand<T>().WithKey(key)
            .WithValue(value)
            .WithSetIfExists()
            .WithExpiresAt(expiration)
            .ExecuteAsync(client);
        if (result.IsOk())
            return;
        throw new GlideSetCommandFailedException(key, result);
    }

    /// <inheritdoc cref="SetCommand.ExecuteAsync"/>
    /// <remarks>Executes <c>SET key value NX GET EXAT unix-time-seconds</c> or <c>SET key value NX GET PXAT unix-time-seconds</c></remarks>
    /// <exception cref="GlideSetCommandFailedException">Thrown if the operation failed</exception>
    public static async Task<string?> SetAndGetIfExistsWithExpirationAsync<T>(
        this IGlideClient client,
        string key,
        T value,
        DateTime expiration
    )
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(key);
        Value result = await new SetCommand<T>().WithKey(key)
            .WithValue(value)
            .WithSetIfExists()
            .WithExpiresAt(expiration)
            .WithGet()
            .ExecuteAsync(client);
        if (result.IsString(out string? text))
            return text;
        if (result.IsNone())
            return null;
        throw new GlideSetCommandFailedException(key, result);
    }

    /// <inheritdoc cref="SetCommand.ExecuteAsync"/>
    /// <remarks>Executes <c>SET key value XX EXAT unix-time-seconds</c> or <c>SET key value XX PXAT unix-time-seconds</c></remarks>
    /// <exception cref="GlideSetCommandFailedException">Thrown if the operation failed</exception>
    public static async Task SetIfNotExistsWithExpirationAsync<T>(
        this IGlideClient client,
        string key,
        T value,
        DateTime expiration
    )
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(key);
        Value result = await new SetCommand<T>().WithKey(key)
            .WithValue(value)
            .WithSetIfNotExists()
            .WithExpiresAt(expiration)
            .ExecuteAsync(client);
        if (result.IsOk())
            return;
        throw new GlideSetCommandFailedException(key, result);
    }

    /// <inheritdoc cref="SetCommand.ExecuteAsync"/>
    /// <remarks>Executes <c>SET key value XX GET EXAT unix-time-seconds</c> or <c>SET key value XX GET PXAT unix-time-seconds</c></remarks>
    /// <exception cref="GlideSetCommandFailedException">Thrown if the operation failed</exception>
    public static async Task<string?> SetAndGetIfNotExistsWithExpirationAsync<T>(
        this IGlideClient client,
        string key,
        T value,
        DateTime expiration
    )
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(key);
        Value result = await new SetCommand<T>().WithKey(key)
            .WithValue(value)
            .WithSetIfNotExists()
            .WithExpiresAt(expiration)
            .WithGet()
            .ExecuteAsync(client);
        if (result.IsString(out string? text))
            return text;
        if (result.IsNone())
            return null;
        throw new GlideSetCommandFailedException(key, result);
    }

    /// <inheritdoc cref="SetCommand.ExecuteAsync"/>
    /// <remarks>Executes <c>SET key value IFEQ comparison-value EXAT unix-time-seconds</c> or <c>SET key value IFEQ comparison-value PXAT unix-time-seconds</c></remarks>
    /// <exception cref="GlideSetCommandFailedException">Thrown if the operation failed</exception>
    public static async Task SetIfEqualWithExpirationAsync<T>(
        this IGlideClient client,
        string key,
        T value,
        string comparisonValue,
        DateTime expiration
    )
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(key);
        Value result = await new SetCommand<T>().WithKey(key)
            .WithValue(value)
            .WithSetIfEquals(comparisonValue)
            .WithExpiresAt(expiration)
            .ExecuteAsync(client);
        if (result.IsOk())
            return;
        throw new GlideSetCommandFailedException(key, result);
    }

    /// <inheritdoc cref="SetCommand.ExecuteAsync"/>
    /// <remarks>Executes <c>SET key value IFEQ comparison-value GET EXAT unix-time-seconds</c> or <c>SET key value IFEQ comparison-value GET PXAT unix-time-seconds</c></remarks>
    /// <exception cref="GlideSetCommandFailedException">Thrown if the operation failed</exception>
    public static async Task<string?> SetAndGetIfEqualWithExpirationAsync<T>(
        this IGlideClient client,
        string key,
        T value,
        string comparisonValue,
        DateTime expiration
    )
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(key);
        Value result = await new SetCommand<T>().WithKey(key)
            .WithValue(value)
            .WithSetIfEquals(comparisonValue)
            .WithExpiresAt(expiration)
            .WithGet()
            .ExecuteAsync(client);
        if (result.IsString(out string? text))
            return text;
        if (result.IsNone())
            return null;
        throw new GlideSetCommandFailedException(key, result);
    }


    /// <inheritdoc cref="SetCommand.ExecuteAsync"/>
    /// <remarks>Executes <c>SET key value KEEPTTL</c></remarks>
    /// <exception cref="GlideSetCommandFailedException">Thrown if the operation failed</exception>
    public static async Task SetWithKeepTtlAsync<T>(
        this IGlideClient client,
        string key,
        T value)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(key);
        Value result = await new SetCommand<T>().WithKey(key)
            .WithValue(value)
            .WithKeepTtl()
            .ExecuteAsync(client);
        if (result.IsOk())
            return;
        throw new GlideSetCommandFailedException(key, result);
    }

    /// <inheritdoc cref="SetCommand.ExecuteAsync"/>
    /// <remarks>Executes <c>SET key value GET KEEPTTL</c></remarks>
    /// <exception cref="GlideSetCommandFailedException">Thrown if the operation failed</exception>
    public static async Task<string?> SetAndGetWithKeepTtlAsync<T>(
        this IGlideClient client,
        string key,
        T value)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(key);
        Value result = await new SetCommand<T>().WithKey(key)
            .WithValue(value)
            .WithKeepTtl()
            .WithGet()
            .ExecuteAsync(client);
        if (result.IsString(out string? text))
            return text;
        if (result.IsNone())
            return null;
        throw new GlideSetCommandFailedException(key, result);
    }

    /// <inheritdoc cref="SetCommand.ExecuteAsync"/>
    /// <remarks>Executes <c>SET key value NX KEEPTTL</c></remarks>
    /// <exception cref="GlideSetCommandFailedException">Thrown if the operation failed</exception>
    public static async Task SetIfNotExistsWithKeepTtlAsync<T>(
        this IGlideClient client,
        string key,
        T value)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(key);
        Value result = await new SetCommand<T>().WithKey(key)
            .WithValue(value)
            .WithSetIfNotExists()
            .WithKeepTtl()
            .ExecuteAsync(client);
        if (result.IsOk())
            return;
        throw new GlideSetCommandFailedException(key, result);
    }

    /// <inheritdoc cref="SetCommand.ExecuteAsync"/>
    /// <remarks>Executes <c>SET key value NX GET KEEPTTL</c></remarks>
    /// <exception cref="GlideSetCommandFailedException">Thrown if the operation failed</exception>
    public static async Task<string?> SetAndGetIfNotExistsWithKeepTtlAsync<T>(
        this IGlideClient client,
        string key,
        T value
    )
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(key);
        Value result = await new SetCommand<T>().WithKey(key)
            .WithValue(value)
            .WithSetIfNotExists()
            .WithKeepTtl()
            .WithGet()
            .ExecuteAsync(client);
        if (result.IsString(out string? text))
            return text;
        if (result.IsNone())
            return null;
        throw new GlideSetCommandFailedException(key, result);
    }

    /// <inheritdoc cref="SetCommand.ExecuteAsync"/>
    /// <remarks>Executes <c>SET key value XX KEEPTTL</c></remarks>
    /// <exception cref="GlideSetCommandFailedException">Thrown if the operation failed</exception>
    public static async Task SetIfExistsWithKeepTtlAsync<T>(
        this IGlideClient client,
        string key,
        T value)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(key);
        Value result = await new SetCommand<T>().WithKey(key)
            .WithValue(value)
            .WithSetIfExists()
            .WithKeepTtl()
            .ExecuteAsync(client);
        if (result.IsOk())
            return;
        throw new GlideSetCommandFailedException(key, result);
    }

    /// <inheritdoc cref="SetCommand.ExecuteAsync"/>
    /// <remarks>Executes <c>SET key value XX GET KEEPTTL</c></remarks>
    /// <exception cref="GlideSetCommandFailedException">Thrown if the operation failed</exception>
    public static async Task<string?> SetAndGetIfExistsWithKeepTtlAsync<T>(
        this IGlideClient client,
        string key,
        T value
    )
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(key);
        Value result = await new SetCommand<T>().WithKey(key)
            .WithValue(value)
            .WithSetIfExists()
            .WithKeepTtl()
            .WithGet()
            .ExecuteAsync(client);
        if (result.IsString(out string? text))
            return text;
        if (result.IsNone())
            return null;
        throw new GlideSetCommandFailedException(key, result);
    }

    /// <inheritdoc cref="SetCommand.ExecuteAsync"/>
    /// <remarks>Executes <c>SET key value IFEQ comparison-value KEEPTTL</c></remarks>
    /// <exception cref="GlideSetCommandFailedException">Thrown if the operation failed</exception>
    public static async Task SetIfEqualWithKeepTtlAsync<T>(
        this IGlideClient client,
        string key,
        T value,
        string comparisonValue
    )
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(key);
        Value result = await new SetCommand<T>().WithKey(key)
            .WithValue(value)
            .WithSetIfEquals(comparisonValue)
            .WithKeepTtl()
            .ExecuteAsync(client);
        if (result.IsOk())
            return;
        throw new GlideSetCommandFailedException(key, result);
    }

    /// <inheritdoc cref="SetCommand.ExecuteAsync"/>
    /// <remarks>Executes <c>SET key value IFEQ comparison-value GET KEEPTTL</c></remarks>
    /// <exception cref="GlideSetCommandFailedException">Thrown if the operation failed</exception>
    public static async Task<string?> SetAndGetIfEqualWithKeepTtlAsync<T>(
        this IGlideClient client,
        string key,
        T value,
        string comparisonValue
    )
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(key);
        Value result = await new SetCommand<T>().WithKey(key)
            .WithValue(value)
            .WithSetIfEquals(comparisonValue)
            .WithKeepTtl()
            .WithGet()
            .ExecuteAsync(client);
        if (result.IsString(out string? text))
            return text;
        if (result.IsNone())
            return null;
        throw new GlideSetCommandFailedException(key, result);
    }
}
