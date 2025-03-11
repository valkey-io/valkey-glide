using System;

namespace Valkey.Glide.InterOp.Exceptions;

/// <summary>
/// Represents the base exception class for errors that occur during Glide operations.
/// </summary>
/// <remarks>
/// The <see cref="GlideException"/> serves as a foundational exception type for all exceptions
/// within the Glide framework. It provides a way to encapsulate error messages and optional inner exceptions
/// for improved debugging and error handling.
/// </remarks>
public class GlideException(string message, Exception? innerException = null) : Exception(message, innerException) { }
