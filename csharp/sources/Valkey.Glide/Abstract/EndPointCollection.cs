﻿// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System.Collections;
using System.Collections.ObjectModel;
using System.Net;

namespace Valkey.Glide;

/// <summary>
/// A list of endpoints.
/// </summary>
public sealed class EndPointCollection : Collection<EndPoint>, IEnumerable<EndPoint>
{
    private static class DefaultPorts
    {
        public static int Standard => 6379;
        public static int Ssl => 6380;
        public static int Sentinel => 26379;
    }

    /// <summary>
    /// Create a new <see cref="EndPointCollection" />.
    /// </summary>
    public EndPointCollection() { }

    /// <summary>
    /// Create a new <see cref="EndPointCollection" />.
    /// </summary>
    /// <param name="endpoints">The endpoints to add to the collection.</param>
    public EndPointCollection(IList<EndPoint> endpoints) : base(endpoints) { }

    /// <summary>
    /// Format an <see cref="EndPoint" />.
    /// </summary>
    /// <param name="endpoint">The endpoint to get a string representation for.</param>
    public static string ToString(EndPoint? endpoint) => Format.ToString(endpoint);

    /// <summary>
    /// Attempt to parse a string into an <see cref="EndPoint" />.
    /// </summary>
    /// <param name="endpoint">The endpoint string to parse.</param>
    public static EndPoint? TryParse(string endpoint) => Format.TryParseEndPoint(endpoint, out EndPoint? result) ? result : null;

    /// <summary>
    /// Adds a new endpoint to the list.
    /// </summary>
    /// <param name="hostAndPort">The host:port string to add an endpoint for to the collection.</param>
    public void Add(string hostAndPort)
    {
        if (!Format.TryParseEndPoint(hostAndPort, out EndPoint? endpoint))
        {
            throw new ArgumentException($"Could not parse host and port from '{hostAndPort}'", nameof(hostAndPort));
        }
        Add(endpoint);
    }

    /// <summary>
    /// Adds a new endpoint to the list.
    /// </summary>
    /// <param name="host">The host to add.</param>
    /// <param name="port">The port for <paramref name="host" /> to add.</param>
    public void Add(string host, int port) => Add(Format.ParseEndPoint(host, port));

    /// <summary>
    /// Adds a new endpoint to the list.
    /// </summary>
    /// <param name="host">The host to add.</param>
    /// <param name="port">The port for <paramref name="host" /> to add.</param>
    public void Add(IPAddress host, int port) => Add(new IPEndPoint(host, port));

    /// <summary>
    /// Try adding a new endpoint to the list.
    /// </summary>
    /// <param name="endpoint">The endpoint to add.</param>
    /// <returns><see langword="true" /> if the endpoint was added, <see langword="false" /> if not.</returns>
    public bool TryAdd(EndPoint endpoint)
    {
        if (endpoint == null)
        {
            throw new ArgumentNullException(nameof(endpoint));
        }

        if (!Contains(endpoint))
        {
            base.InsertItem(Count, endpoint);
            return true;
        }
        else
        {
            return false;
        }
    }

    /// <summary>
    /// See <see cref="Collection{T}.InsertItem(int, T)" />.
    /// </summary>
    /// <param name="index">The index to add <paramref name="item" /> into the collection at.</param>
    /// <param name="item">The item to insert at <paramref name="index" />.</param>
    protected override void InsertItem(int index, EndPoint item)
    {
        if (item == null)
        {
            throw new ArgumentNullException(nameof(item));
        }
        if (Contains(item))
        {
            throw new ArgumentException("EndPoints must be unique", nameof(item));
        }

        base.InsertItem(index, item);
    }

    /// <summary>
    /// See <see cref="Collection{T}.SetItem(int, T)" />.
    /// </summary>
    /// <param name="index">The index to replace an endpoint at.</param>
    /// <param name="item">The item to replace the existing endpoint at <paramref name="index" />.</param>
    protected override void SetItem(int index, EndPoint item)
    {
        if (item == null)
        {
            throw new ArgumentNullException(nameof(item));
        }
        int existingIndex;
        try
        {
            existingIndex = IndexOf(item);
        }
        catch (NullReferenceException)
        {
            // mono has a nasty bug in DnsEndPoint.Equals; if they do bad things here: sorry, I can't help
            existingIndex = -1;
        }
        if (existingIndex >= 0 && existingIndex != index)
        {
            throw new ArgumentException("EndPoints must be unique", nameof(item));
        }
        base.SetItem(index, item);
    }

    internal void SetDefaultPorts(bool ssl = false)
    {
        int defaultPort = ssl ? DefaultPorts.Ssl : DefaultPorts.Standard;

        for (int i = 0; i < Count; i++)
        {
            switch (this[i])
            {
                case DnsEndPoint dns when dns.Port == 0:
                    this[i] = new DnsEndPoint(dns.Host, defaultPort, dns.AddressFamily);
                    break;
                case IPEndPoint ip when ip.Port == 0:
                    this[i] = new IPEndPoint(ip.Address, defaultPort);
                    break;
                default:
                    break;
            }
        }
    }

    IEnumerator IEnumerable.GetEnumerator() => GetEnumerator();
    IEnumerator<EndPoint> IEnumerable<EndPoint>.GetEnumerator() => GetEnumerator();

    /// <inheritdoc cref="Collection{EndPoint}.GetEnumerator" />
    public new IEnumerator<EndPoint> GetEnumerator()
    {
        // this does *not* need to handle all threading scenarios; but we do
        // want it to at least allow overwrites of existing endpoints without
        // breaking the enumerator; in particular, this avoids a problem where
        // ResolveEndPointsAsync swaps the addresses on us
        for (int i = 0; i < Count; i++)
        {
            yield return this[i];
        }
    }

    internal bool HasDnsEndPoints()
    {
        foreach (EndPoint endpoint in this)
        {
            if (endpoint is DnsEndPoint)
            {
                return true;
            }
        }
        return false;
    }

    internal EndPointCollection Clone() => new([.. Items]);
}
