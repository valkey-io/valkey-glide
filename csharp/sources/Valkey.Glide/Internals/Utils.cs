// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System.Diagnostics;

internal class Utils
{
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
