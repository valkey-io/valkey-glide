// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Commands.Options;

using Xunit;

namespace Valkey.Glide.UnitTests;

public class BoundaryTests
{
    [Fact]
    public void LexBoundary_Inclusive_CreatesCorrectBoundary()
    {
        Assert.Multiple(
            () => Assert.Equal("[abc", LexBoundary.Inclusive("abc").ToString()),
            () => Assert.Equal("[123", LexBoundary.Inclusive("123").ToString()),
            () => Assert.Equal("[", LexBoundary.Inclusive("").ToString()),
            () => Assert.Equal("[", LexBoundary.Inclusive(ValkeyValue.Null).ToString())
        );
    }

    [Fact]
    public void LexBoundary_Exclusive_CreatesCorrectBoundary()
    {
        Assert.Multiple(
            () => Assert.Equal("(abc", LexBoundary.Exclusive("abc").ToString()),
            () => Assert.Equal("(123", LexBoundary.Exclusive("123").ToString()),
            () => Assert.Equal("(", LexBoundary.Exclusive("").ToString()),
            () => Assert.Equal("(", LexBoundary.Exclusive(ValkeyValue.Null).ToString())
        );
    }

    [Fact]
    public void LexBoundary_Infinity_CreatesCorrectBoundaries()
    {
        Assert.Multiple(
            () => Assert.Equal("-", LexBoundary.NegativeInfinity().ToString()),
            () => Assert.Equal("+", LexBoundary.PositiveInfinity().ToString())
        );
    }

    [Fact]
    public void LexBoundary_ImplicitStringConversion_Works()
    {
        Assert.Multiple(
            () => Assert.Equal("[test", (string)LexBoundary.Inclusive("test")),
            () => Assert.Equal("(test", (string)LexBoundary.Exclusive("test")),
            () => Assert.Equal("-", (string)LexBoundary.NegativeInfinity()),
            () => Assert.Equal("+", (string)LexBoundary.PositiveInfinity())
        );
    }

    [Fact]
    public void ScoreBoundary_Inclusive_CreatesCorrectBoundary()
    {
        Assert.Multiple(
            () => Assert.Equal("10.5", ScoreBoundary.Inclusive(10.5).ToString()),
            () => Assert.Equal("0", ScoreBoundary.Inclusive(0).ToString()),
            () => Assert.Equal("-5.25", ScoreBoundary.Inclusive(-5.25).ToString()),
            () => Assert.Equal("+inf", ScoreBoundary.Inclusive(double.PositiveInfinity).ToString()),
            () => Assert.Equal("-inf", ScoreBoundary.Inclusive(double.NegativeInfinity).ToString())
        );
    }

    [Fact]
    public void ScoreBoundary_Exclusive_CreatesCorrectBoundary()
    {
        Assert.Multiple(
            () => Assert.Equal("(10.5", ScoreBoundary.Exclusive(10.5).ToString()),
            () => Assert.Equal("(0", ScoreBoundary.Exclusive(0).ToString()),
            () => Assert.Equal("(-5.25", ScoreBoundary.Exclusive(-5.25).ToString()),
            () => Assert.Equal("+inf", ScoreBoundary.Exclusive(double.PositiveInfinity).ToString()),
            () => Assert.Equal("-inf", ScoreBoundary.Exclusive(double.NegativeInfinity).ToString())
        );
    }

    [Fact]
    public void ScoreBoundary_Infinity_CreatesCorrectBoundaries()
    {
        Assert.Multiple(
            () => Assert.Equal("-inf", ScoreBoundary.NegativeInfinity().ToString()),
            () => Assert.Equal("+inf", ScoreBoundary.PositiveInfinity().ToString())
        );
    }

    [Fact]
    public void ScoreBoundary_ImplicitStringConversion_Works()
    {
        Assert.Multiple(
            () => Assert.Equal("10.5", (string)ScoreBoundary.Inclusive(10.5)),
            () => Assert.Equal("(10.5", (string)ScoreBoundary.Exclusive(10.5)),
            () => Assert.Equal("-inf", (string)ScoreBoundary.NegativeInfinity()),
            () => Assert.Equal("+inf", (string)ScoreBoundary.PositiveInfinity())
        );
    }
}
