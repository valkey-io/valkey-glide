/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import java.util.Set;

/**
 * A {@link ConnectionProvider} that exposes cluster seed nodes for GLIDE cluster client creation.
 * Implemented by the cluster connection provider class in each compatibility module.
 */
public interface ClusterNodesConnectionProvider extends ConnectionProvider {

    /** Seed nodes used to bootstrap the cluster topology. */
    Set<HostAndPort> getNodes();
}
