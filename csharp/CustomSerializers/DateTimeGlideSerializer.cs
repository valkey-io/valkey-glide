// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide;

namespace CustomSerializers;

public class DateTimeGlideSerializer : IGlideSerializer<DateTime>
{
    public string ToValkey(DateTime t) => t.ToString("yyyy-MM-ddTHH:mm:ss.fffZ");
}
