/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import glide.api.models.ClusterValue;
import glide.api.models.GlideString;
import glide.api.models.commands.InfoOptions;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for cluster-specific command execution.
 * This interface provides cluster-aware methods that return ClusterValue objects
 * instead of raw objects, enabling the getSingleValue() and getMultiValue() methods
 * expected by integration tests.
 */
public interface ClusterCommandExecutor {
    
    /**
     * Execute a custom command and return a ClusterValue.
     * This method provides cluster-specific return type for integration test compatibility.
     *
     * @param args The command arguments
     * @return A CompletableFuture containing the result wrapped in ClusterValue
     */
    CompletableFuture<ClusterValue<Object>> customClusterCommand(String[] args);

    /**
     * Execute a custom command with GlideString arguments and return a ClusterValue.
     *
     * @param args The command arguments as GlideString array
     * @return A CompletableFuture containing the result wrapped in ClusterValue
     */
    CompletableFuture<ClusterValue<Object>> customClusterCommand(GlideString[] args);

    /**
     * GET information about the cluster with Section enumeration support.
     *
     * @param sections The sections to retrieve
     * @return A CompletableFuture containing the info response wrapped in ClusterValue
     */
    CompletableFuture<ClusterValue<String>> info(InfoOptions.Section[] sections);

    /**
     * GET information about the cluster with Section enumeration support and routing.
     *
     * @param sections The sections to retrieve
     * @param route The routing configuration
     * @return A CompletableFuture containing the info response wrapped in ClusterValue
     */
    CompletableFuture<ClusterValue<String>> info(InfoOptions.Section[] sections, Object route);

    /**
     * GET information about the cluster without any parameters.
     * This method provides cluster-compatible return type for the basic info command.
     * Note: This method conflicts with BaseClient.info() inheritance.
     * Implementation should use clusterInfo() to provide this functionality.
     *
     * @return A CompletableFuture containing the info response wrapped in ClusterValue
     */
    default CompletableFuture<ClusterValue<String>> infoCluster() {
        // Default implementation - should be overridden by implementing class
        throw new UnsupportedOperationException("infoCluster() method must be implemented by cluster client");
    }
}