/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.managers;

import glide.api.models.exceptions.GlideException;

/**
 * Functional Interface to convert values and throw GlideException when encountering an error state.
 *
 * @param <R> type to evaluate
 * @param <T> payload type
 */
@FunctionalInterface
public interface GlideExceptionCheckedFunction<R, T> {

    /**
     * Functional response handler that takes a value of type R and returns a payload of type T.
     * Throws GlideException when encountering an invalid or error state.
     *
     * @param value - received value type
     * @return T - returning payload type
     * @throws GlideException
     */
    T apply(R value) throws GlideException;
}
