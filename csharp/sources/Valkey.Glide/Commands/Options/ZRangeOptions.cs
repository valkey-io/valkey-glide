// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Valkey.Glide.Commands.Options;

/// <summary>
/// Represents infinity boundaries for score-based range queries.
/// </summary>
public enum InfBoundary
{
    /// <summary>
    /// Positive infinity.
    /// </summary>
    PositiveInfinity,

    /// <summary>
    /// Negative infinity.
    /// </summary>
    NegativeInfinity,
}

/// <summary>
/// Base interface for ZRange query types.
/// </summary>
public interface IZRangeQuery
{
    /// <summary>
    /// Converts the query to command arguments.
    /// </summary>
    /// <returns>List of command arguments.</returns>
    List<GlideString> ToArgs();
}

/// <summary>
/// Base interface for ZRangeWithScores query types.
/// </summary>
public interface IZRangeWithScoresQuery : IZRangeQuery
{
}

/// <summary>
/// Represents a range query by index (rank).
/// </summary>
/// <param name="start">The start index of the range.</param>
/// <param name="end">The end index of the range.</param>
public class RangeByIndex(long start, long end) : IZRangeQuery, IZRangeWithScoresQuery
{
    /// <summary>
    /// The start index of the range.
    /// </summary>
    public long Start { get; } = start;

    /// <summary>
    /// The end index of the range.
    /// </summary>
    public long End { get; } = end;

    /// <summary>
    /// Sets the reverse flag to return elements in reverse order.
    /// </summary>
    /// <returns>This RangeByIndex instance for method chaining.</returns>
    public RangeByIndex SetReverse()
    {
        Reverse = true;
        return this;
    }

    /// <summary>
    /// Converts the query to command arguments.
    /// </summary>
    /// <returns>List of command arguments.</returns>
    public List<GlideString> ToArgs()
    {
        var args = new List<GlideString> { Start.ToString(), End.ToString() };
        if (Reverse)
        {
            args.Add("REV");
        }
        return args;
    }
}

/// <summary>
/// Represents a range query by score.
/// </summary>
public class RangeByScore : IZRangeQuery, IZRangeWithScoresQuery
{
    /// <summary>
    /// The start score boundary.
    /// </summary>
    public ScoreBoundary Start { get; }

    /// <summary>
    /// The end score boundary.
    /// </summary>
    public ScoreBoundary End { get; }

    /// <summary>
    /// Whether to reverse the order (highest to lowest score).
    /// </summary>
    public bool Reverse { get; private set; }

    /// <summary>
    /// The limit for the number of elements to return.
    /// </summary>
    public RangeLimit? Limit { get; private set; }

    /// <summary>
    /// Initializes a new instance of the RangeByScore class.
    /// </summary>
    /// <param name="start">The start score boundary.</param>
    /// <param name="end">The end score boundary.</param>
    public RangeByScore(ScoreBoundary start, ScoreBoundary end)
    {
        Start = start;
        End = end;
    }

    /// <summary>
    /// Sets the reverse flag to return elements in reverse order.
    /// </summary>
    /// <returns>This RangeByScore instance for method chaining.</returns>
    public RangeByScore SetReverse()
    {
        Reverse = true;
        return this;
    }

    /// <summary>
    /// Sets the limit for the number of elements to return.
    /// </summary>
    /// <param name="offset">The starting position of the range, zero based.</param>
    /// <param name="count">The maximum number of elements to include in the range. A negative count returns all elements from the offset.</param>
    /// <returns>This RangeByScore instance for method chaining.</returns>
    public RangeByScore SetLimit(long offset, long count)
    {
        Limit = new RangeLimit(offset, count);
        return this;
    }

    /// <summary>
    /// Converts the query to command arguments.
    /// </summary>
    /// <returns>List of command arguments.</returns>
    public List<GlideString> ToArgs()
    {
        var args = new List<GlideString> { Start.ToString(), End.ToString(), "BYSCORE" };
        if (Reverse)
        {
            args.Add("REV");
        }
        if (Limit != null)
        {
            args.AddRange(Limit.ToArgs());
        }
        return args;
    }
}

/// <summary>
/// Represents a range query by lexicographical order.
/// </summary>
public class RangeByLex : IZRangeQuery
{
    /// <summary>
    /// The start lexicographical boundary.
    /// </summary>
    public LexBoundary Start { get; }

    /// <summary>
    /// The end lexicographical boundary.
    /// </summary>
    public LexBoundary End { get; }

    /// <summary>
    /// Whether to reverse the order.
    /// </summary>
    public bool Reverse { get; private set; }

    /// <summary>
    /// The limit for the number of elements to return.
    /// </summary>
    public RangeLimit? Limit { get; private set; }

    /// <summary>
    /// Initializes a new instance of the RangeByLex class.
    /// </summary>
    /// <param name="start">The start lexicographical boundary.</param>
    /// <param name="end">The end lexicographical boundary.</param>
    public RangeByLex(LexBoundary start, LexBoundary end)
    {
        Start = start;
        End = end;
    }

    /// <summary>
    /// Sets the reverse flag to return elements in reverse order.
    /// </summary>
    /// <returns>This RangeByLex instance for method chaining.</returns>
    public RangeByLex SetReverse()
    {
        Reverse = true;
        return this;
    }

    /// <summary>
    /// Sets the limit for the number of elements to return.
    /// </summary>
    /// <param name="offset">The starting position of the range, zero based.</param>
    /// <param name="count">The maximum number of elements to include in the range. A negative count returns all elements from the offset.</param>
    /// <returns>This RangeByLex instance for method chaining.</returns>
    public RangeByLex SetLimit(long offset, long count)
    {
        Limit = new RangeLimit(offset, count);
        return this;
    }

    /// <summary>
    /// Converts the query to command arguments.
    /// </summary>
    /// <returns>List of command arguments.</returns>
    public List<GlideString> ToArgs()
    {
        var args = new List<GlideString> { Start.ToString(), End.ToString(), "BYLEX" };
        if (Reverse)
        {
            args.Add("REV");
        }
        if (Limit != null)
        {
            args.AddRange(Limit.ToArgs());
        }
        return args;
    }
}

/// <summary>
/// Represents a score boundary for range queries.
/// </summary>
public class ScoreBoundary
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
    /// <returns>A new ScoreBoundary instance.</returns>
    public static ScoreBoundary Inclusive(double score)
    {
        return new ScoreBoundary(score.ToString());
    }

    /// <summary>
    /// Creates an exclusive score boundary.
    /// </summary>
    /// <param name="score">The score value.</param>
    /// <returns>A new ScoreBoundary instance.</returns>
    public static ScoreBoundary Exclusive(double score)
    {
        return new ScoreBoundary($"({score}");
    }

    /// <summary>
    /// Creates an infinite score boundary.
    /// </summary>
    /// <param name="boundary">The infinity boundary type.</param>
    /// <returns>A new ScoreBoundary instance.</returns>
    public static ScoreBoundary Infinite(InfBoundary boundary)
    {
        return new ScoreBoundary(boundary switch
        {
            InfBoundary.PositiveInfinity => "+inf",
            InfBoundary.NegativeInfinity => "-inf",
            _ => throw new ArgumentException($"Unknown InfBoundary value: {boundary}")
        });
    }

    /// <summary>
    /// Returns the string representation of the boundary.
    /// </summary>
    /// <returns>The boundary as a string.</returns>
    public override string ToString()
    {
        return _value;
    }
}

/// <summary>
/// Represents a lexicographical boundary for range queries.
/// </summary>
public class LexBoundary
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
    /// <returns>A new LexBoundary instance.</returns>
    public static LexBoundary Inclusive(string value)
    {
        return new LexBoundary($"[{value}");
    }

    /// <summary>
    /// Creates an exclusive lexicographical boundary.
    /// </summary>
    /// <param name="value">The lexicographical value.</param>
    /// <returns>A new LexBoundary instance.</returns>
    public static LexBoundary Exclusive(string value)
    {
        return new LexBoundary($"({value}");
    }

    /// <summary>
    /// Creates an infinite lexicographical boundary.
    /// </summary>
    /// <param name="boundary">The infinity boundary type.</param>
    /// <returns>A new LexBoundary instance.</returns>
    public static LexBoundary Infinite(InfBoundary boundary)
    {
        return new LexBoundary(boundary switch
        {
            InfBoundary.PositiveInfinity => "+",
            InfBoundary.NegativeInfinity => "-",
            _ => throw new ArgumentException($"Unknown InfBoundary value: {boundary}")
        });
    }

    /// <summary>
    /// Returns the string representation of the boundary.
    /// </summary>
    /// <returns>The boundary as a string.</returns>
    public override string ToString()
    {
        return _value;
    }
}

/// <summary>
/// Represents a limit for range queries.
/// </summary>
public class RangeLimit
{
    /// <summary>
    /// The starting position of the range, zero based.
    /// </summary>
    public long Offset { get; }

    /// <summary>
    /// The maximum number of elements to include in the range. A negative count returns all elements from the offset.
    /// </summary>
    public long Count { get; }

    /// <summary>
    /// Initializes a new instance of the RangeLimit class.
    /// </summary>
    /// <param name="offset">The starting position of the range, zero based.</param>
    /// <param name="count">The maximum number of elements to include in the range. A negative count returns all elements from the offset.</param>
    public RangeLimit(long offset, long count)
    {
        Offset = offset;
        Count = count;
    }

    /// <summary>
    /// Converts the limit to command arguments.
    /// </summary>
    /// <returns>List of command arguments.</returns>
    internal List<GlideString> ToArgs()
    {
        return ["LIMIT", Offset.ToString(), Count.ToString()];
    }
}
