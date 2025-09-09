/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import glide.api.models.commands.InfoOptions;
import glide.api.models.commands.FlushMode;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Core interface for server management operations.
 * This interface defines the fundamental server management operations that can be
 * implemented differently for standalone and cluster clients.
 * 
 * This is the foundation interface that enables composition-based server management
 * while avoiding inheritance conflicts between different return types.
 */
public interface ServerManagementCore {
    
    /**
     * Gets server information. Implementation determines specific return type.
     * 
     * @return Server information - format depends on implementation
     */
    CompletableFuture<Object> getInfo();
    
    /**
     * Gets server information for specific sections. Implementation determines specific return type.
     * 
     * @param sections The sections to retrieve
     * @return Server information - format depends on implementation
     */
    CompletableFuture<Object> getInfo(String[] sections);
    
    /**
     * Returns the number of keys in the database.
     * 
     * @return The number of keys
     */
    CompletableFuture<Long> dbsize();
    
    /**
     * Returns the server time.
     * 
     * @return The server time as [UNIX_TIME, microseconds]
     */
    CompletableFuture<String[]> time();
    
    /**
     * Returns UNIX TIME of the last DB save.
     * 
     * @return UNIX TIME of the last DB save
     */
    CompletableFuture<Long> lastsave();
    
    /**
     * Deletes all keys from all databases.
     * 
     * @return OK response
     */
    CompletableFuture<String> flushall();
    
    /**
     * Deletes all keys from all databases with specified mode.
     * 
     * @param mode The flush mode (SYNC or ASYNC)
     * @return OK response
     */
    CompletableFuture<String> flushall(FlushMode mode);
    
    /**
     * Deletes all keys from the current database.
     * 
     * @return OK response
     */
    CompletableFuture<String> flushdb();
    
    /**
     * Deletes all keys from the current database with specified mode.
     * 
     * @param mode The flush mode (SYNC or ASYNC)
     * @return OK response
     */
    CompletableFuture<String> flushdb(FlushMode mode);
    
    /**
     * Rewrites the configuration file.
     * 
     * @return OK response
     */
    CompletableFuture<String> configRewrite();
    
    /**
     * Resets server statistics.
     * 
     * @return OK response
     */
    CompletableFuture<String> configResetStat();
    
    /**
     * Gets configuration parameter values.
     * 
     * @param parameters The parameter names to retrieve
     * @return Configuration values
     */
    CompletableFuture<Map<String, String>> configGet(String[] parameters);
    
    /**
     * Sets configuration parameter values.
     * 
     * @param parameters The parameter values to set
     * @return OK response
     */
    CompletableFuture<String> configSet(Map<String, String> parameters);
}