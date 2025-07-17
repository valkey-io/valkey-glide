/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import glide.api.models.commands.FlushMode;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Implementation of ServerManagementCore for standalone (non-cluster) clients.
 * This class handles server management operations for single-node connections.
 * 
 * This implementation delegates to the actual client implementation and provides
 * the composition layer needed to resolve inheritance conflicts.
 */
public class StandaloneServerManagement implements ServerManagementCore {
    
    private final ServerManagementCommands standaloneClient;
    
    /**
     * Creates a new StandaloneServerManagement instance.
     * 
     * @param standaloneClient The standalone client implementation that will handle the actual operations
     */
    public StandaloneServerManagement(ServerManagementCommands standaloneClient) {
        this.standaloneClient = standaloneClient;
    }
    
    /**
     * Gets server information for the standalone client.
     * Returns a String directly since standalone clients don't need ClusterValue wrapping.
     * 
     * @return Server information as String
     */
    @Override
    public CompletableFuture<Object> getInfo() {
        return standaloneClient.info().thenApply(result -> (Object) result);
    }
    
    /**
     * Gets server information for specific sections.
     * Returns a String directly since standalone clients don't need ClusterValue wrapping.
     * 
     * @param sections The sections to retrieve
     * @return Server information as String
     */
    @Override
    public CompletableFuture<Object> getInfo(String[] sections) {
        // Convert String[] to Section[] for the standalone client API
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
        return standaloneClient.info(sectionEnums).thenApply(result -> (Object) result);
    }
    
    /**
     * Returns the number of keys in the database for standalone client.
     * 
     * @return The number of keys
     */
    @Override
    public CompletableFuture<Long> dbsize() {
        return standaloneClient.dbsize();
    }
    
    /**
     * Returns the server time for standalone client.
     * 
     * @return The server time as [UNIX_TIME, microseconds]
     */
    @Override
    public CompletableFuture<String[]> time() {
        return standaloneClient.time();
    }
    
    /**
     * Returns UNIX TIME of the last DB save for standalone client.
     * 
     * @return UNIX TIME of the last DB save
     */
    @Override
    public CompletableFuture<Long> lastsave() {
        return standaloneClient.lastsave();
    }
    
    /**
     * Deletes all keys from all databases for standalone client.
     * 
     * @return OK response
     */
    @Override
    public CompletableFuture<String> flushall() {
        return standaloneClient.flushall();
    }
    
    /**
     * Deletes all keys from all databases with specified mode for standalone client.
     * 
     * @param mode The flush mode (SYNC or ASYNC)
     * @return OK response
     */
    @Override
    public CompletableFuture<String> flushall(FlushMode mode) {
        return standaloneClient.flushall(mode);
    }
    
    /**
     * Deletes all keys from the current database for standalone client.
     * 
     * @return OK response
     */
    @Override
    public CompletableFuture<String> flushdb() {
        return standaloneClient.flushdb();
    }
    
    /**
     * Deletes all keys from the current database with specified mode for standalone client.
     * 
     * @param mode The flush mode (SYNC or ASYNC)
     * @return OK response
     */
    @Override
    public CompletableFuture<String> flushdb(FlushMode mode) {
        return standaloneClient.flushdb(mode);
    }
    
    /**
     * Rewrites the configuration file for standalone client.
     * 
     * @return OK response
     */
    @Override
    public CompletableFuture<String> configRewrite() {
        return standaloneClient.configRewrite();
    }
    
    /**
     * Resets server statistics for standalone client.
     * 
     * @return OK response
     */
    @Override
    public CompletableFuture<String> configResetStat() {
        return standaloneClient.configResetStat();
    }
    
    /**
     * Gets configuration parameter values for standalone client.
     * 
     * @param parameters The parameter names to retrieve
     * @return Configuration values
     */
    @Override
    public CompletableFuture<Map<String, String>> configGet(String[] parameters) {
        return standaloneClient.configGet(parameters);
    }
    
    /**
     * Sets configuration parameter values for standalone client.
     * 
     * @param parameters The parameter values to set
     * @return OK response
     */
    @Override
    public CompletableFuture<String> configSet(Map<String, String> parameters) {
        return standaloneClient.configSet(parameters);
    }
}