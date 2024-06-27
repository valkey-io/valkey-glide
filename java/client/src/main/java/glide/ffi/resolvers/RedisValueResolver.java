/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.ffi.resolvers;

import java.util.List;

import response.ResponseOuterClass.Response;

public class RedisValueResolver {

    // TODO: consider lazy loading the glide_rs library
    static {
        NativeUtils.loadGlideLib();
    }

    /**
     * Resolve a value received from Redis using given C-style pointer.
     *
     * @param pointer A memory pointer from {@link Response}
     * @return A RESP3 value
     */
    public static native Object valueFromPointer(long pointer);

    /**
     * Resolve a value received from Redis using given C-style pointer. This method does not assume
     * that strings are valid UTF-8 encoded strings
     *
     * @param pointer A memory pointer from {@link Response}
     * @return A RESP3 value
     */
    public static native Object valueFromPointerBinary(long pointer);

    /**
     * Copy the given array of byte arrays to a native series of byte arrays and return a C-style pointer.
     *
     * @param args The arguments to copy.
     * @return A C-style pointer to a native representation of the arguments.
     */
    public static native long createLeakedBytesVec(byte[][] args);
}
