using System.Diagnostics.CodeAnalysis;

using Valkey.Glide.Hosting;
using Valkey.Glide.InterOp;

using Xunit.Sdk;

namespace Valkey.Glide.UnitTests;

[SuppressMessage("ReSharper", "ParameterOnlyUsedForPreconditionCheck.Local")]
public sealed class ConnectionStringParserTests
{
    /// <summary>
    /// Verifies that an arbitrary connection string can be parsed without throwing any exceptions.
    /// </summary>
    [Theory]
    [InlineData("HOST=localhost:1234,localhost:5678;TLS=Secure;PROTOCOL=Resp3;CLIENTNAME=FancyClient;CLUSTERED=NO")]
    public void CanParseArbitraryConnectionStringWithoutException(string connectionString)
    {
        // Arrange
        // Act
        // Assert
        _ = ConnectionStringParser.Parse(connectionString);
    }

    #region HOST

    [Fact(DisplayName = "HOST - Multiple")]
    public void CanParseConnectionStringWithMultipleHosts()
    {
        // Arrange
        const string connectionString = "HOST=srv01.example.com:1234,srv02.example.com:5678;";

        // Act
        ConnectionRequest result = ConnectionStringParser.Parse(connectionString);

        // Assert
        Assert.Collection(
            result.Addresses,
            node =>
            {
                Assert.Equal("srv01.example.com", node.Address);
                Assert.Equal(1234, node.Port);
            },
            node =>
            {
                Assert.Equal("srv02.example.com", node.Address);
                Assert.Equal(5678, node.Port);
            }
        );
    }

    [Fact(DisplayName = "HOST - Multiple - Mixed port present or not")]
    public void CanParseConnectionStringWithMultipleHostsAndMixedPortPresentOrNot()
    {
        // Arrange
        const string connectionString = "HOST=srv01.example.com,srv02.example.com,srv03.example.com:1234;";
        // Act
        ConnectionRequest result = ConnectionStringParser.Parse(connectionString);

        // Assert
        Assert.Collection(
            result.Addresses,
            node =>
            {
                Assert.Equal("srv01.example.com", node.Address);
                Assert.Equal(ValKeyConstants.DefaultPort, node.Port);
            },
            node =>
            {
                Assert.Equal("srv02.example.com", node.Address);
                Assert.Equal(ValKeyConstants.DefaultPort, node.Port);
            },
            node =>
            {
                Assert.Equal("srv03.example.com", node.Address);
                Assert.Equal(1234, node.Port);
            }
        );
    }

    [Fact(DisplayName = "HOST - Single no port")]
    public void CanParseConnectionStringWithSingleHost()
    {
        // Arrange
        const string connectionString = "HOST=srv01.example.com;";

        // Act
        ConnectionRequest result = ConnectionStringParser.Parse(connectionString);

        // Assert
        Node node = Assert.Single(result.Addresses);
        Assert.Equal("srv01.example.com", node.Address);
        Assert.Equal(ValKeyConstants.DefaultPort, node.Port);
    }

    [Fact(DisplayName = "HOST - Single with port")]
    public void CanParseConnectionStringWithSingleHostAndPort()
    {
        // Arrange
        const string connectionString = "HOST=srv01.example.com:1234;";

        // Act
        ConnectionRequest result = ConnectionStringParser.Parse(connectionString);

        // Assert
        Node node = Assert.Single(result.Addresses);
        Assert.Equal("srv01.example.com", node.Address);
        Assert.Equal(1234, node.Port);
    }

    [Fact(DisplayName = "HOST - Ignores prefixed or suffixed comma and whitespace")]
    public void CanParseConnectionStringWithCommaAndWhitespace()
    {
        // Arrange
        const string connectionString =
            "   ;   HOST   =   ,   srv01.example.com  :   1234 , srv02.example.com   : 5678 ,  ;    ";

        // Act
        ConnectionRequest result = ConnectionStringParser.Parse(connectionString);

        // Assert
        Assert.Collection(
            result.Addresses,
            node =>
            {
                Assert.Equal("srv01.example.com", node.Address);
                Assert.Equal(1234, node.Port);
            },
            node =>
            {
                Assert.Equal("srv02.example.com", node.Address);
                Assert.Equal(5678, node.Port);
            }
        );
    }


    [Fact(DisplayName = "HOST - Throws on empty string")]
    public void ThrowsOnEmptyConnectionString()
    {
        // Arrange
        const string connectionString = "HOST=;";

        // Act
        // Assert
        Assert.Throws<FormatException>(() => ConnectionStringParser.Parse(connectionString));
    }

    [Fact(DisplayName = "HOST - Throws on empty string list")]
    public void ThrowsOnEmptyConnectionStringList()
    {
        // Arrange
        const string connectionString = "HOST=,;";

        // Act
        // Assert
        Assert.Throws<FormatException>(() => ConnectionStringParser.Parse(connectionString));
    }

    [Fact(DisplayName = "HOST - Throws on invalid port")]
    public void ThrowsOnInvalidAddress()
    {
        // Arrange
        const string connectionString = "HOST=srv01.example.com:abc;";

        // Act
        // Assert
        Assert.Throws<FormatException>(() => ConnectionStringParser.Parse(connectionString));
    }

    [Fact(DisplayName = "HOST - Throws on port without host")]
    public void ThrowsOnInvalidPort()
    {
        // Arrange
        const string connectionString = "HOST=:1234;";

        // Act
        // Assert
        Assert.Throws<FormatException>(() => ConnectionStringParser.Parse(connectionString));
    }

    #endregion

    #region CLIENTNAME

    [Fact]
    public void CanParseConnectionStringWithClientName()
    {
        // Arrange
        const string connectionString = "CLIENTNAME=test-client;";
        // Act
        ConnectionRequest result = ConnectionStringParser.Parse(connectionString);
        // Assert
        Assert.Equal("test-client", result.ClientName);
    }

    [Fact]
    public void CanParseConnectionStringWithEmptyClientName()
    {
        // Arrange
        const string connectionString = "CLIENTNAME=;";
        // Act
        ConnectionRequest result = ConnectionStringParser.Parse(connectionString);
        // Assert
        Assert.Null(result.ClientName);
    }

    #endregion


    #region CLUSTERED

    // @formatter:max_line_length 2000
    [Theory]
    [MemberData(nameof(MemberDataHelpers.MultiCase), "yes", "CLUSTERED=", null, Int32.MaxValue, new object[] {true}, MemberType = typeof(MemberDataHelpers))]
    [MemberData(nameof(MemberDataHelpers.MultiCase), "true", "CLUSTERED=", null, Int32.MaxValue, new object[] {true}, MemberType = typeof(MemberDataHelpers))]
    [MemberData(nameof(MemberDataHelpers.MultiCase), "no", "CLUSTERED=", null, Int32.MaxValue, new object[] {false}, MemberType = typeof(MemberDataHelpers))]
    [MemberData(nameof(MemberDataHelpers.MultiCase), "false", "CLUSTERED=", null, Int32.MaxValue, new object[] {false}, MemberType = typeof(MemberDataHelpers))]
    [SuppressMessage("Usage", "xUnit1042:The member referenced by the MemberData attribute returns untyped data rows")]
    // @formatter:max_line_length restore
    public void CanParseConnectionStringClustered(string connectionString, bool expected)
    {
        // Arrange
        // Act
        ConnectionRequest result = ConnectionStringParser.Parse(connectionString);

        // Assert
        Assert.Equal(expected, result.ClusterMode);
    }

    #endregion

    #region PROTOCOL

    // @formatter:max_line_length 2000
    [Theory]
    [MemberData(nameof(MemberDataHelpers.MultiCase), "resp2", "PROTOCOL=", null, Int32.MaxValue, new object[] {EProtocolVersion.Resp2}, MemberType = typeof(MemberDataHelpers))]
    [MemberData(nameof(MemberDataHelpers.MultiCase), "resp3", "PROTOCOL=", null, Int32.MaxValue, new object[] {EProtocolVersion.Resp3}, MemberType = typeof(MemberDataHelpers))]
    [SuppressMessage("Usage", "xUnit1042:The member referenced by the MemberData attribute returns untyped data rows")]
    // @formatter:max_line_length restore
    public void CanParseConnectionStringProtocol(string connectionString, EProtocolVersion expected)
    {
        // Arrange
        // Act
        ConnectionRequest result = ConnectionStringParser.Parse(connectionString);

        // Assert
        Assert.Equal(expected, result.Protocol);
    }

    #endregion

    #region TLS

    // @formatter:max_line_length 2000
    [Theory]
    [MemberData(nameof(MemberDataHelpers.MultiCase), "no", "TLS=", null, Int32.MaxValue, new object[] {ETlsMode.NoTls}, MemberType = typeof(MemberDataHelpers))]
    [MemberData(nameof(MemberDataHelpers.MultiCase), "false", "TLS=", null, Int32.MaxValue, new object[] {ETlsMode.NoTls}, MemberType = typeof(MemberDataHelpers))]
    [MemberData(nameof(MemberDataHelpers.MultiCase), "yes", "TLS=", null, Int32.MaxValue, new object[] {ETlsMode.SecureTls}, MemberType = typeof(MemberDataHelpers))]
    [MemberData(nameof(MemberDataHelpers.MultiCase), "true", "TLS=", null, Int32.MaxValue, new object[] {ETlsMode.SecureTls}, MemberType = typeof(MemberDataHelpers))]
    [MemberData(nameof(MemberDataHelpers.MultiCase), "secure", "TLS=", null, Int32.MaxValue, new object[] {ETlsMode.SecureTls}, MemberType = typeof(MemberDataHelpers))]
    [MemberData(nameof(MemberDataHelpers.MultiCase), "insecure", "TLS=", null, Int32.MaxValue, new object[] {ETlsMode.InsecureTls}, MemberType = typeof(MemberDataHelpers))]
    [SuppressMessage("Usage", "xUnit1042:The member referenced by the MemberData attribute returns untyped data rows")]
    // @formatter:max_line_length restore
    public void CanParseConnectionStringTls(string connectionString, ETlsMode expected)
    {
        // Arrange
        // Act
        ConnectionRequest result = ConnectionStringParser.Parse(connectionString);

        // Assert
        Assert.Equal(expected, result.TlsMode);
    }

    #endregion
}
