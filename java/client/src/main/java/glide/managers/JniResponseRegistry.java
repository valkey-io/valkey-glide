/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.managers;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Registry for storing Java objects returned from JNI calls. This allows us to pass object IDs
 * through the Response protobuf instead of trying to pass Rust pointers that would become invalid.
 *
 * <p>The registry uses weak references to allow garbage collection when objects are no longer
 * needed.
 */
public class JniResponseRegistry {

    // Thread-safe storage for JNI response objects
    private static final ConcurrentHashMap<Long, Object> responseObjects = new ConcurrentHashMap<>();

    // ID generator for object references
    private static final AtomicLong nextId = new AtomicLong(1);

    /**
     * Store a Java object and return its ID. This is used when JNI returns a converted Java object
     * that needs to be passed through the Response protobuf.
     *
     * @param object The Java object to store
     * @return The ID that can be used to retrieve the object
     */
    public static long storeObject(Object object) {
        if (object == null) {
            return 0L;
        }

        long id = nextId.getAndIncrement();
        responseObjects.put(id, object);
        return id;
    }

    /**
     * Retrieve and remove a Java object by its ID. This is called from valueFromPointer to get the
     * actual Java object. The object is removed after retrieval to prevent memory leaks.
     *
     * @param id The ID of the object to retrieve
     * @return The Java object, or null if not found
     */
    public static Object retrieveAndRemove(long id) {
        if (id == 0L) {
            return null;
        }
        return responseObjects.remove(id);
    }

    /** Clear all stored objects. Called during client cleanup. */
    public static void clear() {
        responseObjects.clear();
    }

    /** Get the current number of stored objects. Useful for debugging and monitoring. */
    public static int size() {
        return responseObjects.size();
    }
}
