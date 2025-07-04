﻿namespace Valkey.Glide;

/// <summary>
/// The type of a connection.
/// </summary>
public enum ConnectionType
{
    /// <summary>
    /// Not connection-type related.
    /// </summary>
    None = 0,

    /// <summary>
    /// An interactive connection handles request/response commands for accessing data on demand.
    /// </summary>
    Interactive,

    /// <summary>
    /// A subscriber connection receives unsolicited messages from the server as pub/sub events occur.
    /// </summary>
    Subscription,
}
