// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide.api.models;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.List;

/**
 * Configuration for client-side caching with tracking.
 */
public class ClientCacheConfig {
    private final boolean enabled;
    private final int maxSize;
    private final Optional<Long> ttlSeconds;
    private final TrackingMode trackingMode;
    private final Consumer<List<String>> invalidationCallback;

    /**
     * Tracking modes for client-side caching.
     */
    public enum TrackingMode {
        /** Track all keys accessed by the client */
        DEFAULT(0),
        /** Only track keys explicitly requested */
        OPTIN(1), 
        /** Track all keys except explicitly excluded ones */
        OPTOUT(2);
        
        private final int value;
        
        TrackingMode(int value) { 
            this.value = value; 
        }
        
        public int getValue() { 
            return value; 
        }
    }

    private ClientCacheConfig(Builder builder) {
        this.enabled = builder.enabled;
        this.maxSize = builder.maxSize;
        this.ttlSeconds = builder.ttlSeconds;
        this.trackingMode = builder.trackingMode;
        this.invalidationCallback = builder.invalidationCallback;
    }

    /**
     * Builder for ClientCacheConfig.
     */
    public static class Builder {
        private boolean enabled = false;
        private int maxSize = 1000;
        private Optional<Long> ttlSeconds = Optional.empty();
        private TrackingMode trackingMode = TrackingMode.DEFAULT;
        private Consumer<List<String>> invalidationCallback;

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder maxSize(int maxSize) {
            this.maxSize = maxSize;
            return this;
        }

        public Builder ttl(Duration ttl) {
            this.ttlSeconds = Optional.of(ttl.getSeconds());
            return this;
        }

        public Builder trackingMode(TrackingMode mode) {
            this.trackingMode = mode;
            return this;
        }

        public Builder onInvalidation(Consumer<List<String>> callback) {
            this.invalidationCallback = callback;
            return this;
        }

        public ClientCacheConfig build() {
            return new ClientCacheConfig(this);
        }
    }

    // Getters
    public boolean isEnabled() { return enabled; }
    public int getMaxSize() { return maxSize; }
    public Optional<Long> getTtlSeconds() { return ttlSeconds; }
    public TrackingMode getTrackingMode() { return trackingMode; }
    public Consumer<List<String>> getInvalidationCallback() { return invalidationCallback; }
}
