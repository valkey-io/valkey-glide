/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models;

import response.ResponseOuterClass;

/**
 * Represents a successful Response from Redis without a value. "Ok" represents the status of the
 * response not the actual value.
 *
 * @see <a href="https://redis.io/docs/reference/protocol-spec/#simple-strings">Simple Strings
 *     response</a>
 */
public final class Ok {

    public static final Ok INSTANCE = new Ok();

    // Constructor is private - use Ok.INSTANCE instead
    private Ok() {}

    public String toString() {
        return ResponseOuterClass.ConstantResponse.OK.toString();
    }
}
