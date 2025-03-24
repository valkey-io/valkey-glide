namespace Valkey.Glide;

/// <summary>
/// Defines the contract for a transformer that converts an object of type <typeparamref name="T"/>
/// into a parameter representation.
/// </summary>
/// <typeparam name="T">The type of object to be transformed by the implementation.</typeparam>
public interface IGlideTransformer<in T>
{
    /// <summary>
    /// Converts the specified object of type <typeparamref name="T"/> into its parameter representation.
    /// </summary>
    /// <param name="t">The object to be transformed into a parameter representation.</param>
    /// <returns>A string representation of the specified object.</returns>
    string ToValkeyString(T t);
}
