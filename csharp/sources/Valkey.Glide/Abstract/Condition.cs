// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System.Diagnostics;

using Valkey.Glide.Internals;

using static Valkey.Glide.Errors;

namespace Valkey.Glide;

/// <summary>
/// Describes a precondition used in a Valkey transaction.
/// </summary>
public abstract class Condition
{
    private Condition() { }
#pragma warning disable IDE0011 // Add braces
#pragma warning disable IDE0046 // Convert to conditional expression

    /// <summary>
    /// Enforces that the given hash-field must have the specified value.
    /// </summary>
    /// <param name="key">The key of the hash to check.</param>
    /// <param name="hashField">The field in the hash to check.</param>
    /// <param name="value">The value that the hash field must match.</param>
    public static Condition HashEqual(ValkeyKey key, ValkeyValue hashField, ValkeyValue value)
    {
        if (hashField.IsNull) throw new ArgumentNullException(nameof(hashField));
        if (value.IsNull) return HashNotExists(key, hashField);
        return new EqualsCondition(key, ValkeyType.Hash, hashField, true, value);
    }

    /// <summary>
    /// Enforces that the given hash-field must exist.
    /// </summary>
    /// <param name="key">The key of the hash to check.</param>
    /// <param name="hashField">The field in the hash to check.</param>
    public static Condition HashExists(ValkeyKey key, ValkeyValue hashField)
    {
        if (hashField.IsNull) throw new ArgumentNullException(nameof(hashField));
        return new ExistsCondition(key, ValkeyType.Hash, hashField, true);
    }

    /// <summary>
    /// Enforces that the given hash-field must not have the specified value.
    /// </summary>
    /// <param name="key">The key of the hash to check.</param>
    /// <param name="hashField">The field in the hash to check.</param>
    /// <param name="value">The value that the hash field must not match.</param>
    public static Condition HashNotEqual(ValkeyKey key, ValkeyValue hashField, ValkeyValue value)
    {
        if (hashField.IsNull) throw new ArgumentNullException(nameof(hashField));
        if (value.IsNull) return HashExists(key, hashField);
        return new EqualsCondition(key, ValkeyType.Hash, hashField, false, value);
    }

    /// <summary>
    /// Enforces that the given hash-field must not exist.
    /// </summary>
    /// <param name="key">The key of the hash to check.</param>
    /// <param name="hashField">The field in the hash that must not exist.</param>
    public static Condition HashNotExists(ValkeyKey key, ValkeyValue hashField)
    {
        if (hashField.IsNull) throw new ArgumentNullException(nameof(hashField));
        return new ExistsCondition(key, ValkeyType.Hash, hashField, false);
    }

    /// <summary>
    /// Enforces that the given key must exist.
    /// </summary>
    /// <param name="key">The key that must exist.</param>
    public static Condition KeyExists(ValkeyKey key)
        => new ExistsCondition(key, ValkeyType.None, ValkeyValue.Null, true);

    /// <summary>
    /// Enforces that the given key must not exist.
    /// </summary>
    /// <param name="key">The key that must not exist.</param>
    public static Condition KeyNotExists(ValkeyKey key)
        => new ExistsCondition(key, ValkeyType.None, ValkeyValue.Null, false);

    /// <summary>
    /// Enforces that the given list index must have the specified value.
    /// </summary>
    /// <param name="key">The key of the list to check.</param>
    /// <param name="index">The position in the list to check.</param>
    /// <param name="value">The value of the list position that must match.</param>
    public static Condition ListIndexEqual(ValkeyKey key, long index, ValkeyValue value)
        => new ListCondition(key, index, true, value);

    /// <summary>
    /// Enforces that the given list index must exist.
    /// </summary>
    /// <param name="key">The key of the list to check.</param>
    /// <param name="index">The position in the list that must exist.</param>
    public static Condition ListIndexExists(ValkeyKey key, long index)
        => new ListCondition(key, index, true, null);

    /// <summary>
    /// Enforces that the given list index must not have the specified value.
    /// </summary>
    /// <param name="key">The key of the list to check.</param>
    /// <param name="index">The position in the list to check.</param>
    /// <param name="value">The value of the list position must not match.</param>
    public static Condition ListIndexNotEqual(ValkeyKey key, long index, ValkeyValue value)
        => new ListCondition(key, index, false, value);

    /// <summary>
    /// Enforces that the given list index must not exist.
    /// </summary>
    /// <param name="key">The key of the list to check.</param>
    /// <param name="index">The position in the list that must not exist.</param>
    public static Condition ListIndexNotExists(ValkeyKey key, long index)
        => new ListCondition(key, index, false, null);

    /// <summary>
    /// Enforces that the given key must have the specified value.
    /// </summary>
    /// <param name="key">The key to check.</param>
    /// <param name="value">The value that must match.</param>
    public static Condition StringEqual(ValkeyKey key, ValkeyValue value)
    {
        if (value.IsNull) return KeyNotExists(key);
        return new EqualsCondition(key, ValkeyType.Hash, ValkeyValue.Null, true, value);
    }

    /// <summary>
    /// Enforces that the given key must not have the specified value.
    /// </summary>
    /// <param name="key">The key to check.</param>
    /// <param name="value">The value that must not match.</param>
    public static Condition StringNotEqual(ValkeyKey key, ValkeyValue value)
    {
        if (value.IsNull) return KeyExists(key);
        return new EqualsCondition(key, ValkeyType.Hash, ValkeyValue.Null, false, value);
    }

    /// <summary>
    /// Enforces that the given hash length is a certain value.
    /// </summary>
    /// <param name="key">The key of the hash to check.</param>
    /// <param name="length">The length the hash must have.</param>
    public static Condition HashLengthEqual(ValkeyKey key, long length)
        => new LengthCondition(key, ValkeyType.Hash, 0, length);

    /// <summary>
    /// Enforces that the given hash length is less than a certain value.
    /// </summary>
    /// <param name="key">The key of the hash to check.</param>
    /// <param name="length">The length the hash must be less than.</param>
    public static Condition HashLengthLessThan(ValkeyKey key, long length)
        => new LengthCondition(key, ValkeyType.Hash, 1, length);

    /// <summary>
    /// Enforces that the given hash length is greater than a certain value.
    /// </summary>
    /// <param name="key">The key of the hash to check.</param>
    /// <param name="length">The length the hash must be greater than.</param>
    public static Condition HashLengthGreaterThan(ValkeyKey key, long length)
        => new LengthCondition(key, ValkeyType.Hash, -1, length);

    /// <summary>
    /// Enforces that the given string length is a certain value.
    /// </summary>
    /// <param name="key">The key of the string to check.</param>
    /// <param name="length">The length the string must be equal to.</param>
    public static Condition StringLengthEqual(ValkeyKey key, long length)
        => new LengthCondition(key, ValkeyType.String, 0, length);

    /// <summary>
    /// Enforces that the given string length is less than a certain value.
    /// </summary>
    /// <param name="key">The key of the string to check.</param>
    /// <param name="length">The length the string must be less than.</param>
    public static Condition StringLengthLessThan(ValkeyKey key, long length)
        => new LengthCondition(key, ValkeyType.String, 1, length);

    /// <summary>
    /// Enforces that the given string length is greater than a certain value.
    /// </summary>
    /// <param name="key">The key of the string to check.</param>
    /// <param name="length">The length the string must be greater than.</param>
    public static Condition StringLengthGreaterThan(ValkeyKey key, long length)
        => new LengthCondition(key, ValkeyType.String, -1, length);

    /// <summary>
    /// Enforces that the given list length is a certain value.
    /// </summary>
    /// <param name="key">The key of the list to check.</param>
    /// <param name="length">The length the list must be equal to.</param>
    public static Condition ListLengthEqual(ValkeyKey key, long length)
        => new LengthCondition(key, ValkeyType.List, 0, length);

    /// <summary>
    /// Enforces that the given list length is less than a certain value.
    /// </summary>
    /// <param name="key">The key of the list to check.</param>
    /// <param name="length">The length the list must be less than.</param>
    public static Condition ListLengthLessThan(ValkeyKey key, long length)
        => new LengthCondition(key, ValkeyType.List, 1, length);

    /// <summary>
    /// Enforces that the given list length is greater than a certain value.
    /// </summary>
    /// <param name="key">The key of the list to check.</param>
    /// <param name="length">The length the list must be greater than.</param>
    public static Condition ListLengthGreaterThan(ValkeyKey key, long length)
        => new LengthCondition(key, ValkeyType.List, -1, length);

    /// <summary>
    /// Enforces that the given set cardinality is a certain value.
    /// </summary>
    /// <param name="key">The key of the set to check.</param>
    /// <param name="length">The length the set must be equal to.</param>
    public static Condition SetLengthEqual(ValkeyKey key, long length)
        => new LengthCondition(key, ValkeyType.Set, 0, length);

    /// <summary>
    /// Enforces that the given set cardinality is less than a certain value.
    /// </summary>
    /// <param name="key">The key of the set to check.</param>
    /// <param name="length">The length the set must be less than.</param>
    public static Condition SetLengthLessThan(ValkeyKey key, long length)
        => new LengthCondition(key, ValkeyType.Set, 1, length);

    /// <summary>
    /// Enforces that the given set cardinality is greater than a certain value.
    /// </summary>
    /// <param name="key">The key of the set to check.</param>
    /// <param name="length">The length the set must be greater than.</param>
    public static Condition SetLengthGreaterThan(ValkeyKey key, long length)
        => new LengthCondition(key, ValkeyType.Set, -1, length);

    /// <summary>
    /// Enforces that the given set contains a certain member.
    /// </summary>
    /// <param name="key">The key of the set to check.</param>
    /// <param name="member">The member the set must contain.</param>
    public static Condition SetContains(ValkeyKey key, ValkeyValue member)
        => new ExistsCondition(key, ValkeyType.Set, member, true);

    /// <summary>
    /// Enforces that the given set does not contain a certain member.
    /// </summary>
    /// <param name="key">The key of the set to check.</param>
    /// <param name="member">The member the set must not contain.</param>
    public static Condition SetNotContains(ValkeyKey key, ValkeyValue member)
        => new ExistsCondition(key, ValkeyType.Set, member, false);

    /// <summary>
    /// Enforces that the given sorted set cardinality is a certain value.
    /// </summary>
    /// <param name="key">The key of the sorted set to check.</param>
    /// <param name="length">The length the sorted set must be equal to.</param>
    public static Condition SortedSetLengthEqual(ValkeyKey key, long length)
        => new LengthCondition(key, ValkeyType.SortedSet, 0, length);

    /// <summary>
    /// Enforces that the given sorted set contains a certain number of members with scores in the given range.
    /// </summary>
    /// <param name="key">The key of the sorted set to check.</param>
    /// <param name="length">The length the sorted set must be equal to.</param>
    /// <param name="min">Minimum inclusive score.</param>
    /// <param name="max">Maximum inclusive score.</param>
    public static Condition SortedSetLengthEqual(ValkeyKey key, long length, double min = double.NegativeInfinity, double max = double.PositiveInfinity)
        => new SortedSetRangeLengthCondition(key, min, max, 0, length);

    /// <summary>
    /// Enforces that the given sorted set cardinality is less than a certain value.
    /// </summary>
    /// <param name="key">The key of the sorted set to check.</param>
    /// <param name="length">The length the sorted set must be less than.</param>
    public static Condition SortedSetLengthLessThan(ValkeyKey key, long length)
        => new LengthCondition(key, ValkeyType.SortedSet, 1, length);

    /// <summary>
    /// Enforces that the given sorted set contains less than a certain number of members with scores in the given range.
    /// </summary>
    /// <param name="key">The key of the sorted set to check.</param>
    /// <param name="length">The length the sorted set must be equal to.</param>
    /// <param name="min">Minimum inclusive score.</param>
    /// <param name="max">Maximum inclusive score.</param>
    public static Condition SortedSetLengthLessThan(ValkeyKey key, long length, double min = double.NegativeInfinity, double max = double.PositiveInfinity)
        => new SortedSetRangeLengthCondition(key, min, max, 1, length);

    /// <summary>
    /// Enforces that the given sorted set cardinality is greater than a certain value.
    /// </summary>
    /// <param name="key">The key of the sorted set to check.</param>
    /// <param name="length">The length the sorted set must be greater than.</param>
    public static Condition SortedSetLengthGreaterThan(ValkeyKey key, long length)
        => new LengthCondition(key, ValkeyType.SortedSet, -1, length);

    /// <summary>
    /// Enforces that the given sorted set contains more than a certain number of members with scores in the given range.
    /// </summary>
    /// <param name="key">The key of the sorted set to check.</param>
    /// <param name="length">The length the sorted set must be equal to.</param>
    /// <param name="min">Minimum inclusive score.</param>
    /// <param name="max">Maximum inclusive score.</param>
    public static Condition SortedSetLengthGreaterThan(ValkeyKey key, long length, double min = double.NegativeInfinity, double max = double.PositiveInfinity)
        => new SortedSetRangeLengthCondition(key, min, max, -1, length);

    /// <summary>
    /// Enforces that the given sorted set contains a certain member.
    /// </summary>
    /// <param name="key">The key of the sorted set to check.</param>
    /// <param name="member">The member the sorted set must contain.</param>
    public static Condition SortedSetContains(ValkeyKey key, ValkeyValue member)
        => new ExistsCondition(key, ValkeyType.SortedSet, member, true);

    /// <summary>
    /// Enforces that the given sorted set does not contain a certain member.
    /// </summary>
    /// <param name="key">The key of the sorted set to check.</param>
    /// <param name="member">The member the sorted set must not contain.</param>
    public static Condition SortedSetNotContains(ValkeyKey key, ValkeyValue member)
        => new ExistsCondition(key, ValkeyType.SortedSet, member, false);

    /// <summary>
    /// Enforces that the given sorted set member must have the specified score.
    /// </summary>
    /// <param name="key">The key of the sorted set to check.</param>
    /// <param name="member">The member the sorted set to check.</param>
    /// <param name="score">The score that member must have.</param>
    public static Condition SortedSetEqual(ValkeyKey key, ValkeyValue member, ValkeyValue score)
        => new EqualsCondition(key, ValkeyType.SortedSet, member, true, score);

    /// <summary>
    /// Enforces that the given sorted set member must not have the specified score.
    /// </summary>
    /// <param name="key">The key of the sorted set to check.</param>
    /// <param name="member">The member the sorted set to check.</param>
    /// <param name="score">The score that member must not have.</param>
    public static Condition SortedSetNotEqual(ValkeyKey key, ValkeyValue member, ValkeyValue score)
        => new EqualsCondition(key, ValkeyType.SortedSet, member, false, score);

    /// <summary>
    /// Enforces that the given sorted set must have the given score.
    /// </summary>
    /// <param name="key">The key of the sorted set to check.</param>
    /// <param name="score">The score that the sorted set must have.</param>
    public static Condition SortedSetScoreExists(ValkeyKey key, ValkeyValue score)
        => new SortedSetScoreCondition(key, score, false, 0);

    /// <summary>
    /// Enforces that the given sorted set must not have the given score.
    /// </summary>
    /// <param name="key">The key of the sorted set to check.</param>
    /// <param name="score">The score that the sorted set must not have.</param>
    public static Condition SortedSetScoreNotExists(ValkeyKey key, ValkeyValue score)
        => new SortedSetScoreCondition(key, score, true, 0);

    /// <summary>
    /// Enforces that the given sorted set must have the specified count of the given score.
    /// </summary>
    /// <param name="key">The key of the sorted set to check.</param>
    /// <param name="score">The score that the sorted set must have.</param>
    /// <param name="count">The number of members which sorted set must have.</param>
    public static Condition SortedSetScoreExists(ValkeyKey key, ValkeyValue score, ValkeyValue count)
        => new SortedSetScoreCondition(key, score, true, count);

    /// <summary>
    /// Enforces that the given sorted set must not have the specified count of the given score.
    /// </summary>
    /// <param name="key">The key of the sorted set to check.</param>
    /// <param name="score">The score that the sorted set must not have.</param>
    /// <param name="count">The number of members which sorted set must not have.</param>
    public static Condition SortedSetScoreNotExists(ValkeyKey key, ValkeyValue score, ValkeyValue count)
        => new SortedSetScoreCondition(key, score, false, count);

    /// <summary>
    /// Enforces that the given stream length is a certain value.
    /// </summary>
    /// <param name="key">The key of the stream to check.</param>
    /// <param name="length">The length the stream must have.</param>
    public static Condition StreamLengthEqual(ValkeyKey key, long length)
        => new LengthCondition(key, ValkeyType.Stream, 0, length);

    /// <summary>
    /// Enforces that the given stream length is less than a certain value.
    /// </summary>
    /// <param name="key">The key of the stream to check.</param>
    /// <param name="length">The length the stream must be less than.</param>
    public static Condition StreamLengthLessThan(ValkeyKey key, long length)
        => new LengthCondition(key, ValkeyType.Stream, 1, length);

    /// <summary>
    /// Enforces that the given stream length is greater than a certain value.
    /// </summary>
    /// <param name="key">The key of the stream to check.</param>
    /// <param name="length">The length the stream must be greater than.</param>
    public static Condition StreamLengthGreaterThan(ValkeyKey key, long length)
        => new LengthCondition(key, ValkeyType.Stream, -1, length);

    internal abstract List<ICmd> CreateCommands(); // IResultBox? resultBox);

    internal bool Validate(object? res)
    {
        // first command is always WATCH and we expect OK
        object conditionResult = ((object[])res!)[1];
        Debug.Assert(!((conditionResult as RequestException)?.Message?.Contains("wrong number of arguments") ?? false));
        return (string)((object[])res!)[0] == "OK" && conditionResult is not Exception && ValidateImpl(conditionResult);
    }

    protected abstract bool ValidateImpl(object? res);

#pragma warning disable IDE1006 // Naming Styles
#pragma warning disable IDE0045 // Convert to conditional expression
#pragma warning disable IDE0072 // Add missing cases
#pragma warning disable IDE0010 // Add missing cases
#pragma warning disable IDE0022 // Use expression body for method
#pragma warning disable IDE0047 // Remove unnecessary parentheses

    internal class ExistsCondition : Condition
    {
        private readonly bool expectedResult;
        private readonly ValkeyValue expectedValue;
        private readonly ValkeyKey key;
        private readonly ValkeyType type;
        private readonly ValkeyCommand cmd;

        public ExistsCondition(in ValkeyKey key, ValkeyType type, in ValkeyValue expectedValue, bool expectedResult)
        {
            if (key.IsNull) throw new ArgumentNullException(nameof(key));
            this.key = key;
            this.type = type;
            this.expectedValue = expectedValue;
            this.expectedResult = expectedResult;

            if (expectedValue.IsNull)
            {
                cmd = ValkeyCommand.EXISTS;
            }
            else
            {
                cmd = type switch
                {
                    ValkeyType.Hash => ValkeyCommand.HEXISTS,
                    ValkeyType.Set => ValkeyCommand.SISMEMBER,
                    ValkeyType.SortedSet => ValkeyCommand.ZSCORE,
                    _ => throw new ArgumentException($"Type {type} is not recognized", nameof(type)),
                };
            }
        }

        public override string ToString()
            => (expectedValue.IsNull ? key.ToString() : ((string?)key) + " " + type + " > " + expectedValue)
                + (expectedResult ? " exists" : " does not exists");

        internal override List<ICmd> CreateCommands()
            => [
                Request.CustomCommand(["WATCH", key]),
                Request.CustomCommand([cmd.ToString(), key, expectedValue]),
            ];

        protected override bool ValidateImpl(object? result)
        {
            return type switch
            {
                ValkeyType.SortedSet => (result is null) != expectedResult,
                ValkeyType.Hash or ValkeyType.Set => (bool)result! == expectedResult,
                _ => ((long)result! == 1) == expectedResult,
            };
        }
    }

    internal class EqualsCondition : Condition
    {
        private readonly bool expectedEqual;
        private readonly ValkeyValue memberName, expectedValue;
        private readonly ValkeyKey key;
        private readonly ValkeyType type;
        private readonly ValkeyCommand cmd;

        public EqualsCondition(in ValkeyKey key, ValkeyType type, in ValkeyValue memberName, bool expectedEqual, in ValkeyValue expectedValue)
        {
            if (key.IsNull) throw new ArgumentNullException(nameof(key));
            this.key = key;
            this.memberName = memberName;
            this.expectedEqual = expectedEqual;
            this.expectedValue = expectedValue;
            this.type = type;
            cmd = type switch
            {
                ValkeyType.Hash => memberName.IsNull ? ValkeyCommand.GET : ValkeyCommand.HGET,
                ValkeyType.SortedSet => ValkeyCommand.ZSCORE,
                _ => throw new ArgumentException($"Unknown type: {type}", nameof(type)),
            };
        }

        public override string ToString()
            => (memberName.IsNull ? key.ToString() : ((string?)key) + " " + type + " > " + memberName)
                + (expectedEqual ? " == " : " != ") + expectedValue;

        internal sealed override List<ICmd> CreateCommands()
            => [
                Request.CustomCommand(["WATCH", key]),
                Request.CustomCommand(cmd == ValkeyCommand.GET
                    ? [cmd.ToString(), key]
                    : [cmd.ToString(), key, memberName]
                    ),
            ];

        protected override bool ValidateImpl(object? result)
        {
            switch (type)
            {
                case ValkeyType.SortedSet:
                    double value = result is null ? double.NaN : (double)result;
                    return (value == expectedValue) == expectedEqual;
                default:
                    ValkeyValue vval = (GlideString)result!;
                    return (vval == expectedValue) == expectedEqual;

            }
        }
    }

    internal class ListCondition : Condition
    {
        private readonly bool expectedResult;
        private readonly long index;
        private readonly ValkeyValue? expectedValue;
        private readonly ValkeyKey key;

        [System.Diagnostics.CodeAnalysis.SuppressMessage("Roslynator", "RCS1242:Do not pass non-read-only struct by read-only reference.", Justification = "Attribute")]
        public ListCondition(in ValkeyKey key, long index, bool expectedResult, in ValkeyValue? expectedValue)
        {
            if (key.IsNull) throw new ArgumentNullException(nameof(key));
            this.key = key;
            this.index = index;
            this.expectedResult = expectedResult;
            this.expectedValue = expectedValue;
        }

        public override string ToString()
            => ((string?)key) + "[" + index.ToString() + "]" + (expectedValue.HasValue ? (expectedResult ? " == " : " != ")
                + expectedValue.Value : (expectedResult ? " exists" : " does not exist"));

        internal sealed override List<ICmd> CreateCommands()
            => [
                Request.CustomCommand(["WATCH", key]),
                Request.CustomCommand([ValkeyCommand.LINDEX.ToString(), key, index.ToString()]),
            ];

        protected override bool ValidateImpl(object? result)
        {
            if (expectedValue.HasValue)
                return ((ValkeyValue)(GlideString?)result == expectedValue.Value) == expectedResult;
            else
                return (result is null) != expectedResult;
        }
    }

    internal class LengthCondition : Condition
    {
        private readonly int compareToResult;
        private readonly long expectedLength;
        private readonly ValkeyKey key;
        private readonly ValkeyType type;
        private readonly ValkeyCommand cmd;

        public LengthCondition(in ValkeyKey key, ValkeyType type, int compareToResult, long expectedLength)
        {
            if (key.IsNull) throw new ArgumentNullException(nameof(key));
            this.key = key;
            this.compareToResult = compareToResult;
            this.expectedLength = expectedLength;
            this.type = type;
            cmd = type switch
            {
                ValkeyType.Hash => ValkeyCommand.HLEN,
                ValkeyType.Set => ValkeyCommand.SCARD,
                ValkeyType.List => ValkeyCommand.LLEN,
                ValkeyType.SortedSet => ValkeyCommand.ZCARD,
                ValkeyType.Stream => ValkeyCommand.XLEN,
                ValkeyType.String => ValkeyCommand.STRLEN,
                _ => throw new ArgumentException($"Type {type} isn't recognized", nameof(type)),
            };
        }

        public override string ToString()
            => ((string?)key) + " " + type + " length" + GetComparisonString() + expectedLength;

        private string GetComparisonString()
            => compareToResult == 0 ? " == " : (compareToResult < 0 ? " > " : " < ");

        internal sealed override List<ICmd> CreateCommands()
            => [
                Request.CustomCommand(["WATCH", key]),
                Request.CustomCommand([cmd.ToString(), key]),
            ];

        protected override bool ValidateImpl(object? result)
        {
            return expectedLength.CompareTo((long)result!) == compareToResult;
        }
    }

    internal class SortedSetRangeLengthCondition : Condition
    {
        private readonly ValkeyValue min;
        private readonly ValkeyValue max;
        private readonly int compareToResult;
        private readonly long expectedLength;
        private readonly ValkeyKey key;

        public SortedSetRangeLengthCondition(in ValkeyKey key, ValkeyValue min, ValkeyValue max, int compareToResult, long expectedLength)
        {
            if (key.IsNull) throw new ArgumentNullException(nameof(key));
            this.key = key;
            this.min = min;
            this.max = max;
            this.compareToResult = compareToResult;
            this.expectedLength = expectedLength;
        }

        public override string ToString()
            => ((string?)key) + " " + ValkeyType.SortedSet + " range[" + min + ", " + max + "] length" + GetComparisonString() + expectedLength;

        private string GetComparisonString()
        => compareToResult == 0 ? " == " : (compareToResult < 0 ? " > " : " < ");

        internal sealed override List<ICmd> CreateCommands()
            => [
                Request.CustomCommand(["WATCH", key]),
                Request.CustomCommand(["ZCOUNT", key, min, max]),
            ];

        protected override bool ValidateImpl(object? result)
        {
            return expectedLength.CompareTo((long)result!) == compareToResult;
        }
    }

    internal class SortedSetScoreCondition : Condition
    {
        private readonly bool expectedEqual;
        private readonly ValkeyValue sortedSetScore, expectedValue;
        private readonly ValkeyKey key;

        public SortedSetScoreCondition(in ValkeyKey key, in ValkeyValue sortedSetScore, bool expectedEqual, in ValkeyValue expectedValue)
        {
            if (key.IsNull)
            {
                throw new ArgumentNullException(nameof(key));
            }

            this.key = key;
            this.sortedSetScore = sortedSetScore;
            this.expectedEqual = expectedEqual;
            this.expectedValue = expectedValue;
        }

        public override string ToString()
            => key.ToString() + (expectedEqual ? " contains " : " not contains ") + expectedValue + " members with score: " + sortedSetScore;

        internal sealed override List<ICmd> CreateCommands()
            => [
                Request.CustomCommand(["WATCH", key]),
                Request.CustomCommand(["ZCOUNT", key, sortedSetScore, sortedSetScore]),
            ];

        protected override bool ValidateImpl(object? result)
        {
            return ((long)result! == expectedValue) == expectedEqual;
        }
    }
#pragma warning restore IDE1006 // Naming Styles
#pragma warning restore IDE0011 // Add braces
#pragma warning restore IDE0046 // Convert to conditional expression
#pragma warning restore IDE0072 // Add missing cases
#pragma warning restore IDE0010 // Add missing cases
#pragma warning restore IDE0045 // Convert to conditional expression
#pragma warning restore IDE0022 // Use expression body for method
#pragma warning restore IDE0047 // Remove unnecessary parentheses
}

/// <summary>
/// Indicates the status of a condition as part of a transaction.
/// </summary>
public sealed class ConditionResult(Condition condition)
{
    internal readonly Condition Condition = condition;

    /// <summary>
    /// Indicates whether the condition was satisfied.
    /// </summary>
    public bool WasSatisfied { internal set; get; }
}
