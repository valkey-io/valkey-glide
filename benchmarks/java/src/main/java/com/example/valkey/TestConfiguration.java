package com.example.valkey;

public class TestConfiguration {
    private int concurrentConnections = 2;
    // private int requestsPerMinute = 10000;
    private int requestsPerMinute = 100;
    // private int minDataSize = 10240; // 10KB
    private int minDataSize = 25600; // 25KB
    // private int maxDataSize = 51200; // 50KB
    private int maxDataSize = 25600; // 25KB
    private int testDurationSeconds = 300;
     private String redisHost = "localhost";
    // private String redisHost = "improving-elasticache-01-nra7gl.serverless.use1.cache.amazonaws.com";
//    private String redisHost = "clustercfg.glide-perf-test-cache-2025-2.nra7gl.use1.cache.amazonaws.com";
    private int redisPort = 6379;
    private int readWriteRatio = 50; // 50% reads, 50% writes
    private String keyPrefix = "perf_test_";
    private int warmupSeconds = 10;
    private boolean clusterMode = false;
    private boolean tlsEnabled = false;

    // Getters and setters
    public int getConcurrentConnections() {
        return concurrentConnections;
    }

    public void setConcurrentConnections(int concurrentConnections) {
        this.concurrentConnections = concurrentConnections;
    }

    public int getRequestsPerMinute() {
        return requestsPerMinute;
    }

    public void setRequestsPerMinute(int requestsPerMinute) {
        this.requestsPerMinute = requestsPerMinute;
    }

    public int getMinDataSize() {
        return minDataSize;
    }

    public void setMinDataSize(int minDataSize) {
        this.minDataSize = minDataSize;
    }

    public int getMaxDataSize() {
        return maxDataSize;
    }

    public void setMaxDataSize(int maxDataSize) {
        this.maxDataSize = maxDataSize;
    }

    public int getTestDurationSeconds() {
        return testDurationSeconds;
    }

    public void setTestDurationSeconds(int testDurationSeconds) {
        this.testDurationSeconds = testDurationSeconds;
    }

    public String getRedisHost() {
        return redisHost;
    }

    public void setRedisHost(String redisHost) {
        this.redisHost = redisHost;
    }

    public int getRedisPort() {
        return redisPort;
    }

    public void setRedisPort(int redisPort) {
        this.redisPort = redisPort;
    }

    public int getReadWriteRatio() {
        return readWriteRatio;
    }

    public void setReadWriteRatio(int readWriteRatio) {
        this.readWriteRatio = readWriteRatio;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public int getWarmupSeconds() {
        return warmupSeconds;
    }

    public void setWarmupSeconds(int warmupSeconds) {
        this.warmupSeconds = warmupSeconds;
    }

    public boolean isClusterMode() {
        return clusterMode;
    }

    public void setClusterMode(boolean clusterMode) {
        this.clusterMode = clusterMode;
    }

    public boolean isTlsEnabled() {
        return tlsEnabled;
    }

    public void setTlsEnabled(boolean tlsEnabled) {
        this.tlsEnabled = tlsEnabled;
    }

    // Calculate requests per second
    public double getRequestsPerSecond() {
        return requestsPerMinute / 60.0;
    }

    // Calculate delay between requests per connection (in milliseconds)
    public long getDelayBetweenRequests() {
        if (requestsPerMinute == 0) return 0;
        double requestsPerSecondPerConnection = getRequestsPerSecond() / concurrentConnections;
        long delay = Math.round(1000.0 / requestsPerSecondPerConnection);
        return delay;
    }

    @Override
    public String toString() {
        return String.format(
            "TestConfiguration{connections=%d, rpm=%d, dataSize=%d-%d bytes, duration=%ds, host=%s:%d, r/w=%d%%, warmup=%ds, cluster=%s, tls=%s}",
            concurrentConnections, requestsPerMinute, minDataSize, maxDataSize,
            testDurationSeconds, redisHost, redisPort, readWriteRatio, warmupSeconds,
            clusterMode ? "enabled" : "disabled", tlsEnabled ? "enabled" : "disabled"
        );
    }
}
