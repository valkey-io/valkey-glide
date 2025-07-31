// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System.Globalization;

using static Valkey.Glide.Commands.Constants.Constants;

namespace Valkey.Glide.Commands.Options;

/// <summary>
/// Represents a score boundary for sorted set range operations.
/// </summary>
public readonly struct ScoreBoundary
{
    private readonly string _value;

    private ScoreBoundary(string value)
    {
        _value = value;
    }

    /// <summary>
    /// Creates an inclusive score boundary.
    /// </summary>
    /// <param name="score">The score value.</param>
    /// <returns>An inclusive score boundary.</returns>
    public static ScoreBoundary Inclusive(double score) =>
        double.IsPositiveInfinity(score) ? new(PositiveInfinityScore) :
        double.IsNegativeInfinity(score) ? new(NegativeInfinityScore) :
        new(score.ToString(CultureInfo.InvariantCulture));

    /// <summary>
    /// Creates an exclusive score boundary.
    /// </summary>
    /// <param name="score">The score value.</param>
    /// <returns>An exclusive score boundary.</returns>
    public static ScoreBoundary Exclusive(double score) =>
        double.IsPositiveInfinity(score) ? new(PositiveInfinityScore) :
        double.IsNegativeInfinity(score) ? new(NegativeInfinityScore) :
        new("(" + score.ToString(CultureInfo.InvariantCulture));

    /// <summary>
    /// Creates a positive infinity boundary.
    /// </summary>
    /// <returns>A positive infinity boundary.</returns>
    public static ScoreBoundary PositiveInfinity() => new(PositiveInfinityScore);

    /// <summary>
    /// Creates a negative infinity boundary.
    /// </summary>
    /// <returns>A negative infinity boundary.</returns>
    public static ScoreBoundary NegativeInfinity() => new(NegativeInfinityScore);

    /// <summary>
    /// Converts the score boundary to its string representation.
    /// </summary>
    /// <returns>The string representation of the score boundary.</returns>
    public override string ToString() => _value;

    /// <summary>
    /// Implicit conversion to string.
    /// </summary>
    /// <param name="boundary">The score boundary.</param>
    public static implicit operator string(ScoreBoundary boundary) => boundary._value;
}

/// <summary>
/// Represents a range query by index (rank) for the ZRANGE command.
/// </summary>
/// <param name="start">The start index.</param>
/// <param name="stop">The stop index.</param>
public class RangeByIndex(long start, long stop)
{
    /// <summary>
    /// The start index.
    /// </summary>
    public long Start { get; } = start;

    /// <summary>
    /// The stop index.
    /// </summary>
    public long Stop { get; } = stop;

    /// <summary>
    /// Whether to reverse the order.
    /// </summary>
    public bool Reverse { get; private set; }

    /// <summary>
    /// Sets the query to return results in reverse order.
    /// </summary>
    /// <returns>This instance for method chaining.</returns>
    public RangeByIndex SetReverse()
    {
        Reverse = true;
        return this;
    }

    /// <summary>
    /// Converts the query to command arguments.
    /// </summary>
    /// <returns>An array of string arguments for the command.</returns>
    internal string[] ToArgs()
    {
        List<string> args = [
            Start.ToString(CultureInfo.InvariantCulture),
            Stop.ToString(CultureInfo.InvariantCulture)
        ];

        if (Reverse)
        {
            args.Add(ReverseKeyword);
        }

        return [.. args];
    }
}

/// <summary>
/// Represents a range query by score for the ZRANGE command.
/// </summary>
/// <param name="start">The start score boundary.</param>
/// <param name="end">The end score boundary.</param>
public class RangeByScore(ScoreBoundary start, ScoreBoundary end)
{
    /// <summary>
    /// The start score boundary.
    /// </summary>
    public ScoreBoundary Start { get; } = start;

    /// <summary>
    /// The end score boundary.
    /// </summary>
    public ScoreBoundary End { get; } = end;

    /// <summary>
    /// Whether to reverse the order.
    /// </summary>
    public bool Reverse { get; private set; }

    /// <summary>
    /// The limit offset and count.
    /// </summary>
    public (long Offset, long Count)? Limit { get; private set; }

    /// <summary>
    /// Sets the query to return results in reverse order.
    /// </summary>
    /// <returns>This instance for method chaining.</returns>
    public RangeByScore SetReverse()
    {
        Reverse = true;
        return this;
    }

    /// <summary>
    /// Sets a limit on the number of results returned.
    /// </summary>
    /// <param name="offset">The offset to start from.</param>
    /// <param name="count">The maximum number of results to return.</param>
    /// <returns>This instance for method chaining.</returns>
    public RangeByScore SetLimit(long offset, long count)
    {
        Limit = (offset, count);
        return this;
    }

    /// <summary>
    /// Converts the query to command arguments.
    /// </summary>
    /// <returns>An array of string arguments for the command.</returns>
    internal string[] ToArgs()
    {
        List<string> args = [];

        // When using REV with BYSCORE, the start should be the highest score and end should be the lowest score
        // So we need to swap the order of arguments when Reverse is true
        if (Reverse)
        {
            args.Add(End.ToString());
            args.Add(Start.ToString());
        }
        else
        {
            args.Add(Start.ToString());
            args.Add(End.ToString());
        }

        args.Add(ByScoreKeyword);

        if (Reverse)
        {
            args.Add(ReverseKeyword);
        }

        if (Limit.HasValue)
        {
            args.Add(LimitKeyword);
            args.Add(Limit.Value.Offset.ToString(CultureInfo.InvariantCulture));
            args.Add(Limit.Value.Count.ToString(CultureInfo.InvariantCulture));
        }

        return [.. args];
    }
}

/// <summary>
/// Represents a range query by lexicographical value for the ZRANGE command.
/// </summary>
/// <param name="start">The start lexicographical boundary.</param>
/// <param name="end">The end lexicographical boundary.</param>
public class RangeByLex(LexBoundary start, LexBoundary end)
{
    /// <summary>
    /// The start lexicographical boundary.
    /// </summary>
    public LexBoundary Start { get; } = start;

    /// <summary>
    /// The end lexicographical boundary.
    /// </summary>
    public LexBoundary End { get; } = end;

    /// <summary>
    /// Whether to reverse the order.
    /// </summary>
    public bool Reverse { get; private set; }

    /// <summary>
    /// The limit offset and count.
    /// </summary>
    public (long Offset, long Count)? Limit { get; private set; }

    /// <summary>
    /// Sets the query to return results in reverse order.
    /// </summary>
    /// <returns>This instance for method chaining.</returns>
    public RangeByLex SetReverse()
    {
        Reverse = true;
        return this;
    }

    /// <summary>
    /// Sets a limit on the number of results returned.
    /// </summary>
    /// <param name="offset">The offset to start from.</param>
    /// <param name="count">The maximum number of results to return.</param>
    /// <returns>This instance for method chaining.</returns>
    public RangeByLex SetLimit(long offset, long count)
    {
        Limit = (offset, count);
        return this;
    }

    /// <summary>
    /// Converts the query to command arguments.
    /// </summary>
    /// <returns>An array of string arguments for the command.</returns>
    internal string[] ToArgs()
    {
        List<string> args = [];

        // When using REV with BYLEX, the start should be the highest lex value and end should be the lowest lex value
        // So we need to swap the order of arguments when Reverse is true
        if (Reverse)
        {
            args.Add(End.ToString());
            args.Add(Start.ToString());
        }
        else
        {
            args.Add(Start.ToString());
            args.Add(End.ToString());
        }

        args.Add(ByLexKeyword);

        if (Reverse)
        {
            args.Add(ReverseKeyword);
        }

        if (Limit.HasValue)
        {
            args.Add(LimitKeyword);
            args.Add(Limit.Value.Offset.ToString(CultureInfo.InvariantCulture));
            args.Add(Limit.Value.Count.ToString(CultureInfo.InvariantCulture));
        }

        return [.. args];
    }
}

/// <summary>
/// Represents a lexicographical boundary for sorted set range operations.
/// </summary>
public readonly struct LexBoundary
{
    private readonly string _value;

    private LexBoundary(string value)
    {
        _value = value;
    }

    /// <summary>
    /// Creates an inclusive lexicographical boundary.
    /// </summary>
    /// <param name="value">The lexicographical value.</param>
    /// <returns>An inclusive lexicographical boundary.</returns>
    public static LexBoundary Inclusive(ValkeyValue value) => new("[" + value.ToString());

    /// <summary>
    /// Creates an exclusive lexicographical boundary.
    /// </summary>
    /// <param name="value">The lexicographical value.</param>
    /// <returns>An exclusive lexicographical boundary.</returns>
    public static LexBoundary Exclusive(ValkeyValue value) => new("(" + value.ToString());

    /// <summary>
    /// Creates a negative infinity boundary.
    /// </summary>
    /// <returns>A negative infinity boundary.</returns>
    public static LexBoundary NegativeInfinity() => new(Commands.Constants.Constants.NegativeInfinity);

    /// <summary>
    /// Creates a positive infinity boundary.
    /// </summary>
    /// <returns>A positive infinity boundary.</returns>
    public static LexBoundary PositiveInfinity() => new(Commands.Constants.Constants.PositiveInfinity);

    /// <summary>
    /// Converts the lexicographical boundary to its string representation.
    /// </summary>
    /// <returns>The string representation of the lexicographical boundary.</returns>
    public override string ToString() => _value;

    /// <summary>
    /// Implicit conversion to string.
    /// </summary>
    /// <param name="boundary">The lexicographical boundary.</param>
    public static implicit operator string(LexBoundary boundary) => boundary._value;
}
