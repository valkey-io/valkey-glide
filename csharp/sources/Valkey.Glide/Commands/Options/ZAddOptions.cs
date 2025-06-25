// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Valkey.Glide.Commands.Options;

/// <summary>
/// Defines conditions for updating or adding elements with the ZAdd command.
/// </summary>
public enum ConditionalSet
{
    /// <summary>
    /// Only update existing elements. Equivalent to "XX" in the Valkey API.
    /// </summary>
    OnlyIfExists,
    
    /// <summary>
    /// Only add new elements. Equivalent to "NX" in the Valkey API.
    /// </summary>
    OnlyIfDoesNotExist
}

/// <summary>
/// Specifies conditions for updating scores with the ZAdd command.
/// </summary>
public enum UpdateOptions
{
    /// <summary>
    /// Only update existing elements if the new score is less than the current score. Equivalent to "LT" in the Valkey API.
    /// </summary>
    ScoreLessThanCurrent,
    
    /// <summary>
    /// Only update existing elements if the new score is greater than the current score. Equivalent to "GT" in the Valkey API.
    /// </summary>
    ScoreGreaterThanCurrent
}

/// <summary>
/// Optional arguments for the ZAdd command.
/// </summary>
public class ZAddOptions
{
    /// <summary>
    /// Defines conditions for updating or adding elements.
    /// </summary>
    public ConditionalSet? ConditionalChange { get; private set; }

    /// <summary>
    /// Specifies conditions for updating scores.
    /// </summary>
    public UpdateOptions? UpdateOption { get; private set; }

    /// <summary>
    /// Changes the return value from the number of new elements added to the total number of elements changed.
    /// </summary>
    public bool Changed { get; private set; }

    /// <summary>
    /// Sets the conditional change option.
    /// </summary>
    /// <param name="conditionalChange">The conditional change option.</param>
    /// <returns>This ZAddOptions instance for method chaining.</returns>
    public ZAddOptions SetConditionalChange(ConditionalSet conditionalChange)
    {
        ConditionalChange = conditionalChange;
        return this;
    }

    /// <summary>
    /// Sets the update option.
    /// </summary>
    /// <param name="updateOption">The update option.</param>
    /// <returns>This ZAddOptions instance for method chaining.</returns>
    public ZAddOptions SetUpdateOption(UpdateOptions updateOption)
    {
        UpdateOption = updateOption;
        return this;
    }

    /// <summary>
    /// Sets the changed flag.
    /// </summary>
    /// <param name="changed">Whether to return the number of changed elements instead of added elements.</param>
    /// <returns>This ZAddOptions instance for method chaining.</returns>
    public ZAddOptions SetChanged(bool changed)
    {
        Changed = changed;
        return this;
    }

    /// <summary>
    /// Converts the options to a list of arguments for the ZAdd command.
    /// </summary>
    /// <returns>A list of string arguments.</returns>
    internal List<GlideString> ToArgs()
    {
        var args = new List<GlideString>();

        if (ConditionalChange.HasValue)
        {
            args.Add(ConditionalChange.Value switch
            {
                ConditionalSet.OnlyIfExists => "XX",
                ConditionalSet.OnlyIfDoesNotExist => "NX",
                _ => throw new ArgumentException($"Unknown ConditionalSet value: {ConditionalChange.Value}")
            });
        }

        if (UpdateOption.HasValue)
        {
            args.Add(UpdateOption.Value switch
            {
                UpdateOptions.ScoreLessThanCurrent => "LT",
                UpdateOptions.ScoreGreaterThanCurrent => "GT",
                _ => throw new ArgumentException($"Unknown UpdateOptions value: {UpdateOption.Value}")
            });
        }

        if (Changed)
        {
            args.Add("CH");
        }

        return args;
    }
}
