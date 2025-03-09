using System.ComponentModel;
using System.Diagnostics.CodeAnalysis;
using System.Runtime.InteropServices;

namespace Valkey.Glide.InterOp.Native;

[SuppressMessage("ReSharper", "InconsistentNaming")]
[StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
[EditorBrowsable(EditorBrowsableState.Advanced)]
public struct ConnectionRequest
{
    public        ReadFrom                read_from;
    public unsafe byte*                   client_name;
    public unsafe byte*                   auth_username;
    public unsafe byte*                   auth_password;
    public        long                    database_id;
    public        EProtocolVersion        protocol;
    public        ETlsMode                tls_mode;
    public unsafe NodeAddress*            addresses;
    public        uint                    addresses_length;
    public        int                     cluster_mode_enabled;
    public        OptionalU32             request_timeout;
    public        OptionalU32             connection_timeout;
    public        ConnectionRetryStrategy connection_retry_strategy;
    public        PeriodicCheck           periodic_checks;
    public        OptionalU32             inflight_requests_limit;
    public unsafe byte*                   otel_endpoint;

    public OptionalU64 otel_span_flush_interval_ms;
    // ToDo: Enable pubsub_subscriptions
    // public Option<redis::PubSubSubscriptionInfo> pubsub_subscriptions;
}
