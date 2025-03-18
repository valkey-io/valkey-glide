// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System.Runtime.InteropServices;

using static Glide.ConnectionConfiguration;

namespace Glide;

/// <summary>
/// Basic class. Please use one of the following implementations:
/// <list type="bullet">
/// <item><see cref="RandomRoute"/></item>
/// <item><see cref="AllNodesRoute"/></item>
/// <item><see cref="AllPrimariesRoute"/></item>
/// <item><see cref="SlotIdRoute"/></item>
/// <item><see cref="SlotKeyRoute"/></item>
/// <item><see cref="ByAddressRoute"/></item>
/// </list>
/// </summary>
public abstract class Route
{
    public interface ISingleNodeRoute { }

    public interface IMultiNodeRoute { }

    internal interface ISimpleRoute { }

    /// <summary>
    /// Route request to a random node.<br />
    /// <b>Warning:</b> Don't use it with write commands, they could be routed to a replica (RO) node and fail.
    /// </summary>
    public sealed class RandomRoute : Route, ISingleNodeRoute, ISimpleRoute
    {
        internal override RouteInfo ToFfi() => ToFfi(RouteType.Random);
    }

    /// <summary>
    /// Route request to all nodes.<br />
    /// <b>Warning:</b> Don't use it with write commands, they could be routed to a replica (RO) node and fail.
    /// </summary>
    public sealed class AllNodesRoute : Route, IMultiNodeRoute, ISimpleRoute
    {
        internal override RouteInfo ToFfi() => ToFfi(RouteType.AllNodes);
    }

    /// <summary>
    /// Route request to all primary nodes.
    /// </summary>
    public sealed class AllPrimariesRoute : Route, IMultiNodeRoute, ISimpleRoute
    {
        internal override RouteInfo ToFfi() => ToFfi(RouteType.AllPrimaries);
    }

    /// <inheritdoc cref="RandomRoute"/>
    public static readonly RandomRoute Random = new();
    /// <inheritdoc cref="AllNodesRoute"/>
    public static readonly AllNodesRoute AllNodes = new();
    /// <inheritdoc cref="AllPrimariesRoute"/>
    public static readonly AllPrimariesRoute AllPrimaries = new();

    /// <summary>
    /// Defines type of the node being addressed.
    /// </summary>
    public enum SlotType : uint
    {
        /// <summary>
        /// Address a primary node.
        /// </summary>
        Primary,
        /// <summary>
        /// Address a replica node.
        /// </summary>
        Replica,
    }

    /// <summary>
    /// Request routing configuration overrides the <see cref="ReadFromStrategy"/> connection configuration.<br />
    /// If <see cref="SlotType.Replica"/> is used, the request will be routed to a replica, even if the strategy is <see cref="ReadFromStrategy.Primary"/>.
    /// </summary>
    /// <param name="slotId">Slot number. There are 16384 slots in a Valkey cluster, and each shard manages a slot range.
    /// Unless the slot is known, it's better to route using <see cref="SlotKeyRoute"/>.</param>
    /// <param name="slotType">Defines type of the node being addressed.</param>
    public class SlotIdRoute(int slotId, SlotType slotType) : Route, ISingleNodeRoute
    {
        public readonly int SlotId = slotId;
        public new readonly SlotType SlotType = slotType;

        internal override RouteInfo ToFfi() => ToFfi(RouteType.SlotId, slotIdInfo: (SlotId, SlotType));
    }

    /// <summary>
    /// Request routing configuration overrides the <see cref="ReadFromStrategy"/> connection configuration.<br />
    /// If <see cref="SlotType.Replica"/> is used, the request will be routed to a replica, even if the strategy is <see cref="ReadFromStrategy.Primary"/>.
    /// </summary>
    /// <param name="slotKey">The request will be sent to nodes managing this key.</param>
    /// <param name="slotType">Defines type of the node being addressed.</param>
    public class SlotKeyRoute(string slotKey, SlotType slotType) : Route, ISingleNodeRoute
    {
        public readonly string SlotKey = slotKey;
        public new readonly SlotType SlotType = slotType;

        internal override RouteInfo ToFfi() => ToFfi(RouteType.SlotId, slotKeyInfo: (SlotKey, SlotType));
    }

    /// <summary>
    /// Routes a request to a node by its address.
    /// </summary>
    public class ByAddressRoute : Route, ISingleNodeRoute
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
                throw new ArgumentException("No port provided, and host is not in the expected format 'hostname:port'. Received: " + host);
            }
            Host = parts[0];
            Port = int.Parse(parts[1]);
        }

        internal override RouteInfo ToFfi() => ToFfi(RouteType.SlotId, address: (Host, Port));
    }

    internal Route() { }

    internal abstract RouteInfo ToFfi();

    internal static RouteInfo ToFfi(RouteType requestType, (int slotId, SlotType slotType)? slotIdInfo = null, (string slotKey, SlotType slotType)? slotKeyInfo = null, (string host, int port)? address = null) => new()
    {
        Type = requestType,
        SlotId = slotIdInfo?.slotId ?? 0,
        SlotKey = slotKeyInfo?.slotKey,
        SlotType = slotIdInfo?.slotType ?? slotKeyInfo?.slotType ?? 0,
        Host = address?.host,
        Port = address?.port ?? 0,
    };

    internal enum RouteType : uint
    {
        Random,
        AllNodes,
        AllPrimaries,
        SlotId,
        SlotType,
        ByAddress,
    }

    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Ansi)]
    internal struct RouteInfo
    {
        public RouteType Type;
        public int SlotId;
        [MarshalAs(UnmanagedType.LPStr)]
        public string? SlotKey;
        public SlotType SlotType;
        [MarshalAs(UnmanagedType.LPStr)]
        public string? Host;
        public int Port;
    }
}
