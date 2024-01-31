/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.ffi.resolvers;

import response.ResponseOuterClass.Response;

public class RedisValueResolver {

    // TODO: consider lazy loading the glide_rs library
    static {
        System.loadLibrary("glide_rs");
    }

    /**
     * Resolve a value received from Redis using given C-style pointer.
     *
     * @param pointer A memory pointer from {@link Response}
     * @return A RESP3 value
     */
    public static native Object valueFromPointer(long pointer);
}
