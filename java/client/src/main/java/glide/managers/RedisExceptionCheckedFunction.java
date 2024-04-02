/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.managers;

import glide.api.models.exceptions.RedisException;
import response.ResponseOuterClass.Response;

/**
 * Functional Interface to extracts data from <code>GLIDE core</code> response.
 *
 * @param <R> Received value type.
 * @param <T> Returning payload type.
 */
@FunctionalInterface
public interface RedisExceptionCheckedFunction<R, T> {

    /**
     * Functional response handler that takes a response of type <code>R</code> and returns a payload
     * of type <code>T</code> from that response.
     *
     * @param response Received {@link Response} from <code>GLIDE core</code>.
     * @return Extracted data.
     * @throws RedisException When encountering an invalid or error state.
     */
    T apply(R response) throws RedisException;
}
