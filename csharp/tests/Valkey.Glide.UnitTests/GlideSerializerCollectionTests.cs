// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using NSubstitute;
using Valkey.Glide.Serializers;

namespace Valkey.Glide.UnitTests;

public sealed class GlideSerializerCollectionTests
{
    [Fact]
    public void CanRegisterTransformersToNewlyCreated()
    {
        // Arrange
        GlideSerializerCollection glideSerializerCollection = new();

        // Act
        glideSerializerCollection.RegisterSerializer(Substitute.For<IGlideSerializer<int>>());
        glideSerializerCollection.RegisterSerializer(Substitute.For<IGlideSerializer<string>>());
        glideSerializerCollection.RegisterSerializer(Substitute.For<IGlideSerializer<bool>>());

        // Assert
        /* Assert.DidNotThrow() */
        Assert.Equal(3, glideSerializerCollection.Count);
    }

    [Fact]
    public void CanOverrideExistingTransformers()
    {
        // Arrange
        GlideSerializerCollection glideSerializerCollection = new();
        StringGlideSerializer transformer1 = new();
        StringGlideSerializer transformer2 = new();
        StringGlideSerializer transformer3 = new();

        // Act
        // Assert
        Assert.Empty(glideSerializerCollection.DebugGetTransformers());

        glideSerializerCollection.RegisterSerializer(transformer1);
        object transformer = Assert.Single(glideSerializerCollection.DebugGetTransformers());
        Assert.Equal(transformer1, transformer);

        glideSerializerCollection.RegisterSerializer(transformer2);
        transformer = Assert.Single(glideSerializerCollection.DebugGetTransformers());
        Assert.Equal(transformer2, transformer);

        glideSerializerCollection.RegisterSerializer(transformer3);
        transformer = Assert.Single(glideSerializerCollection.DebugGetTransformers());
        Assert.Equal(transformer3, transformer);
        Assert.Equal(1, glideSerializerCollection.Count);
    }

    [Fact]
    public void CannotAddTransformersAfterSeal()
    {
        // Arrange
        GlideSerializerCollection glideSerializerCollection = new();
        glideSerializerCollection.Seal();
        StringGlideSerializer glideSerializer = new();

        // Act
        // Assert
        Assert.Throws<InvalidOperationException>(() => glideSerializerCollection.RegisterSerializer(glideSerializer));
        Assert.Equal(0, glideSerializerCollection.Count);
    }

    [Fact]
    public void CannotSealTwice()
    {
        // Arrange
        GlideSerializerCollection glideSerializerCollection = new();
        glideSerializerCollection.Seal();

        // Act
        // Assert
        Assert.Throws<InvalidOperationException>(() => glideSerializerCollection.Seal());
    }

    [Fact]
    public void CannotTransformDataPriorToSeal()
    {
        // Arrange
        GlideSerializerCollection glideSerializerCollection = new();

        // Act
        // Assert
        Assert.Throws<InvalidOperationException>(() => glideSerializerCollection.Transform("test"));
    }

    [Fact]
    public void CanTransformDataAfterSeal()
    {
        // Arrange
        GlideSerializerCollection glideSerializerCollection = new();
        var transformer = Substitute.For<IGlideSerializer<string>>();
        transformer.ToValkey(Arg.Any<string>()).Returns("foobar");
        glideSerializerCollection.RegisterSerializer(transformer);
        glideSerializerCollection.Seal();

        // Act
        // Assert
        Assert.Equal("foobar", glideSerializerCollection.Transform("test"));
    }
}
