/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.scan;

import glide.api.models.GlideString;
import glide.utils.ArgsBuilder;
import java.util.Arrays;
import java.util.Objects;
import lombok.experimental.SuperBuilder;

/**
 * This base class represents the common set of optional arguments for the SCAN family of commands.
 * Concrete implementations of this class are tied to specific SCAN commands (SCAN, HSCAN, SSCAN,
 * and ZSCAN).
 */
@SuperBuilder
public abstract class BaseScanOptions {
    /** <code>MATCH</code> option string to include in the <code>SCAN</code> commands. */
    public static final String MATCH_OPTION_STRING = "MATCH";

    /** <code>COUNT</code> option string to include in the <code>SCAN</code> commands. */
    public static final String COUNT_OPTION_STRING = "COUNT";

    /**
     * The match filter is applied to the result of the command and will only include strings that
     * match the pattern specified. If the set, hash, or list is large enough for scan commands to
     * return only a subset of the set, hash, or list, then there could be a case where the result is
     * empty although there are items that match the pattern specified. This is due to the default
     * <code>COUNT</code> being <code>10</code> which indicates that it will only fetch and match
     * <code>10</code> items from the list.
     */
    protected final String matchPattern;

    /**
     * The match filter is applied to the result of the command and will only include strings that
     * match the pattern specified. If the set, hash, or list is large enough for scan commands to
     * return only a subset of the set, hash, or list, then there could be a case where the result is
     * empty although there are items that match the pattern specified. This is due to the default
     * <code>COUNT</code> being <code>10</code> which indicates that it will only fetch and match
     * <code>10</code> items from the list.
     */
    protected final GlideString matchPatternBinary;

    /**
     * <code>COUNT</code> is a just a hint for the command for how many elements to fetch from the
     * set, hash, or list. <code>COUNT</code> could be ignored until the set, hash, or list is large
     * enough for the <code>SCAN</code> commands to represent the results as compact single-allocation
     * packed encoding.
     */
    protected final Long count;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BaseScanOptions)) return false;
        BaseScanOptions that = (BaseScanOptions) o;
        return Objects.equals(matchPattern, that.matchPattern) && Objects.equals(count, that.count);
    }

    /**
     * Creates the arguments to be used in <code>SCAN</code> commands.
     *
     * @return a String array that holds the options and their arguments.
     */
    public String[] toArgs() {
        return Arrays.stream(toGlideStringArgs()).map(e -> e.getString()).toArray(String[]::new);
    }

    /**
     * Creates the arguments to be used in <code>SCAN</code> commands.
     *
     * @return a String array that holds the options and their arguments.
     */
    public GlideString[] toGlideStringArgs() {
        ArgsBuilder builder = new ArgsBuilder();
        builder.addIf(MATCH_OPTION_STRING, (matchPattern != null));
        // Add the pattern. We prefer the binary version first
        if (matchPatternBinary != null) {
            builder.add(matchPatternBinary);
        } else if (matchPattern != null) {
            builder.add(matchPattern);
        }
        builder.addIf(COUNT_OPTION_STRING, (count != null));
        builder.addIf(count, (count != null));
        return builder.toArray();
    }
}
