using Valkey.Glide.InterOp.Native;

namespace Valkey.Glide.InterOp;

/// <summary>
/// Routes a request to a node by its address.
/// </summary>
public class ByAddressRoute : SingleNodeRoute
{
    public readonly string Host;
    public readonly int Port;

    /// <summary>
    /// Create a route using hostname/address and port.<br />
    /// <paramref name="host"/> is the preferred endpoint as shown in the output of the <c>CLUSTER SLOTS</c> command.
    /// </summary>
    /// <param name="host">A hostname or IP address.</param>
    /// <param name="port">A port.</param>
    public ByAddressRoute(string host, int port)
    {
        Host = host;
        Port = port;
    }

    /// <summary>
    /// Create a route using address string formatted as <c>"address:port"</c>.
    /// <paramref name="host"/> is the preferred endpoint as shown in the output of the <c>CLUSTER SLOTS</c> command.
    /// </summary>
    /// <param name="host">Address in format <c>"address:port"</c>.</param>
    /// <exception cref="ArgumentException"></exception>
    public ByAddressRoute(string host)
    {
        string[] parts = host.Split(':');
        if (parts.Length != 2)
        {
            throw new ArgumentException(
                "No port provided, and host is not in the expected format 'hostname:port'. Received: " + host);
        }

        Host = parts[0];
        Port = int.Parse(parts[1]);
    }

    internal override Native.Route ToFfi() => new(RouteType.SlotId, address: (Host, Port));
}