// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide.api.models;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

public class ClientCacheConfigTest {

    @Test
    public void testDefaultConfiguration() {
        ClientCacheConfig config = new ClientCacheConfig.Builder().build();
        
        assertFalse(config.isEnabled());
        assertEquals(1000, config.getMaxSize());
        assertTrue(config.getTtlSeconds().isEmpty());
        assertEquals(ClientCacheConfig.TrackingMode.DEFAULT, config.getTrackingMode());
        assertNull(config.getInvalidationCallback());
    }

    @Test
    public void testCustomConfiguration() {
        AtomicReference<List<String>> invalidatedKeys = new AtomicReference<>();
        
        ClientCacheConfig config = new ClientCacheConfig.Builder()
                .enabled(true)
                .maxSize(5000)
                .ttl(Duration.ofMinutes(10))
                .trackingMode(ClientCacheConfig.TrackingMode.OPTIN)
                .onInvalidation(keys -> invalidatedKeys.set(keys))
                .build();
        
        assertTrue(config.isEnabled());
        assertEquals(5000, config.getMaxSize());
        assertEquals(600L, config.getTtlSeconds().get()); // 10 minutes = 600 seconds
        assertEquals(ClientCacheConfig.TrackingMode.OPTIN, config.getTrackingMode());
        assertNotNull(config.getInvalidationCallback());
        
        // Test callback
        List<String> testKeys = Arrays.asList("key1", "key2");
        config.getInvalidationCallback().accept(testKeys);
        assertEquals(testKeys, invalidatedKeys.get());
    }

    @Test
    public void testTrackingModeValues() {
        assertEquals(0, ClientCacheConfig.TrackingMode.DEFAULT.getValue());
        assertEquals(1, ClientCacheConfig.TrackingMode.OPTIN.getValue());
        assertEquals(2, ClientCacheConfig.TrackingMode.OPTOUT.getValue());
    }

    @Test
    public void testBuilderChaining() {
        ClientCacheConfig config = new ClientCacheConfig.Builder()
                .enabled(true)
                .maxSize(2000)
                .trackingMode(ClientCacheConfig.TrackingMode.OPTOUT)
                .build();
        
        assertTrue(config.isEnabled());
        assertEquals(2000, config.getMaxSize());
        assertEquals(ClientCacheConfig.TrackingMode.OPTOUT, config.getTrackingMode());
    }
}
