// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Valkey.Glide.Commands.Options;

/// <summary>
/// Represents the range options for the ZCOUNT command.
/// </summary>
/// <param name="min">The minimum score boundary.</param>
/// <param name="max">The maximum score boundary.</param>
public readonly struct ZCountRange(ScoreBoundary min, ScoreBoundary max)
{
    /// <summary>
    /// The minimum score boundary.
    /// </summary>
    public ScoreBoundary Min { get; } = min;

    /// <summary>
    /// The maximum score boundary.
    /// </summary>
    public ScoreBoundary Max { get; } = max;

    /// <summary>
    /// Converts the range to command arguments.
    /// </summary>
    /// <returns>An array of string arguments for the command.</returns>
    internal string[] ToArgs() => [Min.ToString(), Max.ToString()];
}
