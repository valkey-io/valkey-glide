/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.scan;

import glide.api.commands.GenericClusterCommands;
import glide.api.commands.GenericCommands;
import glide.ffi.resolvers.ObjectTypeResolver;
import glide.utils.ArrayTransformUtils;
import lombok.experimental.SuperBuilder;

/**
 * Optional arguments for {@link GenericCommands#scan} and {@link GenericClusterCommands#scan}.
 *
 * @see <a href="https://valkey.io/commands/scan/">valkey.io</a>
 */
@SuperBuilder
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ScanOptions)) return false;
        if (!super.equals(o)) return false;
        ScanOptions that = (ScanOptions) o;
        return type == that.type;
    }

    /**
     * @return the pattern used for the <code>MATCH</code> filter.
     */
    public String getMatchPattern() {
        return matchPattern;
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
}
