// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System.Text;
using Valkey.Glide.InterOp.Native;
using Valkey.Glide.InterOp.Native.Logging;

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

    public static unsafe IReadOnlyCollection<KeyValuePair<string,string>> FromNativeFieldsToKeyValuePairs(Fields inFields)
    {
        var result = new KeyValuePair<string, string>[inFields.fields_length];
        for (var i = 0; i < inFields.fields_length; i++)
        {
            var field = inFields.fields[i];
            var key = HandleString((byte*)field.key, field.key_length, false);
            var value = HandleString((byte*)field.value, field.value_length, false);
            result[i] = new KeyValuePair<string, string>(key ?? string.Empty, value ?? string.Empty);
        }
        return result;
    }
}
