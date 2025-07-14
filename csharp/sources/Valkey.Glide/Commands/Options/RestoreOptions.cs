// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using static Valkey.Glide.Commands.Constants.Constants;

namespace Valkey.Glide.Commands.Options;

/// <summary>
/// Optional arguments for the RESTORE command.
/// </summary>
/// <remarks>
/// IDLETIME and FREQ modifiers cannot be set at the same time.
/// See <a href="https://valkey.io/commands/restore/">valkey.io</a>
/// </remarks>
public class RestoreOptions
{
    /// <summary>
    /// When true, it represents REPLACE keyword has been used
    /// </summary>
    public bool HasReplace { get; set; } = false;

    /// <summary>
    /// It represents the idletime of object
    /// </summary>
    public long? Idletime { get; set; } = null;

    /// <summary>
    /// It represents the frequency of object
    /// </summary>
    public long? Frequency { get; set; } = null;

    /// <summary>
    /// Sets the REPLACE option
    /// </summary>
    /// <returns>This RestoreOptions instance for method chaining</returns>
    public RestoreOptions Replace()
    {
        HasReplace = true;
        return this;
    }

    /// <summary>
    /// Sets the IDLETIME option
    /// </summary>
    /// <param name="idletime">The idletime value</param>
    /// <returns>This RestoreOptions instance for method chaining</returns>
    public RestoreOptions SetIdletime(long idletime)
    {
        Idletime = idletime;
        return this;
    }

    /// <summary>
    /// Sets the FREQ option
    /// </summary>
    /// <param name="frequency">The frequency value</param>
    /// <returns>This RestoreOptions instance for method chaining</returns>
    public RestoreOptions SetFrequency(long frequency)
    {
        Frequency = frequency;
        return this;
    }

    /// <summary>
    /// Creates the argument array to be used in the RESTORE command
    /// </summary>
    /// <returns>A string array that holds the subcommands and their arguments</returns>
    /// <exception cref="ArgumentException">Thrown when both IDLETIME and FREQ are set</exception>
    internal GlideString[] ToArgs()
    {
        List<GlideString> resultList = [];

        if (HasReplace)
        {
            resultList.Add((GlideString)ReplaceKeyword);
        }

        if (Idletime.HasValue && Frequency.HasValue)
        {
            throw new ArgumentException("IDLETIME and FREQ cannot be set at the same time.");
        }

        if (Idletime.HasValue)
        {
            resultList.Add((GlideString)IdletimeKeyword);
            resultList.Add(Idletime.Value.ToGlideString());
        }

        if (Frequency.HasValue)
        {
            resultList.Add((GlideString)FreqKeyword);
            resultList.Add(Frequency.Value.ToGlideString());
        }

        return [.. resultList];
    }
}
