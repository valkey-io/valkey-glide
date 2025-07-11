// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System.Collections.Generic;
using Xunit;

namespace Valkey.Glide.UnitTests;

public class SortedSetEntryTests
{
    [Fact]
    public void Constructor_ValidParameters_SetsProperties()
    {
        // Arrange
        var element = (ValkeyValue)"test_element";
        var score = 1.5;

        // Act
        var entry = new SortedSetEntry(element, score);

        // Assert
        Assert.Equal(element, entry.Element);
        Assert.Equal(score, entry.Score);
    }

    [Fact]
    public void ToString_ReturnsCorrectFormat()
    {
        // Arrange
        var element = (ValkeyValue)"test_element";
        var score = 1.5;
        var entry = new SortedSetEntry(element, score);

        // Act
        var result = entry.ToString();

        // Assert
        Assert.Equal("test_element: 1.5", result);
    }

    [Fact]
    public void Equals_SameElementAndScore_ReturnsTrue()
    {
        // Arrange
        var element = (ValkeyValue)"test_element";
        var score = 1.5;
        var entry1 = new SortedSetEntry(element, score);
        var entry2 = new SortedSetEntry(element, score);

        // Act & Assert
        Assert.True(entry1.Equals(entry2));
        Assert.True(entry1 == entry2);
        Assert.False(entry1 != entry2);
    }

    [Fact]
    public void Equals_DifferentElement_ReturnsFalse()
    {
        // Arrange
        var entry1 = new SortedSetEntry("element1", 1.5);
        var entry2 = new SortedSetEntry("element2", 1.5);

        // Act & Assert
        Assert.False(entry1.Equals(entry2));
        Assert.False(entry1 == entry2);
        Assert.True(entry1 != entry2);
    }

    [Fact]
    public void Equals_DifferentScore_ReturnsFalse()
    {
        // Arrange
        var element = (ValkeyValue)"test_element";
        var entry1 = new SortedSetEntry(element, 1.5);
        var entry2 = new SortedSetEntry(element, 2.5);

        // Act & Assert
        Assert.False(entry1.Equals(entry2));
        Assert.False(entry1 == entry2);
        Assert.True(entry1 != entry2);
    }

    [Fact]
    public void CompareTo_LowerScore_ReturnsNegative()
    {
        // Arrange
        var entry1 = new SortedSetEntry("element1", 1.0);
        var entry2 = new SortedSetEntry("element2", 2.0);

        // Act
        var result = entry1.CompareTo(entry2);

        // Assert
        Assert.True(result < 0);
    }

    [Fact]
    public void CompareTo_HigherScore_ReturnsPositive()
    {
        // Arrange
        var entry1 = new SortedSetEntry("element1", 2.0);
        var entry2 = new SortedSetEntry("element2", 1.0);

        // Act
        var result = entry1.CompareTo(entry2);

        // Assert
        Assert.True(result > 0);
    }

    [Fact]
    public void CompareTo_SameScore_ReturnsZero()
    {
        // Arrange
        var entry1 = new SortedSetEntry("element1", 1.5);
        var entry2 = new SortedSetEntry("element2", 1.5);

        // Act
        var result = entry1.CompareTo(entry2);

        // Assert
        Assert.Equal(0, result);
    }

    [Fact]
    public void GetHashCode_SameElementAndScore_ReturnsSameHash()
    {
        // Arrange
        var element = (ValkeyValue)"test_element";
        var score = 1.5;
        var entry1 = new SortedSetEntry(element, score);
        var entry2 = new SortedSetEntry(element, score);

        // Act & Assert
        Assert.Equal(entry1.GetHashCode(), entry2.GetHashCode());
    }

    [Fact]
    public void ImplicitConversion_ToKeyValuePair_Success()
    {
        // Arrange
        var element = (ValkeyValue)"test_element";
        var score = 1.5;
        var entry = new SortedSetEntry(element, score);

        // Act
        KeyValuePair<ValkeyValue, double> kvp = entry;

        // Assert
        Assert.Equal(element, kvp.Key);
        Assert.Equal(score, kvp.Value);
    }

    [Fact]
    public void ImplicitConversion_FromKeyValuePair_Success()
    {
        // Arrange
        var element = (ValkeyValue)"test_element";
        var score = 1.5;
        var kvp = new KeyValuePair<ValkeyValue, double>(element, score);

        // Act
        SortedSetEntry entry = kvp;

        // Assert
        Assert.Equal(element, entry.Element);
        Assert.Equal(score, entry.Score);
    }

    [Fact]
    public void CompareTo_WithObject_NonSortedSetEntry_ReturnsNegativeOne()
    {
        // Arrange
        var entry = new SortedSetEntry("element", 1.5);
        var obj = "not a SortedSetEntry";

        // Act
        var result = entry.CompareTo(obj);

        // Assert
        Assert.Equal(-1, result);
    }

    [Fact]
    public void CompareTo_WithObject_SortedSetEntry_ReturnsCorrectComparison()
    {
        // Arrange
        var entry1 = new SortedSetEntry("element1", 1.0);
        var entry2 = new SortedSetEntry("element2", 2.0);

        // Act
        var result = entry1.CompareTo((object)entry2);

        // Assert
        Assert.True(result < 0);
    }
}
