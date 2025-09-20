/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages the lifecycle of Jedis resources including connection tracking, cleanup scheduling, and
 * resource monitoring.
 */
public class ResourceLifecycleManager {

    private static final ResourceLifecycleManager INSTANCE = new ResourceLifecycleManager();

    private final ConcurrentHashMap<String, WeakReference<AutoCloseable>> trackedResources;
    private final ScheduledExecutorService cleanupExecutor;
    private final AtomicLong resourceIdCounter;
    private final Object shutdownLock = new Object();
    private volatile boolean isShutdown = false;

    private ResourceLifecycleManager() {
        this.trackedResources = new ConcurrentHashMap<>();
        this.cleanupExecutor =
                Executors.newSingleThreadScheduledExecutor(
                        r -> {
                            Thread t = new Thread(r, "jedis-resource-cleanup");
                            t.setDaemon(true);
                            return t;
                        });
        this.resourceIdCounter = new AtomicLong(0);

        // Schedule periodic cleanup of dead references
        cleanupExecutor.scheduleWithFixedDelay(this::cleanupDeadReferences, 30, 30, TimeUnit.SECONDS);

        // Register shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
    }

    public static ResourceLifecycleManager getInstance() {
        return INSTANCE;
    }

    /**
     * Register a resource for lifecycle tracking.
     *
     * @param resource the resource to track
     * @return unique resource ID
     */
    public String registerResource(AutoCloseable resource) {
        if (isShutdown) {
            throw new IllegalStateException("ResourceLifecycleManager is shutdown");
        }

        String resourceId = "resource-" + resourceIdCounter.incrementAndGet();
        trackedResources.put(resourceId, new WeakReference<>(resource));
        return resourceId;
    }

    /**
     * Unregister a resource from lifecycle tracking.
     *
     * @param resourceId the resource ID to unregister
     */
    public void unregisterResource(String resourceId) {
        if (resourceId != null) {
            trackedResources.remove(resourceId);
        }
    }

    /**
     * Schedule a resource for delayed cleanup.
     *
     * @param resource the resource to cleanup
     * @param delaySeconds delay before cleanup in seconds
     */
    public void scheduleCleanup(AutoCloseable resource, long delaySeconds) {
        if (isShutdown) {
            // If shutdown, cleanup immediately
            closeQuietly(resource);
            return;
        }

        cleanupExecutor.schedule(() -> closeQuietly(resource), delaySeconds, TimeUnit.SECONDS);
    }

    /**
     * Get the number of currently tracked resources.
     *
     * @return number of tracked resources
     */
    public int getTrackedResourceCount() {
        cleanupDeadReferences();
        return trackedResources.size();
    }

    /** Force cleanup of all tracked resources. This should only be used in emergency situations. */
    public void forceCleanupAll() {
        trackedResources
                .values()
                .forEach(
                        ref -> {
                            AutoCloseable resource = ref.get();
                            if (resource != null) {
                                closeQuietly(resource);
                            }
                        });
        trackedResources.clear();
    }

    /** Shutdown the lifecycle manager and cleanup all resources. */
    public void shutdown() {
        synchronized (shutdownLock) {
            if (isShutdown) {
                return;
            }
            isShutdown = true;
        }

        // Cleanup all tracked resources
        forceCleanupAll();

        // Shutdown the cleanup executor
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Check if the lifecycle manager is shutdown.
     *
     * @return true if shutdown
     */
    public boolean isShutdown() {
        return isShutdown;
    }

    /** Clean up dead weak references. */
    private void cleanupDeadReferences() {
        trackedResources.entrySet().removeIf(entry -> entry.getValue().get() == null);
    }

    /**
     * Close a resource quietly without throwing exceptions.
     *
     * @param resource the resource to close
     */
    private void closeQuietly(AutoCloseable resource) {
        if (resource != null) {
            try {
                resource.close();
            } catch (Exception e) {
                // Log the error but don't throw
                System.err.println("Error closing resource:");
                e.printStackTrace();
            }
        }
    }

    /** Resource wrapper that provides additional lifecycle information. */
    public static class ManagedResource implements AutoCloseable {
        private final AutoCloseable delegate;
        private final String resourceId;
        private final long creationTime;
        private volatile boolean closed = false;

        public ManagedResource(AutoCloseable delegate) {
            this.delegate = delegate;
            this.resourceId = getInstance().registerResource(this);
            this.creationTime = System.currentTimeMillis();
        }

        @Override
        public void close() throws Exception {
            if (!closed) {
                closed = true;
                getInstance().unregisterResource(resourceId);
                delegate.close();
            }
        }

        public boolean isClosed() {
            return closed;
        }

        public long getAgeMillis() {
            return System.currentTimeMillis() - creationTime;
        }

        public String getResourceId() {
            return resourceId;
        }

        public AutoCloseable getDelegate() {
            return delegate;
        }
    }
}
