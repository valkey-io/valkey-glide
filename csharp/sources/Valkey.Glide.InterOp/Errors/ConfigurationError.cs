namespace Valkey.Glide.InterOp.Errors;

/// <summary>
/// An errors that is thrown when a request cannot be completed in current configuration settings.
/// </summary>
public sealed class ConfigurationError : GlideException
{
    // TODO set HelpLink with link to wiki

    public ConfigurationError() { }

    public ConfigurationError(string message) : base(message) { }

    public ConfigurationError(string message, Exception innerException) : base(message, innerException) { }
}