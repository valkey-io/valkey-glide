using System.Diagnostics.CodeAnalysis;

namespace Valkey.Glide;

/// <summary>
/// Represents the result of an operation that produces an <typeparamref name="TValue"/> value,
/// along with a flag indicating if the result is empty.
/// </summary>
/// <remarks>
/// This structure is used to encapsulate the outcome of specific commands or calculations,
/// while also providing additional metadata about whether the result is valid or empty.
/// </remarks>
/// <param name="Value">The value returned or <see langword="default"/> if <paramref name="Empty"/> is <see langword="true"/></param>
public readonly record struct Result<T>(T? Value, bool Empty)
{
    /// <summary>
    /// Defines an implicit conversion from a value of type <typeparamref name="T"/> to a <see cref="Result{T}"/>.
    /// </summary>
    /// <param name="value">
    /// A value of type <typeparamref name="T"/>. If the value is <see langword="null"/>, the result will be marked as <see cref="Empty"/>.
    /// </param>
    /// <returns>
    /// A <see cref="Result{T}"/> instance encapsulating the specified <paramref name="value"/>
    /// and indicating whether the result is <see cref="Empty"/>.
    /// </returns>
    public static implicit operator Result<T>(T? value) => new(value ?? default, value is null);

    /// <summary>
    /// Defines an implicit conversion from a <see cref="Result{T}"/> to its underlying value of type <typeparamref name="T"/>.
    /// </summary>
    /// <param name="result">
    /// A <see cref="Result{T}"/> instance encapsulating the value to retrieve.
    /// If the result is marked as <see cref="Empty"/>, the returned value will be <see langword="null"/>.
    /// </param>
    /// <returns>
    /// The underlying value of type <typeparamref name="T"/> contained in the <paramref name="result"/>,
    /// or <see langword="null"/> if the result is  <see cref="Empty"/>.
    /// </returns>
    public static implicit operator T?(Result<T> result) => result.Value;

    /// <summary>
    /// Returns the <see cref="Value"/> if it is not <see cref="Empty"/>;
    /// otherwise, returns a <see langword="default"/> value.
    /// </summary>
    /// <param name="defaultValueFactory">
    /// A <see cref="Func{TResult}"/> to produce a default value if the result is empty.
    /// If <see langword="null"/>, the <see langword="default"/> value of <typeparamref name="T"/> will be returned.
    /// </param>
    /// <returns>
    /// The encapsulated <see cref="Value"/> if it is not <see cref="Empty"/>;
    /// otherwise, the result of the  <paramref name="defaultValueFactory"/>
    /// or the <see langword="default"/> value of <typeparamref name="T"/>.
    /// </returns>
    [return: NotNullIfNotNull(nameof(defaultValueFactory))]
    public T? ValueOrDefault(Func<T>? defaultValueFactory = null)
    {
        if (!Empty)
            return Value!;
        if (defaultValueFactory is null)
            return default;
        return defaultValueFactory()!;
    }
}
