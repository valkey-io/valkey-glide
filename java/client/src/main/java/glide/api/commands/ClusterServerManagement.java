/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import glide.api.models.ClusterValue;
import glide.api.models.commands.FlushMode;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Implementation of ServerManagementCore for cluster clients.
 * This class handles server management operations for cluster connections.
 * 
 * This implementation delegates to the actual cluster client implementation and provides
 * the composition layer needed to resolve inheritance conflicts while maintaining
 * ClusterValue return types for proper cluster-aware operations.
 */
public class ClusterServerManagement implements ServerManagementCore {
    
    private final ServerManagementClusterCommands clusterClient;
    
    /**
     * Creates a new ClusterServerManagement instance.
     * 
     * @param clusterClient The cluster client implementation that will handle the actual operations
     */
    public ClusterServerManagement(ServerManagementClusterCommands clusterClient) {
        this.clusterClient = clusterClient;
    }
    
    /**
     * Gets server information for the cluster client.
     * Returns a ClusterValue<String> wrapped as Object to maintain the core interface contract.
     * 
     * @return Server information as ClusterValue<String> wrapped as Object
     */
    @Override
    public CompletableFuture<Object> getInfo() {
        return clusterClient.info().thenApply(result -> (Object) result);
    }
    
    /**
     * Gets server information for specific sections.
     * Returns a ClusterValue<String> wrapped as Object to maintain the core interface contract.
     * 
     * @param sections The sections to retrieve
     * @return Server information as ClusterValue<String> wrapped as Object
     */
    @Override
    public CompletableFuture<Object> getInfo(String[] sections) {
        // Convert to Section[] for the cluster client API
        glide.api.models.commands.InfoOptions.Section[] sectionEnums = 
            new glide.api.models.commands.InfoOptions.Section[sections.length];
        for (int i = 0; i < sections.length; i++) {
            try {
                sectionEnums[i] = glide.api.models.commands.InfoOptions.Section.valueOf(sections[i].toUpperCase());
            } catch (IllegalArgumentException e) {
                // If enum conversion fails, default to DEFAULT section
                sectionEnums[i] = glide.api.models.commands.InfoOptions.Section.DEFAULT;
            }
        }
        return clusterClient.info(sectionEnums).thenApply(result -> (Object) result);
    }
    
    /**
     * Returns the number of keys in the database for cluster client.
     * This aggregates the count across all primary nodes in the cluster.
     * 
     * @return The total number of keys across the cluster
     */
    @Override
    public CompletableFuture<Long> dbsize() {
        return clusterClient.dbsize();
    }
    
    /**
     * Returns the server time for cluster client.
     * This returns time from a random node in the cluster.
     * 
     * @return The server time as [UNIX_TIME, microseconds]
     */
    @Override
    public CompletableFuture<String[]> time() {
        return clusterClient.time();
    }
    
    /**
     * Returns UNIX TIME of the last DB save for cluster client.
     * This returns the timestamp from a random node in the cluster.
     * 
     * @return UNIX TIME of the last DB save
     */
    @Override
    public CompletableFuture<Long> lastsave() {
        return clusterClient.lastsave();
    }
    
    /**
     * Deletes all keys from all databases for cluster client.
     * This applies to all primary nodes in the cluster.
     * 
     * @return OK response
     */
    @Override
    public CompletableFuture<String> flushall() {
        return clusterClient.flushall();
    }
    
    /**
     * Deletes all keys from all databases with specified mode for cluster client.
     * This applies to all primary nodes in the cluster.
     * 
     * @param mode The flush mode (SYNC or ASYNC)
     * @return OK response
     */
    @Override
    public CompletableFuture<String> flushall(FlushMode mode) {
        return clusterClient.flushall(mode);
    }
    
    /**
     * Deletes all keys from the current database for cluster client.
     * This applies to all primary nodes in the cluster.
     * 
     * @return OK response
     */
    @Override
    public CompletableFuture<String> flushdb() {
        return clusterClient.flushdb();
    }
    
    /**
     * Deletes all keys from the current database with specified mode for cluster client.
     * This applies to all primary nodes in the cluster.
     * 
     * @param mode The flush mode (SYNC or ASYNC)
     * @return OK response
     */
    @Override
    public CompletableFuture<String> flushdb(FlushMode mode) {
        return clusterClient.flushdb(mode);
    }
    
    /**
     * Rewrites the configuration file for cluster client.
     * This applies to all nodes in the cluster.
     * 
     * @return OK response
     */
    @Override
    public CompletableFuture<String> configRewrite() {
        return clusterClient.configRewrite();
    }
    
    /**
     * Resets server statistics for cluster client.
     * This applies to all nodes in the cluster.
     * 
     * @return OK response
     */
    @Override
    public CompletableFuture<String> configResetStat() {
        return clusterClient.configResetStat();
    }
    
    /**
     * Gets configuration parameter values for cluster client.
     * This queries a random node in the cluster.
     * 
     * @param parameters The parameter names to retrieve
     * @return Configuration values
     */
    @Override
    public CompletableFuture<Map<String, String>> configGet(String[] parameters) {
        return clusterClient.configGet(parameters);
    }
    
    /**
     * Sets configuration parameter values for cluster client.
     * This applies to all nodes in the cluster.
     * 
     * @param parameters The parameter values to set
     * @return OK response
     */
    @Override
    public CompletableFuture<String> configSet(Map<String, String> parameters) {
        return clusterClient.configSet(parameters);
    }
}