using System;
using System.Buffers;
using System.Buffers.Binary;
using System.Collections.Generic;
using System.Diagnostics;
using System.Diagnostics.CodeAnalysis;
using System.IO;
using System.Linq;
using System.Net;
using System.Net.Security;
using System.Net.Sockets;
using System.Runtime.CompilerServices;
using System.Security.Authentication;
using System.Security.Cryptography.X509Certificates;
using System.Text;
using System.Threading;
using System.Threading.Tasks;

namespace Valkey.Glide;

internal sealed partial class PhysicalConnection 
{

    [MethodImpl(MethodImplOptions.AggressiveInlining)]
    internal static int WriteCrlf(Span<byte> span, int offset)
    {
        span[offset++] = (byte)'\r';
        span[offset++] = (byte)'\n';
        return offset;
    }

    internal static int WriteRaw(Span<byte> span, long value, bool withLengthPrefix = false, int offset = 0)
    {
        if (value >= 0 && value <= 9)
        {
            if (withLengthPrefix)
            {
                span[offset++] = (byte)'1';
                offset = WriteCrlf(span, offset);
            }
            span[offset++] = (byte)((int)'0' + (int)value);
        }
        else if (value >= 10 && value < 100)
        {
            if (withLengthPrefix)
            {
                span[offset++] = (byte)'2';
                offset = WriteCrlf(span, offset);
            }
            span[offset++] = (byte)((int)'0' + ((int)value / 10));
            span[offset++] = (byte)((int)'0' + ((int)value % 10));
        }
        else if (value >= 100 && value < 1000)
        {
            int v = (int)value;
            int units = v % 10;
            v /= 10;
            int tens = v % 10, hundreds = v / 10;
            if (withLengthPrefix)
            {
                span[offset++] = (byte)'3';
                offset = WriteCrlf(span, offset);
            }
            span[offset++] = (byte)((int)'0' + hundreds);
            span[offset++] = (byte)((int)'0' + tens);
            span[offset++] = (byte)((int)'0' + units);
        }
        else if (value < 0 && value >= -9)
        {
            if (withLengthPrefix)
            {
                span[offset++] = (byte)'2';
                offset = WriteCrlf(span, offset);
            }
            span[offset++] = (byte)'-';
            span[offset++] = (byte)((int)'0' - (int)value);
        }
        else if (value <= -10 && value > -100)
        {
            if (withLengthPrefix)
            {
                span[offset++] = (byte)'3';
                offset = WriteCrlf(span, offset);
            }
            value = -value;
            span[offset++] = (byte)'-';
            span[offset++] = (byte)((int)'0' + ((int)value / 10));
            span[offset++] = (byte)((int)'0' + ((int)value % 10));
        }
        else
        {
            // we're going to write it, but *to the wrong place*
            var availableChunk = span.Slice(offset);
            var formattedLength = Format.FormatInt64(value, availableChunk);
            if (withLengthPrefix)
            {
                // now we know how large the prefix is: write the prefix, then write the value
                var prefixLength = Format.FormatInt32(formattedLength, availableChunk);
                offset += prefixLength;
                offset = WriteCrlf(span, offset);

                availableChunk = span.Slice(offset);
                var finalLength = Format.FormatInt64(value, availableChunk);
                offset += finalLength;
                Debug.Assert(finalLength == formattedLength);
            }
            else
            {
                offset += formattedLength;
            }
        }

        return WriteCrlf(span, offset);
    }
}
