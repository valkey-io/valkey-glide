/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.ffi.resolvers;

import java.util.LinkedHashMap;

public class StatisticsResolver {
    // TODO: consider lazy loading the glide_rs library
    static {
        NativeUtils.loadGlideLib();
    }

    /** Return the internal statistics Map object */
    public static native LinkedHashMap getStatistics();
}
