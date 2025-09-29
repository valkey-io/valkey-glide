package com.example.valkey;

import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PerformanceWorker implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(PerformanceWorker.class);
    
    private final RedisClient client;
    private final TestConfiguration config;
    private final PerformanceMetrics metrics;
    private final AtomicBoolean running;
    private final Random random;
    private final int workerId;
    
    public PerformanceWorker(RedisClient client, TestConfiguration config, 
                           PerformanceMetrics metrics, AtomicBoolean running, int workerId) {
        this.client = client;
        this.config = config;
        this.metrics = metrics;
        this.running = running;
        this.workerId = workerId;
        this.random = new Random();
    }
    
    @Override
    public void run() {
        boolean connected = false;
        try {
            // Connect the client
            logger.debug("Worker {} attempting to connect using {}", workerId, client.getClientName());
            client.connect();
            client.ping();
            connected = true;
            logger.debug("Worker {} connected successfully", workerId);
            
            // Wait for all workers to be ready
            Thread.sleep(100);
            
            long delayBetweenRequests = config.getDelayBetweenRequests();
            logger.info("Worker {} starting with delay {}ms between requests", workerId, delayBetweenRequests);
            
            int consecutiveFailures = 0;
            final int maxConsecutiveFailures = 10;
            int requestCount = 0;
            
            while (running.get() && consecutiveFailures < maxConsecutiveFailures) {
                try {
                    requestCount++;
                    
                    // Log every 100 requests for first worker
                    if (workerId == 0 && requestCount % 100 == 0) {
                        logger.info("Worker {} processed {} requests", workerId, requestCount);
                    }
                    
                    // Determine operation type based on read/write ratio
                    boolean isRead = random.nextInt(100) < config.getReadWriteRatio();
                    
                    long startTime = System.nanoTime();
                    boolean success = false;
                    String operation;
                    
                    if (isRead) {
                        // Perform GET operation
                        operation = "GET";
                        String key = generateKey();
                        client.get(key);
                        success = true; // GET is successful even if key doesn't exist
                    } else {
                        // Perform SET operation
                        operation = "SET";
                        String key = generateKey();
                        String value = generateValue();
                        success = client.set(key, value);
                    }
                    
                    long endTime = System.nanoTime();
                    long latency = (endTime - startTime) / 1_000_000; // Convert to milliseconds
                    
                    metrics.recordRequest(operation, latency, success);
                    
                    if (success) {
                        consecutiveFailures = 0;
                    } else {
                        consecutiveFailures++;
                        logger.warn("Worker {} operation {} failed (consecutive failures: {})", 
                                  workerId, operation, consecutiveFailures);
                    }
                    
                    // Rate limiting - sleep to maintain target RPS
                    if (delayBetweenRequests > 0) {
                        Thread.sleep(delayBetweenRequests);
                    }
                    
                } catch (InterruptedException e) {
                    logger.debug("Worker {} interrupted", workerId);
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    consecutiveFailures++;
                    logger.error("Worker {} operation failed (consecutive failures: {}): {}", 
                               workerId, consecutiveFailures, e.getMessage());
                    
                    // Record failed request
                    metrics.recordRequest("ERROR", 0, false);
                    
                    // Small delay before retrying
                    try {
                        Thread.sleep(Math.min(1000, 10 * consecutiveFailures)); // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            
            if (consecutiveFailures >= maxConsecutiveFailures) {
                logger.error("Worker {} stopped due to too many consecutive failures", workerId);
            }
            
        } catch (Exception e) {
            logger.error("Worker {} failed to initialize: {}", workerId, e.getMessage(), e);
            if (!connected) {
                logger.error("Worker {} connection details: {}:{}", workerId, config.getRedisHost(), config.getRedisPort());
            }
        } 
        // finally {
        //     try {
        //         if (connected) {
        //             client.close();
        //             logger.debug("Worker {} closed connection", workerId);
        //         }
        //     } catch (Exception e) {
        //         logger.error("Worker {} failed to close client: {}", workerId, e.getMessage());
        //     }
        // }
    }
    
    private String generateKey() {
        // Generate keys that will be reused to enable GET operations to find existing data
        int keyNumber = random.nextInt(1000); // Reuse keys across 1000 possible values
        return config.getKeyPrefix() + workerId + "_" + keyNumber;
    }
    
    private String generateValue() {
        // Generate random string data between minDataSize and maxDataSize
        int minSize = config.getMinDataSize();
        int maxSize = config.getMaxDataSize();
        int size = minSize + random.nextInt(maxSize - minSize + 1);
        
        return RandomStringUtils.randomAlphanumeric(size);
    }
}
