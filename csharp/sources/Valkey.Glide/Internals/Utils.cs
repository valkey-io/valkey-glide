// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System.Diagnostics;
using System.Net;

internal class Utils
{
    public static (string host, ushort port) SplitEndpoint(EndPoint ep)
        => ep switch
        {
            DnsEndPoint dns => (dns.Host, (ushort)dns.Port),
            IPEndPoint ip => (ip.Address.ToString(), (ushort)ip.Port),
            _ => throw new ArgumentException($"Unsupported endpoint type: {ep.GetType()}"),
        };

    public static void Requires<TException>(bool predicate, string message)
        where TException : Exception, new()
    {
        if (!predicate)
        {
            Debug.WriteLine(message);
            throw new TException();
        }
    }

    public static List<Tuple<string, KeyValuePair<string, string>>> ParseInfoResponse(string data)
    {
        string category = "miscellaneous";
        List<Tuple<string, KeyValuePair<string, string>>> list = [];
        using StringReader reader = new(data);
        while (reader.ReadLine() is string line)
        {
            if (string.IsNullOrWhiteSpace(line))
            {
                continue;
            }
            if (line.StartsWith("# "))
            {
                category = line[2..].Trim();
                continue;
            }
            int idx = line.IndexOf(':');
            if (idx < 0)
            {
                continue;
            }
            KeyValuePair<string, string> pair = new(
                line[..idx].Trim(),
                line[(idx + 1)..].Trim());
            list.Add(Tuple.Create(category, pair));
        }
        return list;
    }
}
