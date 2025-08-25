/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Optional arguments for the HEXPIREAT command.
 *
 * <p>HEXPIREAT sets expiration times for hash fields at a specific Unix timestamp and supports only
 * expiration conditions:
 *
 * <ul>
 *   <li>NX - Only set expiration when field has no existing expiration
 *   <li>XX - Only set expiration when field has existing expiration
 *   <li>GT - Only set expiration when new expiration is greater than current
 *   <li>LT - Only set expiration when new expiration is less than current
 * </ul>
 *
 * <p>This class is immutable and thread-safe.
 *
 * @example
 *     <pre>{@code
 * // Set expiration only if fields have no existing expiration
 * HExpireAtOptions options = HExpireAtOptions.builder()
 *     .onlyIfNoExpiry()
 *     .build();
 *
 * String[] fields = {"field1", "field2"};
 * long unixTimestamp = System.currentTimeMillis() / 1000 + 3600; // 1 hour from now
 * Long[] result = client.hexpireat("myHash", unixTimestamp, fields, options).get();
 *
 * // Set expiration only if new expiration is greater than current
 * HExpireAtOptions gtOptions = HExpireAtOptions.builder()
 *     .onlyIfGreaterThanCurrent()
 *     .build();
 *
 * Long[] gtResult = client.hexpireat("myHash", unixTimestamp + 3600, fields, gtOptions).get();
 * }</pre>
 *
 * @since Valkey 9.0.0
 * @see <a href="https://valkey.io/commands/hexpireat/">HEXPIREAT Command Documentation</a>
 */
@Builder
@EqualsAndHashCode
@ToString
public final class HExpireAtOptions {

    /** The expiration condition to apply */
    private final ExpireOptions condition;

    /**
     * Converts the options into command arguments.
     *
     * @return Array of command arguments, empty if no condition is set
     */
    public String[] toArgs() {
        if (condition == null) {
            return new String[0];
        }
        return condition.toArgs();
    }

    /** Builder class for {@link HExpireAtOptions}. */
    public static class HExpireAtOptionsBuilder {

        /**
         * Sets the condition to only set expiration when field has no existing expiration. Equivalent
         * to NX in the Valkey API.
         *
         * @return This builder instance
         */
        public HExpireAtOptionsBuilder onlyIfNoExpiry() {
            this.condition = ExpireOptions.HAS_NO_EXPIRY;
            return this;
        }

        /**
         * Sets the condition to only set expiration when field has existing expiration. Equivalent to
         * XX in the Valkey API.
         *
         * @return This builder instance
         */
        public HExpireAtOptionsBuilder onlyIfHasExpiry() {
            this.condition = ExpireOptions.HAS_EXISTING_EXPIRY;
            return this;
        }

        /**
         * Sets the condition to only set expiration when new expiration is greater than current.
         * Equivalent to GT in the Valkey API.
         *
         * @return This builder instance
         */
        public HExpireAtOptionsBuilder onlyIfGreaterThanCurrent() {
            this.condition = ExpireOptions.NEW_EXPIRY_GREATER_THAN_CURRENT;
            return this;
        }

        /**
         * Sets the condition to only set expiration when new expiration is less than current.
         * Equivalent to LT in the Valkey API.
         *
         * @return This builder instance
         */
        public HExpireAtOptionsBuilder onlyIfLessThanCurrent() {
            this.condition = ExpireOptions.NEW_EXPIRY_LESS_THAN_CURRENT;
            return this;
        }
    }
}
