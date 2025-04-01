// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Serializers;

namespace Valkey.Glide.UnitTests.Transformers;

public sealed class StringGlideSerializerTests
{
    private readonly StringGlideSerializer _sut = new();

    [Fact(DisplayName = "[Serialize] Surrounds string with quotations")]
    public void SerializeSurroundsStringWithQuotations()
    {
        // Arrange
        const string input = "test";
        const string expected = "\"test\"";

        // Act
        var result = _sut.ToValkey(input);

        // Assert
        Assert.Equal(expected, result);
    }
}
