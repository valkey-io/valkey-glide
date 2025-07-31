// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Valkey.Glide.Commands.Constants;

/// <summary>
/// Common constants used throughout Valkey GLIDE.
/// </summary>
public static class Constants
{
    public const string LimitKeyword = "LIMIT";
    public const string ReplaceKeyword = "REPLACE";
    public const string DbKeyword = "DB";
    public const string AbsttlKeyword = "ABSTTL";
    public const string IdletimeKeyword = "IDLETIME";
    public const string FreqKeyword = "FREQ";
    public const string WithScoresKeyword = "WITHSCORES";
    public const string ReverseKeyword = "REV";
    public const string ByLexKeyword = "BYLEX";
    public const string ByScoreKeyword = "BYSCORE";
    public const string MatchKeyword = "MATCH";
    public const string CountKeyword = "COUNT";

    /// <summary>
    /// Expiry keywords.
    /// </summary>
    public const string PersistKeyword = "PERSIST";
    public const string ExpiryKeyword = "EX";
    public const string ExpiryAtKeyword = "EXAT";

    /// <summary>
    /// Keywords for the LCS command.
    /// </summary>
    public const string LenKeyword = "LEN";
    public const string IdxKeyword = "IDX";
    public const string MinMatchLenKeyword = "MINMATCHLEN";
    public const string WithMatchLenKeyword = "WITHMATCHLEN";

    /// <summary>
    /// The highest bound in the sorted set for lexicographical operations.
    /// </summary>
    public const string PositiveInfinity = "+";

    /// <summary>
    /// The lowest bound in the sorted set for lexicographical operations.
    /// </summary>
    public const string NegativeInfinity = "-";

    /// <summary>
    /// The highest bound in the sorted set for score operations.
    /// </summary>
    public const string PositiveInfinityScore = "+inf";

    /// <summary>
    /// The lowest bound in the sorted set for score operations.
    /// </summary>
    public const string NegativeInfinityScore = "-inf";

}
