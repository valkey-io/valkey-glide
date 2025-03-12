using System.Diagnostics;
using System.Diagnostics.CodeAnalysis;
using System.Runtime.CompilerServices;
using Valkey.Glide.InterOp.Exceptions;
using Valkey.Glide.InterOp;
using ERequestType = Valkey.Glide.InterOp.Native.ERequestType;

namespace Valkey.Glide.Commands;

/// <summary>
/// Contains a collection of extension methods for executing various Redis SET commands
/// in a Glide-compatible Redis client. These methods support functionality such as
/// standard set operation, conditional set operations, and retrieval of previous values.
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

    // <seealso href="https://valkey.io/commands/set/"/>
    // <exception cref="GlideException">The set operation was not successful</exception>


    /// <inheritdoc cref="Builder.ExecuteAsync"/>
    /// <remarks>
    /// Equivalent to calling <c>SET key value</c>
    /// </remarks>
    /// <exception cref="GlideSetCommandFailedException">Thrown if the operation failed</exception>
    public static async Task SetAsync(this IGlideClient client, string key, string value)
    {
        var result = await new Builder().WithKey(key)
            .WithValue(value)
            .ExecuteAsync(client);
        if (result.IsOk())
            return;
        throw new GlideSetCommandFailedException(result);
    }

    /// <inheritdoc cref="Builder.ExecuteAsync"/>
    /// <remarks>
    /// Equivalent to calling <c>SET key value GET</c>
    /// </remarks>
    /// <exception cref="GlideSetCommandFailedException">Thrown if the operation failed</exception>
    public static async Task<string?> SetAndGetAsync(this IGlideClient client, string key, string value)
    {
        var result = await new Builder().WithKey(key)
            .WithValue(value)
            .WithGet()
            .ExecuteAsync(client);
        if (result.IsString(out var text))
            return text;
        if (result.IsNone())
            return null;
        throw new GlideSetCommandFailedException(result);
    }

    /// <inheritdoc cref="Builder.ExecuteAsync"/>
    /// <remarks>Executes <c>SET key value NX</c></remarks>
    /// <exception cref="GlideSetCommandFailedException">Thrown if the operation failed</exception>
    public static async Task SetIfNotExistAsync(this IGlideClient client, string key, string value)
    {
        var result = await new Builder().WithKey(key)
            .WithValue(value)
            .WithSetIfNotExists()
            .ExecuteAsync(client);
        if (result.IsOk())
            return;
        throw new GlideSetCommandFailedException(result);
    }

    /// <inheritdoc cref="Builder.ExecuteAsync"/>
    /// <remarks>Executes <c>SET key value NX GET</c></remarks>
    /// <exception cref="GlideSetCommandFailedException">Thrown if the operation failed</exception>
    public static async Task<string?> SetAndGetIfNotExistsAsync(this IGlideClient client, string key, string value)
    {
        var result = await new Builder().WithKey(key)
            .WithValue(value)
            .WithSetIfNotExists()
            .WithGet()
            .ExecuteAsync(client);
        if (result.IsString(out var text))
            return text;
        if (result.IsNone())
            return null;
        throw new GlideSetCommandFailedException(result);
    }

    /// <inheritdoc cref="Builder.ExecuteAsync"/>
    /// <remarks>Executes <c>SET key value XX</c></remarks>
    /// <exception cref="GlideSetCommandFailedException">Thrown if the operation failed</exception>
    public static async Task SetIfExistsAsync(this IGlideClient client, string key, string value)
    {
        var result = await new Builder().WithKey(key)
            .WithValue(value)
            .WithSetIfExists()
            .ExecuteAsync(client);
        if (result.IsOk())
            return;
        throw new GlideSetCommandFailedException(result);
    }

    /// <inheritdoc cref="Builder.ExecuteAsync"/>
    /// <remarks>Executes <c>SET key value XX GET</c></remarks>
    /// <exception cref="GlideSetCommandFailedException">Thrown if the operation failed</exception>
    public static async Task<string?> SetAndGetIfExistsAsync(this IGlideClient client, string key, string value)
    {
        var result = await new Builder().WithKey(key)
            .WithValue(value)
            .WithSetIfExists()
            .WithGet()
            .ExecuteAsync(client);
        if (result.IsString(out var text))
            return text;
        if (result.IsNone())
            return null;
        throw new GlideSetCommandFailedException(result);
    }

    /// <inheritdoc cref="Builder.ExecuteAsync"/>
    /// <remarks>Executes <c>SET key value IFEQ comparison-value</c></remarks>
    /// <exception cref="GlideSetCommandFailedException">Thrown if the operation failed</exception>
    public static async Task SetIfEqualAsync(this IGlideClient client, string key, string value, string comparisonValue)
    {
        var result = await new Builder().WithKey(key)
            .WithValue(value)
            .WithSetIfEquals(comparisonValue)
            .ExecuteAsync(client);
        if (result.IsOk())
            return;
        throw new GlideSetCommandFailedException(result);
    }

    /// <inheritdoc cref="Builder.ExecuteAsync"/>
    /// <remarks>Executes <c>SET key value IFEQ comparison-value GET</c></remarks>
    /// <exception cref="GlideSetCommandFailedException">Thrown if the operation failed</exception>
    public static async Task<string?> SetAndGetIfEqualAsync(
        this IGlideClient client,
        string key,
        string value,
        string comparisonValue
    )
    {
        var result = await new Builder().WithKey(key)
            .WithValue(value)
            .WithSetIfEquals(comparisonValue)
            .WithGet()
            .ExecuteAsync(client);
        if (result.IsString(out var text))
            return text;
        if (result.IsNone())
            return null;
        throw new GlideSetCommandFailedException(result);
    }

    /// <inheritdoc cref="Builder.ExecuteAsync"/>
    /// <remarks>Executes <c>SET key value EX seconds</c> or <c>SET key value PX seconds</c></remarks>
    /// <exception cref="GlideSetCommandFailedException">Thrown if the operation failed</exception>
    public static async Task SetWithExpirationAsync(
        this IGlideClient client,
        string key,
        string value,
        TimeSpan expiration
    )
    {
        var result = await new Builder().WithKey(key)
            .WithValue(value)
            .WithExpiresIn(expiration)
            .ExecuteAsync(client);
        if (result.IsOk())
            return;
        throw new GlideSetCommandFailedException(result);
    }

    /// <inheritdoc cref="Builder.ExecuteAsync"/>
    /// <remarks>Executes <c>SET key value GET EX seconds</c> or <c>SET key value GET PX seconds</c></remarks>
    /// <exception cref="GlideSetCommandFailedException">Thrown if the operation failed</exception>
    public static async Task<string?> SetAndGetWithExpirationAsync(
        this IGlideClient client,
        string key,
        string value,
        TimeSpan expiration
    )
    {
        var result = await new Builder().WithKey(key)
            .WithValue(value)
            .WithExpiresIn(expiration)
            .WithGet()
            .ExecuteAsync(client);
        if (result.IsString(out var text))
            return text;
        if (result.IsNone())
            return null;
        throw new GlideSetCommandFailedException(result);
    }

    /// <inheritdoc cref="Builder.ExecuteAsync"/>
    /// <remarks>Executes <c>SET key value NX EX seconds</c> or <c>SET key value NX PX seconds</c></remarks>
    /// <exception cref="GlideSetCommandFailedException">Thrown if the operation failed</exception>
    public static async Task SetIfNotExistsWithExpirationAsync(
        this IGlideClient client,
        string key,
        string value,
        TimeSpan expiration
    )
    {
        var result = await new Builder().WithKey(key)
            .WithValue(value)
            .WithSetIfNotExists()
            .WithExpiresIn(expiration)
            .ExecuteAsync(client);
        if (result.IsOk())
            return;
        throw new GlideSetCommandFailedException(result);
    }

    /// <inheritdoc cref="Builder.ExecuteAsync"/>
    /// <remarks>Executes <c>SET key value NX GET EX seconds</c> <c>SET key value NX GET PX seconds</c></remarks>
    /// <exception cref="GlideSetCommandFailedException">Thrown if the operation failed</exception>
    public static async Task<string?> SetAndGetIfNotExistsWithExpirationAsync(
        this IGlideClient client,
        string key,
        string value,
        TimeSpan expiration
    )
    {
        var result = await new Builder().WithKey(key)
            .WithValue(value)
            .WithSetIfNotExists()
            .WithExpiresIn(expiration)
            .WithGet()
            .ExecuteAsync(client);
        if (result.IsString(out var text))
            return text;
        if (result.IsNone())
            return null;
        throw new GlideSetCommandFailedException(result);
    }

    /// <inheritdoc cref="Builder.ExecuteAsync"/>
    /// <remarks>Executes <c>SET key value XX EX seconds</c> or <c>SET key value XX PX seconds</c></remarks>
    /// <exception cref="GlideSetCommandFailedException">Thrown if the operation failed</exception>
    public static async Task SetIfExistsWithExpirationAsync(
        this IGlideClient client,
        string key,
        string value,
        TimeSpan expiration
    )
    {
        var result = await new Builder().WithKey(key)
            .WithValue(value)
            .WithSetIfExists()
            .WithExpiresIn(expiration)
            .ExecuteAsync(client);
        if (result.IsOk())
            return;
        throw new GlideSetCommandFailedException(result);
    }

    /// <inheritdoc cref="Builder.ExecuteAsync"/>
    /// <remarks>Executes <c>SET key value XX GET EX seconds</c> or <c>SET key value XX GET PX seconds</c></remarks>
    /// <exception cref="GlideSetCommandFailedException">Thrown if the operation failed</exception>
    public static async Task<string?> SetAndGetIfExistsWithExpirationAsync(
        this IGlideClient client,
        string key,
        string value,
        TimeSpan expiration
    )
    {
        var result = await new Builder().WithKey(key)
            .WithValue(value)
            .WithSetIfExists()
            .WithExpiresIn(expiration)
            .WithGet()
            .ExecuteAsync(client);
        if (result.IsString(out var text))
            return text;
        if (result.IsNone())
            return null;
        throw new GlideSetCommandFailedException(result);
    }

    /// <inheritdoc cref="Builder.ExecuteAsync"/>
    /// <remarks>Executes <c>SET key value IFEQ comparison-value EX seconds</c> or <c>SET key value IFEQ comparison-value PX seconds</c></remarks>
    /// <exception cref="GlideSetCommandFailedException">Thrown if the operation failed</exception>
    public static async Task SetIfEqualWithExpirationAsync(
        this IGlideClient client,
        string key,
        string value,
        string comparisonValue,
        TimeSpan expiration
    )
    {
        var result = await new Builder().WithKey(key)
            .WithValue(value)
            .WithSetIfEquals(comparisonValue)
            .WithExpiresIn(expiration)
            .ExecuteAsync(client);
        if (result.IsOk())
            return;
        throw new GlideSetCommandFailedException(result);
    }

    /// <inheritdoc cref="Builder.ExecuteAsync"/>
    /// <remarks>Executes <c>SET key value IFEQ comparison-value GET EX seconds</c></remarks>
    /// <exception cref="GlideSetCommandFailedException">Thrown if the operation failed</exception>
    public static async Task<string?> SetAndGetIfEqualWithExpirationAsync(
        this IGlideClient client,
        string key,
        string value,
        string comparisonValue,
        TimeSpan expiration
    )
    {
        var result = await new Builder().WithKey(key)
            .WithValue(value)
            .WithSetIfEquals(comparisonValue)
            .WithExpiresIn(expiration)
            .WithGet()
            .ExecuteAsync(client);
        if (result.IsString(out var text))
            return text;
        if (result.IsNone())
            return null;
        throw new GlideSetCommandFailedException(result);
    }


    /// <inheritdoc cref="Builder.ExecuteAsync"/>
    /// <remarks>Executes <c>SET key value EXAT unix-time-seconds</c> or <c>SET key value PXAT unix-time-seconds</c></remarks>
    /// <exception cref="GlideSetCommandFailedException">Thrown if the operation failed</exception>
    public static async Task SetWithExpirationAsync(
        this IGlideClient client,
        string key,
        string value,
        DateTime expiration
    )
    {
        var result = await new Builder().WithKey(key)
            .WithValue(value)
            .WithExpiresAt(expiration)
            .ExecuteAsync(client);
        if (result.IsOk())
            return;
        throw new GlideSetCommandFailedException(result);
    }

    /// <inheritdoc cref="Builder.ExecuteAsync"/>
    /// <remarks>Executes <c>SET key value GET EXAT unix-time-seconds</c> or <c>SET key value GET PXAT unix-time-seconds</c></remarks>
    /// <exception cref="GlideSetCommandFailedException">Thrown if the operation failed</exception>
    public static async Task<string?> SetAndGetWithExpirationAsync(
        this IGlideClient client,
        string key,
        string value,
        DateTime expiration
    )
    {
        var result = await new Builder().WithKey(key)
            .WithValue(value)
            .WithExpiresAt(expiration)
            .WithGet()
            .ExecuteAsync(client);
        if (result.IsString(out var text))
            return text;
        if (result.IsNone())
            return null;
        throw new GlideSetCommandFailedException(result);
    }

    /// <inheritdoc cref="Builder.ExecuteAsync"/>
    /// <remarks>Executes <c>SET key value NX EXAT unix-time-seconds</c> or <c>SET key value NX PXAT unix-time-seconds</c></remarks>
    /// <exception cref="GlideSetCommandFailedException">Thrown if the operation failed</exception>
    public static async Task SetIfExistsWithExpirationAsync(
        this IGlideClient client,
        string key,
        string value,
        DateTime expiration
    )
    {
        var result = await new Builder().WithKey(key)
            .WithValue(value)
            .WithSetIfExists()
            .WithExpiresAt(expiration)
            .ExecuteAsync(client);
        if (result.IsOk())
            return;
        throw new GlideSetCommandFailedException(result);
    }

    /// <inheritdoc cref="Builder.ExecuteAsync"/>
    /// <remarks>Executes <c>SET key value NX GET EXAT unix-time-seconds</c> or <c>SET key value NX GET PXAT unix-time-seconds</c></remarks>
    /// <exception cref="GlideSetCommandFailedException">Thrown if the operation failed</exception>
    public static async Task<string?> SetAndGetIfExistsWithExpirationAsync(
        this IGlideClient client,
        string key,
        string value,
        DateTime expiration
    )
    {
        var result = await new Builder().WithKey(key)
            .WithValue(value)
            .WithSetIfExists()
            .WithExpiresAt(expiration)
            .WithGet()
            .ExecuteAsync(client);
        if (result.IsString(out var text))
            return text;
        if (result.IsNone())
            return null;
        throw new GlideSetCommandFailedException(result);
    }

    /// <inheritdoc cref="Builder.ExecuteAsync"/>
    /// <remarks>Executes <c>SET key value XX EXAT unix-time-seconds</c> or <c>SET key value XX PXAT unix-time-seconds</c></remarks>
    /// <exception cref="GlideSetCommandFailedException">Thrown if the operation failed</exception>
    public static async Task SetIfNotExistsWithExpirationAsync(
        this IGlideClient client,
        string key,
        string value,
        DateTime expiration
    )
    {
        var result = await new Builder().WithKey(key)
            .WithValue(value)
            .WithSetIfNotExists()
            .WithExpiresAt(expiration)
            .ExecuteAsync(client);
        if (result.IsOk())
            return;
        throw new GlideSetCommandFailedException(result);
    }

    /// <inheritdoc cref="Builder.ExecuteAsync"/>
    /// <remarks>Executes <c>SET key value XX GET EXAT unix-time-seconds</c> or <c>SET key value XX GET PXAT unix-time-seconds</c></remarks>
    /// <exception cref="GlideSetCommandFailedException">Thrown if the operation failed</exception>
    public static async Task<string?> SetAndGetIfNotExistsWithExpirationAsync(
        this IGlideClient client,
        string key,
        string value,
        DateTime expiration
    )
    {
        var result = await new Builder().WithKey(key)
            .WithValue(value)
            .WithSetIfNotExists()
            .WithExpiresAt(expiration)
            .WithGet()
            .ExecuteAsync(client);
        if (result.IsString(out var text))
            return text;
        if (result.IsNone())
            return null;
        throw new GlideSetCommandFailedException(result);
    }

    /// <inheritdoc cref="Builder.ExecuteAsync"/>
    /// <remarks>Executes <c>SET key value IFEQ comparison-value EXAT unix-time-seconds</c> or <c>SET key value IFEQ comparison-value PXAT unix-time-seconds</c></remarks>
    /// <exception cref="GlideSetCommandFailedException">Thrown if the operation failed</exception>
    public static async Task SetIfEqualWithExpirationAsync(
        this IGlideClient client,
        string key,
        string value,
        string comparisonValue,
        DateTime expiration
    )
    {
        var result = await new Builder().WithKey(key)
            .WithValue(value)
            .WithSetIfEquals(comparisonValue)
            .WithExpiresAt(expiration)
            .ExecuteAsync(client);
        if (result.IsOk())
            return;
        throw new GlideSetCommandFailedException(result);
    }

    /// <inheritdoc cref="Builder.ExecuteAsync"/>
    /// <remarks>Executes <c>SET key value IFEQ comparison-value GET EXAT unix-time-seconds</c> or <c>SET key value IFEQ comparison-value GET PXAT unix-time-seconds</c></remarks>
    /// <exception cref="GlideSetCommandFailedException">Thrown if the operation failed</exception>
    public static async Task<string?> SetAndGetIfEqualWithExpirationAsync(
        this IGlideClient client,
        string key,
        string value,
        string comparisonValue,
        DateTime expiration
    )
    {
        var result = await new Builder().WithKey(key)
            .WithValue(value)
            .WithSetIfEquals(comparisonValue)
            .WithExpiresAt(expiration)
            .WithGet()
            .ExecuteAsync(client);
        if (result.IsString(out var text))
            return text;
        if (result.IsNone())
            return null;
        throw new GlideSetCommandFailedException(result);
    }


    /// <inheritdoc cref="Builder.ExecuteAsync"/>
    /// <remarks>Executes <c>SET key value KEEPTTL</c></remarks>
    /// <exception cref="GlideSetCommandFailedException">Thrown if the operation failed</exception>
    public static async Task SetWithKeepTtlAsync(this IGlideClient client, string key, string value)
    {
        var result = await new Builder().WithKey(key)
            .WithValue(value)
            .WithKeepTtl()
            .ExecuteAsync(client);
        if (result.IsOk())
            return;
        throw new GlideSetCommandFailedException(result);
    }

    /// <inheritdoc cref="Builder.ExecuteAsync"/>
    /// <remarks>Executes <c>SET key value GET KEEPTTL</c></remarks>
    /// <exception cref="GlideSetCommandFailedException">Thrown if the operation failed</exception>
    public static async Task<string?> SetAndGetWithKeepTtlAsync(this IGlideClient client, string key, string value)
    {
        var result = await new Builder().WithKey(key)
            .WithValue(value)
            .WithKeepTtl()
            .WithGet()
            .ExecuteAsync(client);
        if (result.IsString(out var text))
            return text;
        if (result.IsNone())
            return null;
        throw new GlideSetCommandFailedException(result);
    }

    /// <inheritdoc cref="Builder.ExecuteAsync"/>
    /// <remarks>Executes <c>SET key value NX KEEPTTL</c></remarks>
    /// <exception cref="GlideSetCommandFailedException">Thrown if the operation failed</exception>
    public static async Task SetIfNotExistsWithKeepTtlAsync(this IGlideClient client, string key, string value)
    {
        var result = await new Builder().WithKey(key)
            .WithValue(value)
            .WithSetIfNotExists()
            .WithKeepTtl()
            .ExecuteAsync(client);
        if (result.IsOk())
            return;
        throw new GlideSetCommandFailedException(result);
    }

    /// <inheritdoc cref="Builder.ExecuteAsync"/>
    /// <remarks>Executes <c>SET key value NX GET KEEPTTL</c></remarks>
    /// <exception cref="GlideSetCommandFailedException">Thrown if the operation failed</exception>
    public static async Task<string?> SetAndGetIfNotExistsWithKeepTtlAsync(
        this IGlideClient client,
        string key,
        string value
    )
    {
        var result = await new Builder().WithKey(key)
            .WithValue(value)
            .WithSetIfNotExists()
            .WithKeepTtl()
            .WithGet()
            .ExecuteAsync(client);
        if (result.IsString(out var text))
            return text;
        if (result.IsNone())
            return null;
        throw new GlideSetCommandFailedException(result);
    }

    /// <inheritdoc cref="Builder.ExecuteAsync"/>
    /// <remarks>Executes <c>SET key value XX KEEPTTL</c></remarks>
    /// <exception cref="GlideSetCommandFailedException">Thrown if the operation failed</exception>
    public static async Task SetIfExistsWithKeepTtlAsync(this IGlideClient client, string key, string value)
    {
        var result = await new Builder().WithKey(key)
            .WithValue(value)
            .WithSetIfExists()
            .WithKeepTtl()
            .ExecuteAsync(client);
        if (result.IsOk())
            return;
        throw new GlideSetCommandFailedException(result);
    }

    /// <inheritdoc cref="Builder.ExecuteAsync"/>
    /// <remarks>Executes <c>SET key value XX GET KEEPTTL</c></remarks>
    /// <exception cref="GlideSetCommandFailedException">Thrown if the operation failed</exception>
    public static async Task<string?> SetAndGetIfExistsWithKeepTtlAsync(
        this IGlideClient client,
        string key,
        string value
    )
    {
        var result = await new Builder().WithKey(key)
            .WithValue(value)
            .WithSetIfExists()
            .WithKeepTtl()
            .WithGet()
            .ExecuteAsync(client);
        if (result.IsString(out var text))
            return text;
        if (result.IsNone())
            return null;
        throw new GlideSetCommandFailedException(result);
    }

    /// <inheritdoc cref="Builder.ExecuteAsync"/>
    /// <remarks>Executes <c>SET key value IFEQ comparison-value KEEPTTL</c></remarks>
    /// <exception cref="GlideSetCommandFailedException">Thrown if the operation failed</exception>
    public static async Task SetIfEqualWithKeepTtlAsync(
        this IGlideClient client,
        string key,
        string value,
        string comparisonValue
    )
    {
        var result = await new Builder().WithKey(key)
            .WithValue(value)
            .WithSetIfEquals(comparisonValue)
            .WithKeepTtl()
            .ExecuteAsync(client);
        if (result.IsOk())
            return;
        throw new GlideSetCommandFailedException(result);
    }

    /// <inheritdoc cref="Builder.ExecuteAsync"/>
    /// <remarks>Executes <c>SET key value IFEQ comparison-value GET KEEPTTL</c></remarks>
    /// <exception cref="GlideSetCommandFailedException">Thrown if the operation failed</exception>
    public static async Task<string?> SetAndGetIfEqualWithKeepTtlAsync(
        this IGlideClient client,
        string key,
        string value,
        string comparisonValue
    )
    {
        var result = await new Builder().WithKey(key)
            .WithValue(value)
            .WithSetIfEquals(comparisonValue)
            .WithKeepTtl()
            .WithGet()
            .ExecuteAsync(client);
        if (result.IsString(out var text))
            return text;
        if (result.IsNone())
            return null;
        throw new GlideSetCommandFailedException(result);
    }


    private sealed class Builder
    {
        private string _key   = string.Empty; // key
        private string _value = string.Empty; // value
        private bool   _get; // GET

        private TimeSpan? _expiresIn; // EX seconds / PX milliseconds
        private DateTime? _expiresAt; // EXAT unix-time-seconds / PXAT unix-time-milliseconds
        private bool      _keepTtl; // KEEPTTL

        private bool    _setIfDoesNotExists; // NX
        private bool    _setIfExists; // XX
        private string? _setIfEquals; // IFEQ comparison-value

        [MethodImpl(MethodImplOptions.AggressiveInlining | MethodImplOptions.AggressiveOptimization)]
        public Builder WithKey(string key)
        {
            _key = key;
            return this;
        }

        public Builder WithValue(string value)
        {
            _value = value;
            return this;
        }

        public Builder WithGet()
        {
            _get = true;
            return this;
        }

        public Builder WithExpiresIn(TimeSpan expiresIn)
        {
            _expiresIn = expiresIn;
            _expiresAt = null;
            _keepTtl   = false;
            return this;
        }

        public Builder WithExpiresAt(DateTime expiresAt)
        {
            _expiresIn = null;
            _expiresAt = expiresAt;
            _keepTtl   = false;
            return this;
        }

        public Builder WithKeepTtl()
        {
            _expiresIn = null;
            _expiresAt = null;
            _keepTtl   = true;
            return this;
        }

        public Builder WithSetIfNotExists()
        {
            _setIfDoesNotExists = true;
            _setIfExists        = false;
            _setIfEquals        = null;
            return this;
        }

        public Builder WithSetIfExists()
        {
            _setIfDoesNotExists = false;
            _setIfExists        = true;
            _setIfEquals        = null;
            return this;
        }

        public Builder WithSetIfEquals(string value)
        {
            _setIfDoesNotExists = false;
            _setIfExists        = false;
            _setIfEquals        = value;
            return this;
        }


        [MethodImpl(MethodImplOptions.AggressiveInlining | MethodImplOptions.AggressiveOptimization)]
        public Task<Value> ExecuteAsync(IGlideClient client)
        {
            if (_expiresIn.HasValue)
                if (_expiresIn.Value.TotalMilliseconds % 1000 != 0)
                    return ExecuteTtlWithGet(
                        client,
                        [
                            "PX".AsRedisCommandText(),
                            ((ulong) _expiresIn.Value.TotalMilliseconds).ToString()
                            .AsRedisInteger()
                        ]
                    );
                else
                    return ExecuteTtlWithGet(
                        client,
                        [
                            "EX".AsRedisCommandText(),
                            ((ulong) _expiresIn.Value.TotalSeconds).ToString()
                            .AsRedisInteger()
                        ]
                    );
            if (_expiresAt.HasValue)
            {
                var unixTimeSpan = _expiresAt.Value - DateTime.UnixEpoch;
                if (unixTimeSpan.TotalMilliseconds % 1000 != 0)
                    return ExecuteTtlWithGet(
                        client,
                        [
                            "PXAT".AsRedisCommandText(),
                            ((ulong) unixTimeSpan.TotalMilliseconds).ToString()
                            .AsRedisInteger()
                        ]
                    );
                else
                    return ExecuteTtlWithGet(
                        client,
                        [
                            "EXAT".AsRedisCommandText(),
                            ((ulong) unixTimeSpan.TotalSeconds).ToString()
                            .AsRedisInteger()
                        ]
                    );
            }

            if (_keepTtl)
                return ExecuteTtlWithGet(client, ["KEEPTTL".AsRedisCommandText()]);
            return ExecuteTtlWithGet(client, []);
        }

        [MethodImpl(MethodImplOptions.AggressiveInlining | MethodImplOptions.AggressiveOptimization)]
        private Task<Value> ExecuteTtlWithGet(IGlideClient client, string[] ttlParameters)
        {
            if (_get)
                return ExecuteInner(client, ["GET".AsRedisCommandText(), ..ttlParameters]);
            return ExecuteInner(client, ttlParameters);
        }

        [MethodImpl(MethodImplOptions.AggressiveInlining | MethodImplOptions.AggressiveOptimization)]
        private Task<Value> ExecuteInner(IGlideClient client, string[] ttlParameters)
        {
            if (_setIfDoesNotExists)
                return client.CommandAsync(
                    ERequestType.Set,
                    [_key.AsRedisCommandText(), _value.AsRedisString(), "NX".AsRedisCommandText(), ..ttlParameters]
                );
            if (_setIfExists)
                return client.CommandAsync(
                    ERequestType.Set,
                    [_key.AsRedisCommandText(), _value.AsRedisString(), "XX".AsRedisCommandText(), ..ttlParameters]
                );
            if (_setIfEquals is not null)
                return client.CommandAsync(
                    ERequestType.Set,
                    [
                        _key.AsRedisCommandText(),
                        _value.AsRedisString(),
                        "IFEQ".AsRedisCommandText(),
                        _setIfEquals.AsRedisString(),
                        ..ttlParameters
                    ]
                );
            return client.CommandAsync(
                ERequestType.Set,
                [_key.AsRedisCommandText(), _value.AsRedisString(), ..ttlParameters]
            );
        }
    }
}
