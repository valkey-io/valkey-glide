// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System.Diagnostics;
using System.Net;

internal class Utils
{
    public static EndPoint ParseEndPoint(string host, int port)
    {
        if (IPAddress.TryParse(host, out IPAddress? ip)) return new IPEndPoint(ip, port);
        return new DnsEndPoint(host, port);
    }

    public static bool TryParseEndPoint(string hostAndPort, out IPEndPoint result)
    {
        if (!Uri.TryCreate($"tcp://{hostAndPort}", UriKind.Absolute, out Uri uri) ||
            !IPAddress.TryParse(uri.Host, out IPAddress ipAddress) ||
            uri.Port < 0 || uri.Port > 65535)
        {
            result = default(IPEndPoint);
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
}
