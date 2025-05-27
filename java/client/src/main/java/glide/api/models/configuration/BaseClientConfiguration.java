/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Singular;
import lombok.experimental.SuperBuilder;

/**
 * Configuration settings class for creating a client. Shared settings for standalone and cluster
 * clients.
 */
@Getter
@SuperBuilder
public abstract class BaseClientConfiguration {
    /**
     * DNS Addresses and ports of known nodes in the cluster. If the server is in cluster mode the
     * list can be partial, as the client will attempt to map out the cluster and find all nodes. If
     * the server is in standalone mode, only nodes whose addresses were provided will be used by the
     * client. For example: <code>[ {address:sample-address-0001.use1.cache.amazonaws.com, port:6379},
     * {address: sample-address-0002.use2.cache.amazonaws.com, port:6379} ]</code>.
     */
    @Singular private final List<NodeAddress> addresses;

    /**
     * True if communication with the cluster should use Transport Level Security.
     *
     * <p>If the server/cluster requires TLS, not setting this will cause the connection attempt to
     * fail.
     *
     * <p>If the server/cluster doesn't require TLS, setting this will also cause the connection
     * attempt to fail.
     */
    @Builder.Default private final boolean useTLS = false;

    /** Represents the client's read from strategy. */
    @NonNull @Builder.Default private final ReadFrom readFrom = ReadFrom.PRIMARY;

    /**
     * Enables lazy connection mode, where physical connections to the server(s) are deferred until
     * the first command is sent. This can reduce startup latency and allow for client creation in
     * disconnected environments.
     *
     * <p>Default: {@code false} – connections are established immediately during client creation.
     *
     * <p>When {@code lazyConnect} is set to {@code true}, the client will not attempt to connect to
     * the specified nodes during initialization. Instead, connections will be established only when a
     * command is actually executed.
     *
     * <p>This setting applies to both standalone and cluster modes. Note that if an operation is
     * attempted and connection fails (e.g., unreachable nodes), errors will surface at that point.
     *
     * <p><b>Example:</b>
     *
     * <pre>{@code
     * GlideClientConfiguration config = GlideClientConfiguration.builder()
     *         .address(NodeAddress.builder().host("localhost").port(6379).build())
     *         .lazyConnect(true)
     *         .build();
     *
     * // No connection is made yet
     * GlideClient client = GlideClient.createClient(config).get();
     * client.ping().get(); // Now the client connects and sends the command
     * }</pre>
     */
    @Builder.Default private final boolean lazyConnect = false;

    /**
     * Credentials for authentication process. If none are set, the client will not authenticate
     * itself with the server.
     */
    private final ServerCredentials credentials;

    /**
     * The duration in milliseconds that the client should wait for a request to complete. This
     * duration encompasses sending the request, awaiting for a response from the server, and any
     * required reconnections or retries. If the specified timeout is exceeded for a pending request,
     * it will result in a timeout error. If not explicitly set, a default value of 250 milliseconds
     * will be used.
     */
    private final Integer requestTimeout;

    /**
     * Client name to be used for the client. Will be used with CLIENT SETNAME command during
     * connection establishment.
     */
    private final String clientName;

    /**
     * Serialization protocol to be used with the server. If not set, {@link ProtocolVersion#RESP3}
     * will be used.
     */
    private final ProtocolVersion protocol;

    public abstract BaseSubscriptionConfiguration getSubscriptionConfiguration();

    /**
     * The maximum number of concurrent requests allowed to be in-flight (sent but not yet completed).
     * This limit is used to control the memory usage and prevent the client from overwhelming the
     * server or getting stuck in case of a queue backlog. If not set, a default value of 1000 will be
     * used.
     */
    private final Integer inflightRequestsLimit;

    /**
     * Availability Zone of the client. If ReadFrom strategy is AZAffinity or
     * AZAffinityReplicasAndPrimary, this setting ensures that readonly commands are directed to nodes
     * within the specified AZ if exits.
     */
    private final String clientAZ;
}
