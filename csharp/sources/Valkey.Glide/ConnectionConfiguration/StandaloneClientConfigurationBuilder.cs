using Valkey.Glide.InterOp.Native;

namespace Valkey.Glide;

/// <summary>
/// Represents the configuration settings for a Standalone GLIDE client.
/// </summary>
public class StandaloneClientConfigurationBuilder : ClientConfigurationBuilder<StandaloneClientConfigurationBuilder>
{
    public StandaloneClientConfigurationBuilder() : base(false) { }

    /// <summary>
    /// Complete the configuration with given settings.
    /// </summary>
    public new StandaloneClientConfiguration Build() => new() {Request = base.Build()};

    #region DataBase ID

    /// <summary>
    /// Index of the logical database to connect to.
    /// </summary>
    public uint DataBaseId
    {
        set => Config.DatabaseId = value;
    }

    /// <inheritdoc cref="DataBaseId"/>
    public StandaloneClientConfigurationBuilder WithDataBaseId(uint dataBaseId)
    {
        DataBaseId = dataBaseId;
        return this;
    }

    #endregion

    #region Connection Retry Strategy

    /// <summary>
    /// Strategy used to determine how and when to reconnect, in case of connection failures.<br />
    /// See also <seealso cref="RetryStrategy"/>
    /// </summary>
    public RetryStrategy ConnectionRetryStrategy
    {
        set => Config.RetryStrategy = value;
    }

    /// <inheritdoc cref="ConnectionRetryStrategy"/>
    public StandaloneClientConfigurationBuilder WithConnectionRetryStrategy(RetryStrategy connectionRetryStrategy)
    {
        ConnectionRetryStrategy = connectionRetryStrategy;
        return this;
    }

    /// <inheritdoc cref="ConnectionRetryStrategy"/>
    /// <param name="numberOfRetries"><inheritdoc cref="RetryStrategy.NumberOfRetries" path="/summary"/></param>
    /// <param name="factor"><inheritdoc cref="RetryStrategy.Factor" path="/summary"/></param>
    /// <param name="exponentBase"><inheritdoc cref="RetryStrategy.ExponentBase" path="/summary"/></param>
    public StandaloneClientConfigurationBuilder WithConnectionRetryStrategy(uint numberOfRetries, uint factor,
        uint exponentBase) => WithConnectionRetryStrategy(new RetryStrategy(numberOfRetries, factor, exponentBase));

    #endregion
}
