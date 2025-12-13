package com.example.valkey;

public abstract class RedisClient {
    protected final TestConfiguration config;
    
    public RedisClient(TestConfiguration config) {
        this.config = config;
    }
    
    /**
     * Initialize the client connection
     */
    public abstract void connect() throws Exception;
    
    /**
     * Close the client connection
     */
    public abstract void close() throws Exception;
    
    /**
     * Set a key-value pair
     * @param key the key
     * @param value the value
     * @return true if successful, false otherwise
     */
    public abstract boolean set(String key, String value) throws Exception;
    
    /**
     * Get a value by key
     * @param key the key
     * @return the value, or null if not found
     */
    public abstract String get(String key) throws Exception;
    
    /**
     * Get the client name for reporting
     */
    public abstract String getClientName();
    
    /**
     * Check if the client is connected
     */
    public abstract boolean isConnected();
    
    /**
     * Ping the server to test connectivity
     */
    public abstract boolean ping() throws Exception;
}
