// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System.Text;
using Valkey.Glide.InterOp.Native;

namespace Valkey.Glide.InterOp;

internal static class HelperMethods
{
    internal static unsafe string? HandleString(byte* resultErrorString, int? length = null, bool free = true)
    {
        if (resultErrorString is null)
            return null;
        try
        {
            int len = length ?? Strlen(resultErrorString);
            return Encoding.UTF8.GetString(resultErrorString, len);
        }
        finally
        {
            if (free)
                Imports.free_string(resultErrorString);
        }
    }

    internal static unsafe int Strlen(byte* input)
    {
        int i = 0;
        for (; input[i] != 0; i++)
            ;
        return i;
    }
}
