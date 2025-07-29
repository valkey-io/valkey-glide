// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Valkey.Glide.Internals;

/// <summary>
/// Contains guard clauses for validating method parameters and enforcing preconditions.
/// </summary>
internal static class GuardClauses
{
    /// <summary>
    /// Validates that the When parameter is either Always or NotExists.
    /// </summary>
    /// <param name="when">The When enum value to validate</param>
    /// <exception cref="ArgumentException">Thrown when the When parameter is not Always or NotExists</exception>
    public static void WhenAlwaysOrNotExists(When when)
    {
        switch (when)
        {
            case When.Always:
            case When.NotExists:
                break;
            case When.Exists:
            default:
                throw new ArgumentException(when + " is not valid in this context; the permitted values are: Always, NotExists");
        }
    }
}
