// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System.Diagnostics;
using System.Net;

internal class Utils
{
    public static EndPoint ParseEndPoint(string host, int port)
        => IPAddress.TryParse(host, out IPAddress? ip)
            ? new IPEndPoint(ip, port)
            : new DnsEndPoint(host, port);

    public static bool TryParseEndPoint(string hostAndPort, out IPEndPoint result)
    {
        if (!Uri.TryCreate($"tcp://{hostAndPort}", UriKind.Absolute, out Uri? uri) ||
            !IPAddress.TryParse(uri.Host, out IPAddress? ipAddress) ||
            uri.Port < 0 || uri.Port > 65535)
        {
            result = new(0, 0);
            return false;
        }

        result = new IPEndPoint(ipAddress, uri.Port);
        return true;
    }

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
