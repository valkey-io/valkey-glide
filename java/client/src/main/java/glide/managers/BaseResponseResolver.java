/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.managers;

import static glide.api.BaseClient.OK;

import glide.api.models.exceptions.GlideException;
import glide.ffi.resolvers.OpenTelemetryResolver;
import response.ResponseOuterClass.Response;

/**
 * Response resolver responsible for evaluating the Valkey response object with a success or
 * failure.
 *
 * <p>Uses an explicit constructor instead of Lombok-generated members so the JNI-facing response
 * path is easy to follow in stack traces; mention motivation in the PR description when this
 * pattern changes.
 */
public class BaseResponseResolver implements GlideExceptionCheckedFunction<Response, Object> {

    private final GlideExceptionCheckedFunction<Long, Object> respPointerResolver;

    public BaseResponseResolver(GlideExceptionCheckedFunction<Long, Object> respPointerResolver) {
        this.respPointerResolver = respPointerResolver;
    }

    /**
     * Extracts value from the DirectByteBuffer response.
     *
     * @return A generic Object with the Response or null if the response is empty
     */
    public Object apply(Response response) throws GlideException {
        // Note: errors are already handled before in CallbackDispatcher
        assert !response.hasClosingError() : "Unhandled response closing error";
        assert !response.hasRequestError() : "Unhandled response request error";

        // Drop the OpenTelemetry span if one was created
        if (response.hasRootSpanPtr() && response.getRootSpanPtr() != 0) {
            OpenTelemetryResolver.dropOtelSpan(response.getRootSpanPtr());
        }

        if (response.hasConstantResponse()) {
            return OK;
        }
        if (response.hasRespPointer()) {
            // DirectByteBuffer approach: the response is already converted to a Java object
            // The pointer now points to the converted Java object, not raw Redis data
            return respPointerResolver.apply(response.getRespPointer());
        }
        // if no response payload is provided, assume null
        return null;
    }
}
