/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Optional arguments for hash field expiration commands (HEXPIRE, HPEXPIRE, HEXPIREAT, HPEXPIREAT).
 *
 * <p>These commands set expiration times for hash fields and support only expiration conditions:
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
 * HashFieldExpirationConditionOptions options = HashFieldExpirationConditionOptions.builder()
 *     .onlyIfNoExpiry()
 *     .build();
 *
 * String[] fields = {"field1", "field2"};
 * Long[] result = client.hexpire("myHash", 60, fields, options).get();
 *
 * // Set expiration only if new expiration is greater than current
 * HashFieldExpirationConditionOptions gtOptions = HashFieldExpirationConditionOptions.builder()
 *     .onlyIfGreaterThanCurrent()
 *     .build();
 *
 * Long[] gtResult = client.hpexpire("myHash", 60000, fields, gtOptions).get();
 * }</pre>
 *
 * @since Valkey 9.0.0
 * @see <a href="https://valkey.io/commands/hexpire/">HEXPIRE Command Documentation</a>
 * @see <a href="https://valkey.io/commands/hpexpire/">HPEXPIRE Command Documentation</a>
 * @see <a href="https://valkey.io/commands/hexpireat/">HEXPIREAT Command Documentation</a>
 * @see <a href="https://valkey.io/commands/hpexpireat/">HPEXPIREAT Command Documentation</a>
 */
@Builder
@EqualsAndHashCode
@ToString
public final class HashFieldExpirationConditionOptions {

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

    /** Builder class for {@link HashFieldExpirationConditionOptions}. */
    public static class HashFieldExpirationConditionOptionsBuilder {

        /**
         * Sets the condition to only set expiration when field has no existing expiration. Equivalent
         * to NX in the Valkey API.
         *
         * @return This builder instance
         */
        public HashFieldExpirationConditionOptionsBuilder onlyIfNoExpiry() {
            this.condition = ExpireOptions.HAS_NO_EXPIRY;
            return this;
        }

        /**
         * Sets the condition to only set expiration when field has existing expiration. Equivalent to
         * XX in the Valkey API.
         *
         * @return This builder instance
         */
        public HashFieldExpirationConditionOptionsBuilder onlyIfHasExpiry() {
            this.condition = ExpireOptions.HAS_EXISTING_EXPIRY;
            return this;
        }

        /**
         * Sets the condition to only set expiration when new expiration is greater than current.
         * Equivalent to GT in the Valkey API.
         *
         * @return This builder instance
         */
        public HashFieldExpirationConditionOptionsBuilder onlyIfGreaterThanCurrent() {
            this.condition = ExpireOptions.NEW_EXPIRY_GREATER_THAN_CURRENT;
            return this;
        }

        /**
         * Sets the condition to only set expiration when new expiration is less than current.
         * Equivalent to LT in the Valkey API.
         *
         * @return This builder instance
         */
        public HashFieldExpirationConditionOptionsBuilder onlyIfLessThanCurrent() {
            this.condition = ExpireOptions.NEW_EXPIRY_LESS_THAN_CURRENT;
            return this;
        }
    }
}
