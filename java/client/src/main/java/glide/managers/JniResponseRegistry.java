/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.managers;

import glide.api.logging.Logger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Registry for storing Java objects returned from JNI calls. This allows us to pass object IDs
 * through the Response protobuf instead of trying to pass Rust pointers that would become invalid.
 *
 * <p>Objects are stored temporarily and must be retrieved via {@link #retrieveAndRemove(long)} or
 * cleaned up via {@link #remove(long)} to prevent memory leaks. The registry includes monitoring to
 * detect potential leaks by periodically checking registry size.
 */
public class JniResponseRegistry {

    /** Log identifier for this class. */
    private static final String LOG_IDENTIFIER = "JniResponseRegistry";

    /** Minimum interval between warning checks in nanoseconds. */
    private static final long CHECK_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(60); // 1 minute

    /** Size threshold that triggers a warning. */
    private static final int SIZE_WARNING_THRESHOLD = 10000;

    // Thread-safe storage for JNI response objects
    private static final ConcurrentHashMap<Long, Object> responseObjects = new ConcurrentHashMap<>();

    // ID generator for object references
    private static final AtomicLong nextId = new AtomicLong(1);

    // Last time we checked for warnings (to avoid overhead on every call)
    private static volatile long lastCheckTimeNanos = System.nanoTime();

    /** Private constructor - this class will never be instantiated */
    private JniResponseRegistry() {}

    /**
     * Store a Java object and return its ID. This is used when JNI returns a converted Java object
     * that needs to be passed through the Response protobuf.
     *
     * <p>IMPORTANT: The caller is responsible for ensuring the object is eventually removed via
     * {@link #retrieveAndRemove(long)} or {@link #remove(long)}. Failure to do so will cause memory
     * leaks.
     *
     * @param object The Java object to store
     * @return The ID that can be used to retrieve the object, or 0L if object is null
     */
    public static long storeObject(Object object) {
        if (object == null) {
            return 0L;
        }

        long id = nextId.getAndIncrement();
        responseObjects.put(id, object);

        checkForPotentialLeak();

        return id;
    }

    /**
     * Retrieve and remove a Java object by its ID. This is called from valueFromPointer to get the
     * actual Java object. The object is removed after retrieval to prevent memory leaks.
     *
     * @param id The ID of the object to retrieve
     * @return The Java object, or null if not found or id is 0
     */
    public static Object retrieveAndRemove(long id) {
        if (id == 0L) {
            return null;
        }
        return responseObjects.remove(id);
    }

    /**
     * Remove an object from the registry without returning it. This is used for cleanup when an
     * exception occurs after storing an object but before it can be retrieved normally.
     *
     * @param id The ID of the object to remove
     * @return true if an object was removed, false if no object existed with that ID
     */
    public static boolean remove(long id) {
        if (id == 0L) {
            return false;
        }
        return responseObjects.remove(id) != null;
    }

    /** Clear all stored objects. Called during client cleanup. */
    public static void clear() {
        responseObjects.clear();
    }

    /**
     * Get the current number of stored objects. Useful for debugging, monitoring, and detecting
     * potential memory leaks.
     *
     * @return The number of objects currently stored in the registry
     */
    public static int size() {
        return responseObjects.size();
    }

    /**
     * Periodically check if registry size indicates a potential memory leak. This check is
     * rate-limited to minimize performance overhead.
     */
    private static void checkForPotentialLeak() {
        long now = System.nanoTime();
        long lastCheck = lastCheckTimeNanos;

        // Only check once per minute to minimize overhead
        if ((now - lastCheck) < CHECK_INTERVAL_NANOS) {
            return;
        }

        // Update last check time (benign race - multiple threads may update, that's fine)
        lastCheckTimeNanos = now;

        int currentSize = responseObjects.size();
        if (currentSize >= SIZE_WARNING_THRESHOLD) {
            Logger.log(
                    Logger.Level.WARN,
                    LOG_IDENTIFIER,
                    "JniResponseRegistry size ("
                            + currentSize
                            + ") exceeds threshold ("
                            + SIZE_WARNING_THRESHOLD
                            + "). This may indicate a memory leak in response handling.");
        }
    }
}
