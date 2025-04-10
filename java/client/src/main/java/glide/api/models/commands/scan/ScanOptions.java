/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.scan;

import glide.api.commands.GenericClusterCommands;
import glide.api.commands.GenericCommands;
import glide.api.models.GlideString;
import glide.ffi.resolvers.ObjectTypeResolver;
import glide.utils.ArrayTransformUtils;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

/**
 * Optional arguments for {@link GenericCommands#scan} and {@link GenericClusterCommands#scan}.
 *
 * @see <a href="https://valkey.io/commands/scan/">valkey.io</a>
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class ScanOptions extends BaseScanOptions {
    /** <code>TYPE</code> option string to include in the <code>SCAN</code> commands. */
    public static final String TYPE_OPTION_STRING = "TYPE";

    /**
     * Use this option to ask SCAN to only return objects that match a given type. <br>
     * The filter is applied after elements are retrieved from the database, so the option does not
     * reduce the amount of work the server has to do to complete a full iteration. For rare types you
     * may receive no elements in many iterations.
     */
    private final ObjectType type;

    /**
     * If set to true, the scan will perform even if some slots are not covered by any node. It's
     * important to note that when set to true, the scan has no guarantee to cover all keys in the
     * cluster, and the method loses its way to validate the progress of the scan. Defaults to false.
     */
    @Builder.Default private final Boolean allowNonCoveredSlots = false;

    /** Defines the complex data types available for a <code>SCAN</code> request. */
    public enum ObjectType {
        STRING(ObjectTypeResolver.OBJECT_TYPE_STRING_NATIVE_NAME),
        LIST(ObjectTypeResolver.OBJECT_TYPE_LIST_NATIVE_NAME),
        SET(ObjectTypeResolver.OBJECT_TYPE_SET_NATIVE_NAME),
        ZSET(ObjectTypeResolver.OBJECT_TYPE_ZSET_NATIVE_NAME),
        HASH(ObjectTypeResolver.OBJECT_TYPE_HASH_NATIVE_NAME),
        STREAM(ObjectTypeResolver.OBJECT_TYPE_STREAM_NATIVE_NAME);

        /**
         * @return the name of the enum when communicating with the native layer.
         */
        public String getNativeName() {
            return nativeName;
        }

        private final String nativeName;

        ObjectType(String nativeName) {
            this.nativeName = nativeName;
        }
    }

    @Override
    public String[] toArgs() {
        if (type != null) {
            return ArrayTransformUtils.concatenateArrays(
                    super.toArgs(), new String[] {TYPE_OPTION_STRING, type.name()});
        }
        return super.toArgs();
    }

    /**
     * @return the pattern used for the <code>MATCH</code> filter.
     */
    public GlideString getMatchPattern() {
        if (matchPatternBinary != null) {
            return matchPatternBinary;
        } else if (matchPattern != null) {
            return GlideString.of(matchPattern);
        } else {
            return null;
        }
    }

    /**
     * @return the count used for the <code>COUNT</code> field.
     */
    public Long getCount() {
        return count;
    }

    /**
     * @return the type used for the <code>TYPE</code> filter.
     */
    public ObjectType getType() {
        return type;
    }

    /**
     * @return whether non-covered slots are allowed.
     */
    public Boolean getAllowNonCoveredSlots() {
        return allowNonCoveredSlots;
    }
}
